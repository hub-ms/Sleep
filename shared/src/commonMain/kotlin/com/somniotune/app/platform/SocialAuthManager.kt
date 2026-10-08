package com.somniotune.app.platform

expect class SocialAuthManager {
    suspend fun getGoogleToken(): String?
    suspend fun getKakaoToken(): String?
    suspend fun getAppleToken(): String?
}