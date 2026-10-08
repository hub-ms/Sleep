package com.soundsleeper.app.service_new

import com.soundsleeper.app.dto_new.mapper.toResponse
import com.soundsleeper.app.config.JwtTokenProvider
import com.soundsleeper.app.entity_new.UserEntity
import com.soundsleeper.app.repository_new.UserJpaRepository
import com.soundsleeper.app.repository_new.AuthInfoJpaRepository
import com.soundsleeper.app.entity_new.AuthInfoEntity
import com.soundsleeper.app.exception.LastAuthMethodException
import com.soundsleeper.app.exception.WithdrawnAccountException
import com.soundsleeper.app.scheduler.UserDeletionScheduler
import com.soundsleeper.app.data.remote.dto.request.EmailVerifyRequest
import com.soundsleeper.app.data.remote.dto.response.AuthInfoResponse
import com.soundsleeper.app.domain.model.User
import com.soundsleeper.app.enum_.AuthProvider
import com.soundsleeper.app.util.NicknameGenerator
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Service
class AuthService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val emailAuthManager: EmailAuthManager,
    private val userJpaRepository: UserJpaRepository,
    private val authInfoJpaRepository: AuthInfoJpaRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val socialVerifierFactory: SocialVerifierFactory,
    private val userDeletionScheduler: UserDeletionScheduler
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 탈퇴한 계정인지 보고, 유예 기간이 남았으면 되살리거나 거절한다.
     *
     * [restore] 가 false 인 1차 호출은 항상 거절한다. 앱이 그 응답을 받아 "복구할까요?"를
     * 묻고, 사용자가 동의하면 restore=true 로 다시 부른다. 묻지 않고 되살리면 탈퇴가
     * 조용히 취소돼 사용자가 자기 계정 상태를 오해한다.
     */
    private fun ensureNotWithdrawn(user: UserEntity, restore: Boolean) {
        if (!user.isDeleted) return

        val restorableUntil = user.deleteAfter?.takeIf { it.isAfter(LocalDateTime.now()) }
            ?: throw WithdrawnAccountException(restorableUntil = null)

        if (!restore) throw WithdrawnAccountException(restorableUntil = restorableUntil)

        // 소셜 연결(AuthInfoEntity)과 수면 기록은 탈퇴해도 지우지 않으므로 되돌리기만 하면 된다.
        user.isDeleted = false
        user.deletedAt = null
        user.deleteAfter = null
    }

    @Transactional
    fun socialLogin(
        provider: AuthProvider,
        accessToken: String,
        restore: Boolean = false,
    ): AuthInfoResponse {
        val verifier = socialVerifierFactory.get(provider)
        val socialInfo = verifier.verify(accessToken)

        // 1. socialId + provider로 기존 소셜 계정 조회
        val existingSocialEntity = authInfoJpaRepository.findByAuthIdAndProvider(
            socialInfo.socialId,
            socialInfo.provider
        )

        // 2. 기존 소셜 계정이 있으면 해당 사용자 정보 업데이트 후 반환
        if (existingSocialEntity != null) {
            val user = existingSocialEntity.user ?: throw IllegalStateException("연결된 유저 엔티티가 없습니다.")

            // 탈퇴한 계정도 AuthInfoEntity 는 남아 있어 이 분기로 그대로 들어온다.
            ensureNotWithdrawn(user, restore)

            user.connectedProviders.add(provider)
            if (user.primaryProvider == null) user.primaryProvider = provider
            
            // 💡 프로필 이미지 URL 강제 최신화
            socialInfo.profileImageUrl?.let { user.profileImageUrl = it }
            user.lastLoginAt = LocalDateTime.now()
            val updatedUser = userJpaRepository.save(user)

            val userAccessToken = jwtTokenProvider.getAccessToken(updatedUser.userId)
            val userRefreshToken = jwtTokenProvider.getRefreshToken(updatedUser.userId)

            return existingSocialEntity.toResponse(
                accessToken = userAccessToken,
                refreshToken = userRefreshToken,
                userResponse = updatedUser.toResponse()
            )
        }

        // 3. 해당 소셜 계정은 없지만, 이메일이 겹치는 유저가 있는지 확인 (계정 통합)
        //
        // 예전에는 이메일을 못 받았을 때 "{socialId}@{provider}.user" 를 지어내 그대로 저장했다.
        // 그 값이 계정 화면에 사용자의 이메일인 것처럼 표시되는 문제가 있었다.
        // 이제는 모르면 null 로 둔다. 통합할 이메일이 없으면 통합 조회도 의미가 없으므로 건너뛴다.
        val email = socialInfo.email
        val existingUser = email?.let { userJpaRepository.findByEmail(it) }
            // 이메일이 같은 탈퇴 계정에 소셜을 덧붙이면 탈퇴가 흐지부지 취소된다.
            ?.also { ensureNotWithdrawn(it, restore) }
        // 이메일이 겹치는 기존 유저에 소셜 계정을 덧붙이는 "계정 통합"은 신규 가입이 아니다.
        // 아래에서 user 를 새로 만드는 경우에만 가입으로 본다.
        val isNewUser = existingUser == null

        val user = existingUser ?: run {
            // 4. 완전 신규 사용자 생성
            val baseNickname = socialInfo.nickname.takeIf { it.isNotBlank() }
            val nickname = if (baseNickname != null) {
                "${baseNickname}_${(100..999).random()}"
            } else {
                NicknameGenerator.generate()
            }

            userJpaRepository.save(
                UserEntity(
                    email = email,
                    nickname = nickname,
                    profileImageUrl = socialInfo.profileImageUrl
                )
            )
        }
        socialInfo.profileImageUrl?.let { user.profileImageUrl = it }

        user.connectedProviders.add(provider)
        if (user.primaryProvider == null) user.primaryProvider = provider
        user.lastLoginAt = LocalDateTime.now()
        val finalUser = userJpaRepository.save(user)

        // 5. 사용자에 새 소셜 계정 연결 (다중 계정)
        val newSocialEntity = authInfoJpaRepository.save(
            AuthInfoEntity(
                provider = socialInfo.provider,
                authId = socialInfo.socialId,
                user = finalUser
            )
        )

        val userAccessToken = jwtTokenProvider.getAccessToken(finalUser.userId)
        val userRefreshToken = jwtTokenProvider.getRefreshToken(finalUser.userId)
        val userResponse = finalUser.toResponse()

        return newSocialEntity.toResponse(
            accessToken = userAccessToken,
            refreshToken = userRefreshToken,
            userResponse = userResponse,
            isNewUser = isNewUser
        )
    }

    @Transactional
    fun connectSocial(userId: Long, provider: AuthProvider, accessToken: String) {
        val verifier = socialVerifierFactory.get(provider)
        val socialInfo = verifier.verify(accessToken)

        val user = userJpaRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("유저 없음") }

        // 어느 경로로 연결되든 connectedProviders에 반영한다. 이게 빠지면 social_info 행 수와
        // 어긋나서 해제 가드가 오판한다(연결이 2개인데 정상 해제가 409로 막히는 등).
        user.connectedProviders.add(socialInfo.provider)
        userJpaRepository.save(user)

        val merged = mergeSocialToNewUser(
            socialInfo.socialId,
            socialInfo.provider,
            userId
        )
        if (merged) {
            log.info("소셜 계정 병합 완료: userId={}, provider={}", userId, provider)
            return
        }

        val existingByUser = authInfoJpaRepository.findByUserIdAndProvider(userId, provider)
        if (existingByUser != null) {
            log.info("이미 연결된 소셜 계정 갱신: userId=$userId, provider=$provider")
            return
        }

        authInfoJpaRepository.save(
            AuthInfoEntity(
                provider = socialInfo.provider,
                authId = socialInfo.socialId,
                user = user
            )
        )
    }

    @Transactional
    fun mergeSocialToNewUser(
        socialId: String,
        provider: AuthProvider,
        targetUserId: Long
    ): Boolean {
        val existingSocial = authInfoJpaRepository.findByAuthIdAndProvider(socialId, provider)
            ?: return false // 병합할 소셜 정보 없음

        if (existingSocial.user?.userId == targetUserId) {
            log.info("이미 같은 사용자에 연결됨: userId={}, provider={}", targetUserId, provider)
            return true
        }

        log.info("계정 병합: {}에서 {}로 소셜 계정 이동", existingSocial.user?.userId, targetUserId)

        // 1. 기존 연결 완전 삭제
        // Hibernate는 delete()를 즉시 실행하지 않고 flush 시점까지 지연시키며, 같은 트랜잭션
        // 안에서 delete와 insert가 (social_id, provider) 유니크 제약에 걸리는 같은 값을 다룰 때
        // insert가 먼저 flush되면 제약 위반이 발생한다. 아래 insert 전에 delete를 즉시 반영하도록
        // 명시적으로 flush한다.
        authInfoJpaRepository.delete(existingSocial)
        authInfoJpaRepository.flush()

        // 2. 새 연결 생성
        val targetUser = userJpaRepository.findById(targetUserId)
            .orElseThrow { IllegalArgumentException("대상 사용자 없음: $targetUserId") }

        authInfoJpaRepository.save(
            AuthInfoEntity(
                provider = provider,
                authId = socialId,
                user = targetUser
            )
        )

        log.info("계정 병합 완료: socialId={}, provider={}, targetUserId={}", socialId, provider, targetUserId)
        return true
    }

    fun sendAuthCode(email: String) {
        log.debug("authcode requested: $email")
        emailAuthManager.sendAuthCode(email)
    }

    @Transactional
    fun verifyAuthCode(request: EmailVerifyRequest): AuthInfoResponse {
        if (!emailAuthManager.verifyAuthCode(request)) {
            throw IllegalArgumentException("인증코드가 유효하지 않습니다.")
        }

        val existingUser = userJpaRepository.findByEmail(request.email)
            // 소셜과 같은 이유로 여기서도 막는다. 한 문만 잠그면 다른 문으로 들어온다.
            ?.also { ensureNotWithdrawn(it, restore = false) }
        val isNewUser = existingUser == null
        val user = existingUser ?: run {
            // [해결] JPA 엔티티 구조와 규격 불일치로 터지는 authInfo 파라미터 제외
            val newUser = UserEntity(
                email = request.email,
                nickname = request.email.split("@")[0]
            )
            userJpaRepository.save(newUser)
        }
        user.lastLoginAt = LocalDateTime.now()
        userJpaRepository.save(user)

        val accessToken = jwtTokenProvider.getAccessToken(user.userId)
        val refreshToken = jwtTokenProvider.getRefreshToken(user.userId)

        val emailAuthInfo = User.AuthInfo.Member(
            memberEmail = user.email,
            authId = user.email ?: "unknown",
            provider = AuthProvider.EMAIL
        )

        return AuthInfoResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = user.toResponse(),
            authId = emailAuthInfo.authId,
            provider = AuthProvider.EMAIL,
            isNewUser = isNewUser
        )
    }

    /**
     * 이메일을 계정에 연결한다.
     *
     * 예전에는 Redis 의 "EMAIL_TOKEN:" 값을 읽었는데 그 키를 **쓰는 코드가 없었다**.
     * 읽기/삭제만 네 군데 있고 생산자가 없어서 이 함수는 항상 "유효하지 않은 토큰"으로
     * 끝났다. 실제로 동작하는 인증코드 경로(sendAuthCode 가 AUTH_CODE: 에 기록)로 바꾼다.
     */
    @Transactional
    fun connectEmail(userId: Long, request: EmailVerifyRequest) {
        if (!emailAuthManager.verifyAuthCode(request)) {
            throw IllegalArgumentException("인증코드가 유효하지 않습니다.")
        }
        val email = request.email.trim().lowercase()

        val user = userJpaRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("유저 없음") }

        // 탈퇴한 계정이 쥐고 있는 이메일까지 "사용 중"으로 세면, 유예 기간 7일 동안
        // 아무도 그 주소를 쓸 수 없다.
        val ownerOfEmail = userJpaRepository.findByEmail(email)
        if (ownerOfEmail != null && !ownerOfEmail.isDeleted && ownerOfEmail.userId != userId) {
            throw IllegalStateException("이미 사용 중인 이메일입니다.")
        }

        user.updateEmail(email)
        user.emailVerified = true
        user.connectedProviders.add(AuthProvider.EMAIL)   // ← 누락되어 UI가 갱신 안 되던 부분
        if (user.primaryProvider == null) user.primaryProvider = AuthProvider.EMAIL
        userJpaRepository.save(user)
    }

    fun refreshToken(refreshToken: String): AuthInfoResponse {
        val isBlacklisted = redisTemplate.hasKey("BLACKLIST:$refreshToken")
        if (isBlacklisted) {
            throw IllegalArgumentException("블랙리스트된 토큰입니다.")
        }
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw IllegalArgumentException("유효하지 않은 토큰입니다.")
        }
        val userId = jwtTokenProvider.extractUserId(refreshToken)
        val user = userJpaRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }
            .also { ensureNotWithdrawn(it, restore = false) }

        val accessToken = jwtTokenProvider.getAccessToken(user.userId)
        val newRefreshToken = jwtTokenProvider.getRefreshToken(user.userId)

        val currentPrimaryProvider = user.primaryProvider ?: AuthProvider.EMAIL
        val userAuthInfo = User.AuthInfo.Member(
            memberEmail = user.email,
            authId = user.email ?: user.userId.toString(),
            provider = currentPrimaryProvider
        )

        return AuthInfoResponse(
            accessToken = accessToken,
            refreshToken = newRefreshToken,
            user = user.toResponse(),
            authId = userAuthInfo.authId,
            provider = currentPrimaryProvider
        )
    }

    /** 변경: (Authentication, String?) -> (Long, String?) */
    fun logout(userId: Long, accessToken: String?) {
        redisTemplate.delete("REFRESH_TOKEN:$userId")
        accessToken?.let { token ->
            val expiration = jwtTokenProvider.getRemainingTime(token)
            if (expiration > 0L) {
                redisTemplate.opsForValue()
                    .set("BLACKLIST:$token", "logout", expiration, TimeUnit.MILLISECONDS)
            }
        }
    }

    /** 변경: accessToken: String -> String? */
    @Transactional
    fun withdraw(userId: Long, accessToken: String?, reason: String? = null) {
        val user = userJpaRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("No existing user") }

        user.isDeleted = true
        user.deletedAt = LocalDateTime.now()
        user.deleteAfter = LocalDateTime.now().plusDays(7)
        userJpaRepository.save(user)

        redisTemplate.delete("REFRESH_TOKEN:$userId")   // ← 탈퇴 후 재발급으로 계속 쓰던 구멍 차단
        accessToken?.let { token ->
            val expiration = jwtTokenProvider.getRemainingTime(token)
            if (expiration > 0L) {
                redisTemplate.opsForValue()
                    .set("BLACKLIST:$token", "withdraw", expiration, TimeUnit.MILLISECONDS)
            }
        }
    }

    @Transactional
    fun changePrimaryProvider(userId: Long, provider: AuthProvider) {
        val user = userJpaRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        require(provider in user.connectedProviders) { "연결되지 않은 로그인 수단입니다: $provider" }
        user.primaryProvider = provider
        userJpaRepository.save(user)
    }

    /** 이메일 인증까지 포함해 이 유저에게 실제로 남아 있는 로그인 수단 수. */
    private fun authMethodCount(user: UserEntity): Long =
        authInfoJpaRepository.countByUser_UserId(user.userId) +
            (if (user.emailVerified && user.email != null) 1L else 0L)

    @Transactional
    fun disconnectSocial(userId: Long, provider: AuthProvider) {
        require(provider != AuthProvider.EMAIL) { "이메일은 /auth/email/disconnect 를 사용하세요." }

        val user = userJpaRepository.findByIdForUpdate(userId)   // 동시 해제 직렬화
            ?: throw IllegalArgumentException("User not found")

        val target = authInfoJpaRepository.findByUserIdAndProvider(userId, provider)
            ?: throw IllegalArgumentException("연결되지 않은 로그인 수단입니다: $provider")

        // connectedProviders(비정규화 값)가 아니라 실제 자격증명 기준으로 센다.
        if (authMethodCount(user) - 1 < 1) {
            throw LastAuthMethodException()      // 컨트롤러가 409로 변환 → 앱의 차단 모달
        }

        // UserEntity.authInfo가 cascade = ALL 이라 컬렉션에 남아 있으면 flush 때 되살아난다.
        user.authInfo.remove(target)
        authInfoJpaRepository.delete(target)     // 실제 레코드 삭제 (재로그인 시 부활 방지)

        user.connectedProviders.remove(provider)
        if (user.primaryProvider == provider) {
            user.primaryProvider = user.connectedProviders.firstOrNull()
        }
        userJpaRepository.save(user)
    }

    /** 변경: (token: String) -> (userId: Long) */
    @Transactional
    fun disconnectEmail(userId: Long) {
        val user = userJpaRepository.findByIdForUpdate(userId)   // 동시 해제 직렬화
            ?: throw IllegalArgumentException("User not found")

        if (!user.emailVerified || user.email == null) {
            throw IllegalArgumentException("연결된 이메일이 없습니다.")
        }

        if (authMethodCount(user) - 1 < 1) throw LastAuthMethodException()

        user.email = null
        user.emailVerified = false
        user.connectedProviders.remove(AuthProvider.EMAIL)
        if (user.primaryProvider == AuthProvider.EMAIL) {
            user.primaryProvider = user.connectedProviders.firstOrNull()
        }
        userJpaRepository.save(user)
    }

    /** 신규: GET /auth/email/verify-token */
    @Transactional
    fun loginWithEmailToken(emailToken: String): AuthInfoResponse {
        val email = redisTemplate.opsForValue().get("EMAIL_TOKEN:$emailToken")
            ?: throw IllegalArgumentException("유효하지 않거나 만료된 이메일 토큰입니다.")

        val existingUser = userJpaRepository.findByEmail(email)
            ?.also { ensureNotWithdrawn(it, restore = false) }
        val isNewUser = existingUser == null
        val user = existingUser
            ?: userJpaRepository.save(
                UserEntity(email = email, nickname = email.substringBefore("@"))
            )

        user.connectedProviders.add(AuthProvider.EMAIL)
        if (user.primaryProvider == null) user.primaryProvider = AuthProvider.EMAIL
        user.emailVerified = true
        user.lastLoginAt = LocalDateTime.now()
        userJpaRepository.save(user)

        redisTemplate.delete("EMAIL_TOKEN:$emailToken")   // 1회용

        return AuthInfoResponse(
            accessToken = jwtTokenProvider.getAccessToken(user.userId),
            refreshToken = jwtTokenProvider.getRefreshToken(user.userId),
            user = user.toResponse(),
            authId = user.email ?: user.userId.toString(),
            provider = AuthProvider.EMAIL,
            isNewUser = isNewUser,
        )
    }
}