package com.soundsleeper.app.service_new

import com.soundsleeper.app.enum_.AuthProvider
import org.springframework.stereotype.Component

@Component
class SocialVerifierFactory(
    private val googleVerifier: GoogleVerifier,
    private val kakaoVerifier: KakaoVerifier,
) {
    fun get(provider: AuthProvider): SocialVerifier {
        return when (provider) {
            AuthProvider.GOOGLE -> googleVerifier
            AuthProvider.KAKAO -> kakaoVerifier
            else -> throw IllegalArgumentException("지원하지 않는 소셜 공급자입니다: $provider")
        }
    }
}