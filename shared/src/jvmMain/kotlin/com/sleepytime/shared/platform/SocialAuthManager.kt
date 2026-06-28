package com.sleepytime.shared.platform

actual class SocialAuthManager {
    actual suspend fun getGoogleToken(): String? = null
    actual suspend fun getKakaoToken(): String? = null
    actual suspend fun getAppleToken(): String? = null
}
