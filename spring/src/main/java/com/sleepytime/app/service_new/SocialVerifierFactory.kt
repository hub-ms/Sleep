package com.sleepytime.app.service_new

import com.sleepytime.shared.enum_.AuthProvider
import org.springframework.stereotype.Component

@Component
class SocialVerifierFactory(
    private val googleVerifier: GoogleVerifier,
    private val kakaoVerifier: KakaoVerifier,
    private val appleVerifier: AppleVerifier
) {
    fun get(provider: AuthProvider): SocialVerifier {
        return when (provider) {
            AuthProvider.GOOGLE -> googleVerifier
            AuthProvider.KAKAO -> kakaoVerifier
            AuthProvider.APPLE -> appleVerifier
            else -> throw IllegalArgumentException("지원하지 않는 소셜 공급자입니다: $provider")
        }
    }
}