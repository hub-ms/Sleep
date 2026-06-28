package com.sleepytime.shared.domain.model

import com.sleepytime.shared.enum_.AuthProvider
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

data class User(
    val userId: Long,                     // 시스템 내부 DB 식별자 (Long으로 통일)
    val email: String?,
    val nickname: String,
    val profileImageUrl: String?,
    val connectedProviders: Set<AuthProvider> = emptySet(),
    val isEmailConnected: Boolean = false,
    val isPremium: Boolean,
    val isActive: Boolean,
    val lastLoginAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?,
) {
    @Serializable
    sealed class AuthInfo(
        val authId: String,
        val provider: AuthProvider?
    ) {
        // 2. 부모 생성자 AuthInfo(authId, provider)를 호출하도록 변경합니다.
        object Guest : AuthInfo(authId = "guest", provider = null)

        // 소셜(구글, 카카오, 애플) 및 이메일 회원을 모두 커버하는 Member 클래스
        data class Member(
            val memberEmail: String?, // User의 email과 혼동을 피하기 위해 명칭 변경 (선택사항)
            val id: String,
            val authProvider: AuthProvider,
        ) : AuthInfo(authId = id, provider = authProvider)

        val displayName: String
            get() = when (provider) {
                AuthProvider.KAKAO -> "카카오"
                AuthProvider.GOOGLE -> "구글"
                AuthProvider.APPLE -> "애플"
                AuthProvider.EMAIL -> "이메일"
                null -> "게스트" // provider가 null인 게스트 상태 예외 처리 추가
            }
    }
}