package com.sleepytime.app.service_new

import com.sleepytime.app.dto_new.SocialLoginInfo
import com.sleepytime.shared.enum_.AuthProvider
import org.springframework.stereotype.Component

@Component
class AppleVerifier : SocialVerifier {
    override fun verify(accessToken: String): SocialLoginInfo {
        // 실제 운영 환경에서는 Apple Public Key를 가져와서 JWT(idToken)를 검증해야 합니다.
        // 여기서는 학습용으로 accessToken을 socialId로 간주하거나 간단한 파싱 로직을 시뮬레이션합니다.
        
        // TODO: Implement actual JWT validation using jose4j or similar library
        
        return SocialLoginInfo(
            email = "apple_user@example.com", // 토큰에서 추출 필요
            nickname = "AppleUser",
            profileImageUrl = null,
            socialId = "apple_$accessToken", // 실제로는 sub claim
            provider = AuthProvider.APPLE
        )
    }
}
