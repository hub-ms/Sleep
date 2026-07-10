package com.sleepytime.shared

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.media3.common.util.UnstableApi
import com.russhwolf.settings.ExperimentalSettingsApi
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.platform.SocialAuthService
import com.sleepytime.shared.platform.SocialAuthManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.time.ExperimentalTime

@UnstableApi
@ExperimentalTime
@ExperimentalMaterial3Api
@ExperimentalCoroutinesApi
@ExperimentalSettingsApi
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