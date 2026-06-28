package com.sleepytime.app.service_new

import com.sleepytime.app.dto_new.mapper_new.toResponse
import com.sleepytime.app.config.JwtTokenProvider
import com.sleepytime.app.entity_new.UserEntity
import com.sleepytime.app.repository_new.UserJpaRepository
import com.sleepytime.app.repository_new.AuthInfoJpaRepository
import com.sleepytime.app.entity_new.AuthInfoEntity
import com.sleepytime.app.scheduler.UserDeletionScheduler
import com.sleepytime.shared.data.remote.dto.request.EmailVerifyRequest
import com.sleepytime.shared.data.remote.dto.response.AuthInfoResponse
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.util.NicknameGenerator
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.core.Authentication
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

    @Transactional
    fun socialLogin(provider: AuthProvider, accessToken: String): AuthInfoResponse {
        val verifier = socialVerifierFactory.get(provider)
        val socialInfo = verifier.verify(accessToken)

        // 1. socialId + provider로 기존 소셜 계정 조회
        val existingSocialEntity = authInfoJpaRepository.findByAuthIdAndProvider(
            socialInfo.socialId,
            socialInfo.provider
        )

        // 2. 기존 소셜 계정이 있으면 해당 사용자 반환
        if (existingSocialEntity != null) {
            val user = existingSocialEntity.user ?: throw IllegalStateException("연결된 유저 엔티티가 없습니다.")

            user.connectedProviders.add(provider)
            if (user.primaryProvider == null) user.primaryProvider = provider

            val userAccessToken = jwtTokenProvider.getAccessToken(user.userId)
            val userRefreshToken = jwtTokenProvider.getRefreshToken(user.userId)

            // [해결] 리시버 스마트 캐스트 및 명확한 변환
            val userResponse = user.toResponse()

            return existingSocialEntity.toResponse(
                accessToken = userAccessToken,
                refreshToken = userRefreshToken,
                userResponse = userResponse
            )
        }

        // 3. 해당 소셜 계정은 없지만, 다른 소셜 계정으로 연결된 사용자 찾기
        val existingUser = authInfoJpaRepository.findUserBySocialIdAndProvider(
            socialInfo.socialId,
            socialInfo.provider
        )

        val user = existingUser ?: run {
            // 4. 완전 신규 사용자 생성
            val baseNickname = socialInfo.nickname.takeIf { it.isNotBlank() }
            val nickname = if (baseNickname != null) {
                "${baseNickname}_${(100..999).random()}"
            } else {
                NicknameGenerator.generate()
            }

            val email = socialInfo.email ?: "${socialInfo.socialId}@${socialInfo.provider}.user"

            // [해결] JPA 엔티티 제약에 따라 authInfo 매핑 파라미터 제외 (UserEntity 정의에서 이미 기본값 세팅됨)
            userJpaRepository.save(
                UserEntity(
                    email = email,
                    nickname = nickname
                )
            )
        }
        user.connectedProviders.add(provider)
        if (user.primaryProvider == null) user.primaryProvider = provider

        // 5. 사용자에 새 소셜 계정 연결 (다중 계정)
        val newSocialEntity = authInfoJpaRepository.save(
            AuthInfoEntity(
                provider = socialInfo.provider,
                authId = socialInfo.socialId,
                user = user
            )
        )

        val userAccessToken = jwtTokenProvider.getAccessToken(user.userId)
        val userRefreshToken = jwtTokenProvider.getRefreshToken(user.userId)
        val userResponse = user.toResponse()

        return newSocialEntity.toResponse(
            accessToken = userAccessToken,
            refreshToken = userRefreshToken,
            userResponse = userResponse
        )
    }

    @Transactional
    fun connectSocial(userId: Long, provider: AuthProvider, accessToken: String) {
        val verifier = socialVerifierFactory.get(provider)
        val socialInfo = verifier.verify(accessToken)

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
            authInfoJpaRepository.save(existingByUser)
            return
        }

        val user = userJpaRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("유저 없음") }

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
        authInfoJpaRepository.delete(existingSocial)

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

        val user = userJpaRepository.findByEmail(request.email) ?: run {
            // [해결] JPA 엔티티 구조와 규격 불일치로 터지는 authInfo 파라미터 제외
            val newUser = UserEntity(
                email = request.email,
                nickname = request.email.split("@")[0]
            )
            userJpaRepository.save(newUser)
        }

        val accessToken = jwtTokenProvider.getAccessToken(user.userId)
        val refreshToken = jwtTokenProvider.getRefreshToken(user.userId)

        val emailAuthInfo = User.AuthInfo.Member(
            memberEmail = user.email,
            id = user.email ?: "unknown",
            authProvider = AuthProvider.EMAIL
        )

        return AuthInfoResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = user.toResponse(),
            authId = emailAuthInfo.authId,
            provider = AuthProvider.EMAIL
        )
    }

    fun connectEmail(jwt: String, emailToken: String) {
        val userId = jwtTokenProvider.extractUserId(jwt)
        val email = redisTemplate.opsForValue().get("EMAIL_TOKEN:$emailToken")
            ?: throw IllegalArgumentException("유효하지 않거나 만료된 이메일 토큰입니다.")

        val user = userJpaRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("유저 없음") }

        if (userJpaRepository.existsByEmail(email)) {
            throw IllegalStateException("이미 사용 중인 이메일")
        }

        user.updateEmail(email)
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

        val accessToken = jwtTokenProvider.getAccessToken(user.userId)
        val newRefreshToken = jwtTokenProvider.getRefreshToken(user.userId)

        val currentPrimaryProvider = user.primaryProvider ?: AuthProvider.EMAIL
        val userAuthInfo = User.AuthInfo.Member(
            memberEmail = user.email,
            id = user.email ?: user.userId.toString(),
            authProvider = currentPrimaryProvider
        )

        return AuthInfoResponse(
            accessToken = accessToken,
            refreshToken = newRefreshToken,
            user = user.toResponse(),
            authId = userAuthInfo.authId,
            provider = currentPrimaryProvider
        )
    }

    fun logout(authentication: Authentication, accessToken: String?) {
        val identifier = authentication.name
        redisTemplate.delete("REFRESH_TOKEN:$identifier")
        accessToken?.let { token ->
            val expiration = jwtTokenProvider.getRemainingTime(token)
            if (expiration > 0L) {
                redisTemplate.opsForValue().set(
                    "BLACKLIST:$token",
                    "logout",
                    expiration,
                    TimeUnit.MILLISECONDS
                )
            }
        }
    }

    @Transactional
    fun withdraw(userId: Long, accessToken: String) {
        val user = userJpaRepository.findById(userId).orElseThrow { IllegalArgumentException("No existing user") }

        user.isDeleted = true
        user.deletedAt = LocalDateTime.now()
        user.deleteAfter = LocalDateTime.now().plusDays(7)

        userJpaRepository.save(user)

        val expiration = jwtTokenProvider.getRemainingTime(accessToken)
        if (expiration > 0L) {
            redisTemplate.opsForValue().set(
                "BLACKLIST:$accessToken",
                "withdraw",
                expiration,
                TimeUnit.MILLISECONDS
            )
        }
    }

    fun changePrimaryProvider(token: String, provider: AuthProvider) {
        val userId = jwtTokenProvider.extractUserId(token)
        val user = userJpaRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User not found")
        }
        user.primaryProvider = provider
        userJpaRepository.save(user)
    }

    fun disconnectProvider(token: String, provider: AuthProvider) {
        val userId = jwtTokenProvider.extractUserId(token)
        val user = userJpaRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User not found")
        }
        user.connectedProviders.remove(provider)

        if (user.primaryProvider == provider) {
            user.primaryProvider = user.connectedProviders.firstOrNull()
        }
        userJpaRepository.save(user)
    }

    fun disconnectEmail(token: String) {
        val userId = jwtTokenProvider.extractUserId(token)
        val user = userJpaRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User not found")
        }
        user.email = null
        user.emailVerified = false
        userJpaRepository.save(user)
    }
}