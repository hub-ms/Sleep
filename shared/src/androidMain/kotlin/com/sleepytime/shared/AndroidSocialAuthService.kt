package com.sleepytime.shared

import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.platform.SocialAuthService
import com.sleepytime.shared.platform.SocialAuthManager

class AndroidSocialAuthService(
    private val socialAuthManager: SocialAuthManager
) : SocialAuthService {
    override suspend fun getSocialToken(provider: AuthProvider): Result<String> {
        val token = when (provider) {
            AuthProvider.GOOGLE -> socialAuthManager.getGoogleToken()
            AuthProvider.KAKAO -> socialAuthManager.getKakaoToken()
            AuthProvider.APPLE -> socialAuthManager.getAppleToken()
            else -> null
        }
        return if (token != null) Result.success(token) else Result.failure(Exception("Login Failed"))
    }
}