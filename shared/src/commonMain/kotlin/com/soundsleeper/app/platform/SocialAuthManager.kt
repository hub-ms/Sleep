package com.soundsleeper.app.platform

expect class SocialAuthManager {
    suspend fun getGoogleToken(): String?
    suspend fun getKakaoToken(): String?
    suspend fun getAppleToken(): String?
}