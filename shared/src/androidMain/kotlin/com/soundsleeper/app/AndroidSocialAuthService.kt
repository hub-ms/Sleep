@file:OptIn(InternalVoyagerApi::class)

package com.soundsleeper.app

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.media3.common.util.UnstableApi
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import com.russhwolf.settings.ExperimentalSettingsApi
import com.soundsleeper.app.enum_.AuthProvider
import com.soundsleeper.app.platform.SocialAuthService
import com.soundsleeper.app.platform.SocialAuthManager
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
            else -> null
        }
        return if (token != null) Result.success(token) else Result.failure(Exception("Login Failed"))
    }
}