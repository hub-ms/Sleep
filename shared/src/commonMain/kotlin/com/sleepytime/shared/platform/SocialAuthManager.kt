package com.sleepytime.shared.platform

expect class SocialAuthManager {
    suspend fun getGoogleToken(): String?
    suspend fun getKakaoToken(): String?
    suspend fun getAppleToken(): String?
}