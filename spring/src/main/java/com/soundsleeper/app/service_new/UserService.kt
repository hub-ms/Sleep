package com.soundsleeper.app.service_new

import com.soundsleeper.app.dto_new.mapper.toResponse
import com.soundsleeper.app.entity_new.UserEntity
import com.soundsleeper.app.repository_new.AuthInfoJpaRepository
import com.soundsleeper.app.repository_new.UserJpaRepository
import com.soundsleeper.app.storage.FileStorage
import com.soundsleeper.app.data.remote.dto.response.UserResponse
import com.soundsleeper.app.enum_.AuthProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class UserService(
    private val userJpaRepository: UserJpaRepository,
    private val authInfoJpaRepository: AuthInfoJpaRepository,
    private val socialVerifierFactory: SocialVerifierFactory,
    private val fileStorage: FileStorage,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** GET /user/info */
    fun getUserInfo(userId: Long): UserResponse = findActiveUser(userId).toResponse()

    /**
     * POST /user/profile — 기존 updateProfile + refreshSocialProfile 통합
     * 이미지 우선순위: 직접 업로드 > 소셜 갱신 (둘 다 없으면 이미지는 건드리지 않음)
     */
    @Transactional
    fun updateProfile(
        userId: Long,
        nickname: String?,
        email: String?,
        resetImage: Boolean,
        profileImage: MultipartFile?,
        socialProvider: AuthProvider?,
        socialToken: String?,
    ): UserResponse {
        val user = findActiveUser(userId)

        nickname?.takeIf { it.isNotBlank() }?.let { applyNickname(user, it) }
        email?.takeIf { it.isNotBlank() }?.let { applyEmail(user, it) }

        when {
            resetImage -> {
                val previous = user.profileImageUrl
                user.profileImageUrl = null          // 앱에서 ic_profile 플레이스홀더가 표시됨
                previous?.let { runCatching { fileStorage.deleteIfOwned(it) } }
            }
            profileImage != null && !profileImage.isEmpty ->
                applyUploadedImage(user, profileImage, userId)

            socialProvider != null && !socialToken.isNullOrBlank() ->
                applySocialImage(user, userId, socialProvider, socialToken)
        }

        return userJpaRepository.save(user).toResponse()
    }

    // ---------- 내부 처리 ----------

    /**
     * 닉네임 변경 규칙. 클라이언트(AccountSettingScreen 의 NICKNAME_* 상수)가 같은 내용을
     * 안내 문구로 보여주므로, 여기를 바꾸면 그쪽도 함께 바꿔야 한다.
     *
     * 중복 검사는 예전에 없었다. nickname 컬럼에 유니크 제약이 걸려 있어 저장 시점에
     * DataIntegrityViolationException 으로 터졌고, 사용자에게는 이유 없는 500 으로 보였다.
     */
    private fun applyNickname(user: UserEntity, nickname: String) {
        val trimmed = nickname.trim()
        // 바뀐 게 없으면 쿨다운을 소모시키지 않는다. 이미지만 바꾸려고 저장을 눌렀을 때
        // 닉네임도 같이 올라오기 때문에, 여기서 걸리면 이미지 변경까지 막힌다.
        if (trimmed == user.nickname) return

        require(trimmed.length in NICKNAME_MIN_LENGTH..NICKNAME_MAX_LENGTH) {
            "닉네임은 ${NICKNAME_MIN_LENGTH}~${NICKNAME_MAX_LENGTH}자여야 합니다."
        }
        if (userJpaRepository.existsByNickname(trimmed)) {
            throw IllegalStateException("이미 사용 중인 닉네임입니다.")
        }

        val now = LocalDateTime.now()
        user.nicknameUpdatedAt?.let { lastChanged ->
            val changeableAt = lastChanged.plusDays(NICKNAME_CHANGE_COOLDOWN_DAYS)
            if (now.isBefore(changeableAt)) {
                val remainingDays = ChronoUnit.DAYS.between(now, changeableAt) + 1
                throw IllegalStateException(
                    "닉네임은 ${NICKNAME_CHANGE_COOLDOWN_DAYS}일에 한 번만 바꿀 수 있어요. " +
                        "${remainingDays}일 뒤에 다시 시도해 주세요."
                )
            }
        }

        user.nickname = trimmed
        user.nicknameUpdatedAt = now
    }

    private fun applyEmail(user: UserEntity, email: String) {
        val normalized = email.trim().lowercase()
        if (normalized == user.email) return
        require(EMAIL_REGEX.matches(normalized)) { "이메일 형식이 올바르지 않습니다." }
        if (userJpaRepository.existsByEmail(normalized)) {
            throw IllegalStateException("이미 사용 중인 이메일입니다.")
        }
        user.email = normalized
        user.emailVerified = false   // 폼으로 바꾼 이메일은 미인증 상태 (아래 주의사항 참고)
    }

    private fun applyUploadedImage(user: UserEntity, image: MultipartFile, userId: Long) {
        val previous = user.profileImageUrl
        user.profileImageUrl = fileStorage.upload(image, userId)
        previous?.let { runCatching { fileStorage.deleteIfOwned(it) } }  // 이전 파일 정리 (실패해도 무시)
    }

    private fun applySocialImage(
        user: UserEntity,
        userId: Long,
        provider: AuthProvider,
        socialToken: String,
    ) {
        val linked = authInfoJpaRepository.findByUserIdAndProvider(userId, provider)
            ?: throw IllegalArgumentException("연결되지 않은 소셜 계정입니다: $provider")

        val socialInfo = socialVerifierFactory.get(provider).verify(socialToken)

        // 남의 소셜 토큰으로 내 프로필을 덮어쓰는 것을 차단
        require(linked.authId == socialInfo.socialId) { "다른 소셜 계정의 토큰입니다." }

        val newImage = socialInfo.profileImageUrl
        if (newImage.isNullOrBlank()) {
            log.info("소셜 프로필 이미지 없음: userId={}, provider={}", userId, provider)
            return
        }
        val previous = user.profileImageUrl
        user.profileImageUrl = newImage
        previous?.let { runCatching { fileStorage.deleteIfOwned(it) } }
    }

    private fun findActiveUser(userId: Long): UserEntity {
        val user = userJpaRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("유저를 찾을 수 없습니다: $userId") }
        check(!user.isDeleted) { "탈퇴 처리된 계정입니다." }
        return user
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$")

        const val NICKNAME_MIN_LENGTH = 2
        const val NICKNAME_MAX_LENGTH = 12
        const val NICKNAME_CHANGE_COOLDOWN_DAYS = 30L
    }
}