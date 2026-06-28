package com.sleepytime.shared.domain.repository

import com.sleepytime.shared.domain.model.User
import kotlinx.coroutines.flow.Flow

interface TokenRepository {
    suspend fun getLoggedInUserType(): User.AuthInfo?
    suspend fun getAccessToken(): String?
    fun observeAccessToken(): Flow<String?>
    suspend fun clearAccessToken()
    suspend fun saveAccessToken(token: String)


    suspend fun getRefreshToken(): String?
    suspend fun clearRefreshToken()
    suspend fun saveRefreshToken(token: String)

    fun isLoggedIn(): Flow<Boolean>
    suspend fun isAccessTokenValid(): Boolean
    suspend fun isRefreshTokenAvailable(): Boolean
    suspend fun isSessionAlive(): Boolean
}

