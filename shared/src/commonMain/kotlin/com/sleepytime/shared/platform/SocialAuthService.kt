package com.sleepytime.shared.platform

import com.sleepytime.shared.enum_.AuthProvider

interface SocialAuthService {
    suspend fun getSocialToken(provider: AuthProvider): Result<String>
}