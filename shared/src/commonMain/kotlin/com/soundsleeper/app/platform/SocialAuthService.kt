package com.soundsleeper.app.platform

import com.soundsleeper.app.enum_.AuthProvider

interface SocialAuthService {
    suspend fun getSocialToken(provider: AuthProvider): Result<String>
}