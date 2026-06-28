package com.sleepytime.shared.domain.repository

import com.sleepytime.shared.domain.model.AuthStatus
import com.sleepytime.shared.data.remote.dto.response.AuthInfoResponse
import com.sleepytime.shared.data.remote.dto.response.UserResponse
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.domain.model.User
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun getUserContext(): User.AuthInfo
    suspend fun socialLogin(provider: AuthProvider, accessToken: String): Result<AuthInfoResponse>
    suspend fun connectSocial(
        provider: AuthProvider,
        jwt: String,
        socialToken: String
    ): Result<Unit>
    suspend fun sendAuthCode(email: String): Result<Unit>
    suspend fun verifyAuthCode(email: String, code: String): Result<UserResponse>
    suspend fun verifyEmailToken(token: String): Result<User>
    suspend fun connectEmail(jwt: String, emailToken: String): Result<HttpResponse>
    suspend fun refreshToken(refreshToken: String): Result<AuthInfoResponse>
    suspend fun logout(): Result<Unit>
    suspend fun loginWithGoogle(): Result<AuthInfoResponse>
    suspend fun loginWithKakao(): Result<AuthInfoResponse>
    suspend fun loginWithApple(): Result<AuthInfoResponse>
    suspend fun getUser(): User?
    fun observeAuthStatus(): Flow<AuthStatus>
    suspend fun withdraw(): Result<Unit>

    suspend fun changePrimaryProvider(jwt: String, provider: AuthProvider): Result<HttpResponse>
    suspend fun disconnectProvider(jwt: String, provider: AuthProvider): Result<HttpResponse>
    suspend fun disconnectEmail(jwt: String): Result<HttpResponse>

    suspend fun resetLocalUserData(): Result<Unit>
    suspend fun saveSocialUser(provider: AuthProvider, userResponse: UserResponse): Result<User>
}
