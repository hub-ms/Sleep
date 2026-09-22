package com.sleepytime.app.service_new

import com.sleepytime.app.dto_new.mapper_new.toResponse
import com.sleepytime.app.entity_new.UserEntity
import com.sleepytime.app.repository_new.AuthInfoJpaRepository
import com.sleepytime.app.repository_new.UserJpaRepository
import com.sleepytime.app.storage.FileStorage
import com.sleepytime.shared.data.remote.dto.response.UserResponse
import com.sleepytime.shared.enum_.AuthProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

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

    private fun applyNickname(user: UserEntity, nickname: String) {
        val trimmed = nickname.trim()
        require(trimmed.length in 2..12) { "닉네임은 2~12자여야 합니다." }
        user.nickname = trimmed
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
    }
}