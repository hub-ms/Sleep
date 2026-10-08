package com.somniotune.app.platform

import com.somniotune.app.enum_.AuthProvider

interface SocialAuthService {
    suspend fun getSocialToken(provider: AuthProvider): Result<String>
}