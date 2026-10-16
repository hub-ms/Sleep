package com.soundsleeper.app.domain.repository

import com.soundsleeper.app.domain.model.AuthStatus
import com.soundsleeper.app.data.remote.dto.response.AuthInfoResponse
import com.soundsleeper.app.data.remote.dto.response.UserResponse
import com.soundsleeper.app.enum_.AuthProvider
import com.soundsleeper.app.domain.model.User
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun getUserContext(): User.AuthInfo
    suspend fun socialLogin(provider: AuthProvider, accessToken: String, restore: Boolean = false): Result<AuthInfoResponse>
    suspend fun connectSocial(
        provider: AuthProvider,
        jwt: String,
        socialToken: String
    ): Result<Unit>
    suspend fun sendAuthCode(email: String): Result<Unit>
    /** 메일로 받은 코드로 로그인/가입하고 토큰을 저장한다. */
    suspend fun verifyAuthCode(email: String, code: String): Result<AuthInfoResponse>
    suspend fun verifyEmailToken(token: String): Result<User>
    suspend fun connectEmail(jwt: String, email: String, code: String): Result<HttpResponse>
    suspend fun refreshToken(refreshToken: String): Result<AuthInfoResponse>
    suspend fun logout(): Result<Unit>
    suspend fun loginWithGoogle(restore: Boolean = false): Result<AuthInfoResponse>
    suspend fun loginWithKakao(restore: Boolean = false): Result<AuthInfoResponse>
    suspend fun getUser(): User?

    /**
     * 서버가 보는, 이 계정에 실제로 연결된 로그인 수단.
     *
     * 로컬 DB 에는 캐시하지 않는다(UserEntity.sq 의 upsertUser 가 위치 기반 `VALUES ?` 라
     * 컬럼 추가가 스키마 마이그레이션까지 끌고 들어간다). 계정 화면에 들어올 때와
     * 연결/해제 직후에 다시 물어보면 충분하다.
     */
    suspend fun fetchConnectedProviders(): Result<Set<AuthProvider>>
    fun observeAuthStatus(): Flow<AuthStatus>
    suspend fun withdraw(reason: String? = null): Result<Unit>

    suspend fun changePrimaryProvider(jwt: String, provider: AuthProvider): Result<HttpResponse>
    suspend fun disconnectSocial(jwt: String, provider: AuthProvider): Result<HttpResponse>
    suspend fun disconnectEmail(jwt: String): Result<HttpResponse>

    suspend fun updateProfile(
        nickname: String? = null,
        email: String? = null,
        imageBytes: ByteArray? = null,
        resetImage: Boolean = false,
        socialProvider: AuthProvider? = null,
        socialAccessToken: String? = null,
    ): Result<User>

    suspend fun resetLocalUserData(): Result<Unit>
    suspend fun saveSocialUser(provider: AuthProvider, userResponse: UserResponse): Result<User>

    suspend fun getChannelTalkHash(): Result<String>
}
