@file:OptIn(ExperimentalSettingsApi::class)

package com.sleepytime.shared.data.local.repository

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.domain.repository.TokenRepository
import com.sleepytime.shared.util.JwtLocalParser
import com.sleepytime.shared.platform.SecureStorage
import com.sleepytime.shared.util.PreferencesKeys.Auth.ACCESS_TOKEN
import com.sleepytime.shared.util.PreferencesKeys.Auth.REFRESH_TOKEN
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TokenRepositoryImpl(
    private val settings: ObservableSettings,
    private val secureStorage: SecureStorage,
    private val jwtLocalParser: JwtLocalParser,
) : TokenRepository {
    override suspend fun getLoggedInUserType(): User.AuthInfo? {
        TODO("Not yet implemented")
    }

    private var cachedAccessToken: String? = null

    override fun observeAccessToken(): Flow<String?> = settings.getStringOrNullFlow(ACCESS_TOKEN)
        .map { encrypted -> encrypted?.let { secureStorage.decrypt(it) } }

    override suspend fun getAccessToken(): String? {
        cachedAccessToken?.let { return it }
        return settings.getStringOrNull(ACCESS_TOKEN)
            ?.let { secureStorage.decrypt(it) }
            ?.also { cachedAccessToken = it }
    }

    override suspend fun saveAccessToken(token: String) {
        cachedAccessToken = token
        settings.putString(ACCESS_TOKEN, secureStorage.encrypt(token))
    }

    override suspend fun clearAccessToken() {
        cachedAccessToken = null
        settings.remove(ACCESS_TOKEN)
    }

    override suspend fun getRefreshToken(): String? =
        settings.getStringOrNull(REFRESH_TOKEN)?.let { secureStorage.decrypt(it) }

    override suspend fun saveRefreshToken(token: String) =
        settings.putString(REFRESH_TOKEN, secureStorage.encrypt(token))

    override suspend fun clearRefreshToken() = settings.remove(REFRESH_TOKEN)

    override fun isLoggedIn(): Flow<Boolean> = settings.getStringOrNullFlow(REFRESH_TOKEN)
        .map { !it.isNullOrEmpty() && isRefreshTokenAvailable() }

    override suspend fun isAccessTokenValid(): Boolean =
        getAccessToken()?.let { !jwtLocalParser.isExpired(it) } ?: false

    override suspend fun isRefreshTokenAvailable(): Boolean {
        val token = getRefreshToken() ?: return false
        return token.isNotEmpty() && !jwtLocalParser.isExpired(token)
    }

    override suspend fun isSessionAlive(): Boolean = isAccessTokenValid() || isRefreshTokenAvailable()
}