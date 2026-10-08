package com.somniotune.app.data.auth

import com.russhwolf.settings.ObservableSettings
import com.somniotune.app.domain.repository.TokenRepository
import com.somniotune.app.util.PreferencesKeys

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