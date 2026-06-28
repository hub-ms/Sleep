package com.sleepytime.app.service_new

import com.sleepytime.shared.enum_.AuthProvider
import org.springframework.stereotype.Component

@Component
class SocialVerifierFactory(
    private val googleVerifier: GoogleVerifier,
    private val kakaoVerifier: KakaoVerifier
) {
    fun get(provider: AuthProvider): SocialVerifier {
        return when (provider) {
            AuthProvider.GOOGLE -> googleVerifier
            AuthProvider.KAKAO -> kakaoVerifier
            else -> throw IllegalArgumentException("지원 안함")
        }
    }
}