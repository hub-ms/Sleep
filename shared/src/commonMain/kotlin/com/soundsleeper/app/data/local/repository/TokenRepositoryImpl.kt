@file:OptIn(ExperimentalSettingsApi::class)

package com.soundsleeper.app.data.local.repository

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import com.soundsleeper.app.domain.repository.TokenRepository
import com.soundsleeper.app.util.JwtLocalParser
import com.soundsleeper.app.platform.SecureStorage
import com.soundsleeper.app.util.PreferencesKeys.Auth.ACCESS_TOKEN
import com.soundsleeper.app.util.PreferencesKeys.Auth.REFRESH_TOKEN
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TokenRepositoryImpl(
    private val settings: ObservableSettings,
    private val secureStorage: SecureStorage,
    private val jwtLocalParser: JwtLocalParser,
) : TokenRepository {
    private var cachedAccessToken: String? = null

    override fun observeAccessToken(): Flow<String?> = settings.getStringOrNullFlow(ACCESS_TOKEN)
        .map { encrypted -> encrypted?.let { secureStorage.decrypt(it) } }

    override suspend fun getAccessToken(): String? {
        cachedAccessToken?.let { return it }
        return decryptValue(ACCESS_TOKEN)?.also {
            cachedAccessToken = it
        }
    }

    override suspend fun saveAccessToken(token: String) {
        cachedAccessToken = token
        saveEncrypted(ACCESS_TOKEN, token)
    }

    override suspend fun clearAccessToken() {
        cachedAccessToken = null
        removeValue(ACCESS_TOKEN)
    }
    override suspend fun getRefreshToken(): String? = decryptValue(REFRESH_TOKEN)

    override suspend fun saveRefreshToken(token: String) = saveEncrypted(REFRESH_TOKEN, token)

    override suspend fun clearRefreshToken() {
        removeValue(REFRESH_TOKEN)
    }

    override fun isLoggedIn(): Flow<Boolean> = settings.getStringOrNullFlow(REFRESH_TOKEN)
        .map { !it.isNullOrEmpty() && isRefreshTokenAvailable() }

    override suspend fun isAccessTokenValid(): Boolean =
        getAccessToken()?.let { !jwtLocalParser.isExpired(it) } ?: false

    override suspend fun isRefreshTokenAvailable(): Boolean {
        val token = getRefreshToken() ?: return false
        return token.isNotEmpty() && !jwtLocalParser.isExpired(token)
    }

    override suspend fun isSessionAlive(): Boolean = isAccessTokenValid() || isRefreshTokenAvailable()

    private fun decryptValue(
        key: String
    ): String? {
        val encrypted = settings.getStringOrNull(key) ?: return null
        return secureStorage.decrypt(encrypted)
    }
    private fun saveEncrypted(
        key: String,
        value: String
    ) {
        settings.putString(
            key,
            secureStorage.encrypt(value)
        )
    }
    private fun removeValue(
        key: String
    ) {
        settings.remove(key)
    }
}