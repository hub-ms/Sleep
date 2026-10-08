package com.soundsleeper.app.data.auth

import com.russhwolf.settings.ObservableSettings
import com.soundsleeper.app.domain.repository.TokenRepository
import com.soundsleeper.app.util.PreferencesKeys

class SessionManager(
    private val tokenRepository: TokenRepository,
    private val settings: ObservableSettings
) {
    suspend fun clearSession() {
        tokenRepository.clearAccessToken()
        tokenRepository.clearRefreshToken()
        settings.remove(PreferencesKeys.Auth.SOCIAL_PROVIDER)
    }
    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String
    ) {
        tokenRepository.saveAccessToken(accessToken)
        tokenRepository.saveRefreshToken(refreshToken)
    }
}