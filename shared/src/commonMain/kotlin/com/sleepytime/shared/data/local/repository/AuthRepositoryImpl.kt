package com.sleepytime.shared.data.local.repository

import com.russhwolf.settings.ObservableSettings
import com.sleepytime.shared.domain.repository.AuthRepository
import com.sleepytime.shared.domain.repository.TokenRepository
import com.sleepytime.shared.domain.model.AuthStatus
import com.sleepytime.shared.platform.SocialAuthManager
import com.sleepytime.shared.data.local.dao.UserDao
import com.sleepytime.shared.data.local.mapper.toUserDomain
import com.sleepytime.shared.data.local.mapper.toUserEntity
import com.sleepytime.shared.data.remote.dto.request.EmailConnectRequest
import com.sleepytime.shared.data.remote.dto.request.EmailVerifyRequest
import com.sleepytime.shared.data.remote.dto.request.SocialConnectRequest
import com.sleepytime.shared.data.remote.dto.response.AuthInfoResponse
import com.sleepytime.shared.data.remote.dto.response.UserResponse
import com.sleepytime.shared.data.remote.mapper.responseToUser
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.util.PreferencesKeys.App.FIRST_LAUNCH
import com.sleepytime.shared.util.PreferencesKeys.Auth.SOCIAL_PROVIDER
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class AuthRepositoryImpl(
    private val httpClient: HttpClient,
    private val tokenRepository: TokenRepository,
    private val userDao: UserDao,
    private val socialAuthManager: SocialAuthManager,
    private val settings: ObservableSettings
) : AuthRepository {

    override suspend fun getUserContext(): User.AuthInfo {
        if (!tokenRepository.isSessionAlive()) {
            return User.AuthInfo.Guest
        }

        val providerString = settings.getStringOrNull(SOCIAL_PROVIDER) ?: return User.AuthInfo.Guest
        val savedUserId = settings.getString("logged_in_user_id", defaultValue = "")
        if (savedUserId.isEmpty()) return User.AuthInfo.Guest

        return when (providerString.uppercase()) {
            AuthProvider.KAKAO.name -> User.AuthInfo.Member("", savedUserId, AuthProvider.KAKAO)
            AuthProvider.GOOGLE.name -> User.AuthInfo.Member("", savedUserId, AuthProvider.GOOGLE)
            AuthProvider.APPLE.name -> User.AuthInfo.Member("", savedUserId, AuthProvider.APPLE)
            AuthProvider.EMAIL.name -> User.AuthInfo.Member("", savedUserId, AuthProvider.EMAIL)
            else -> User.AuthInfo.Guest
        }
    }

    override fun observeAuthStatus(): Flow<AuthStatus> = combine(
        userDao.observeUser(),
        tokenRepository.observeAccessToken()
    ) { userEntity, token ->
        val providerString = settings.getStringOrNull(SOCIAL_PROVIDER)
        val provider = runCatching { AuthProvider.valueOf(providerString.orEmpty().uppercase()) }.getOrNull()

        if (!token.isNullOrEmpty() && userEntity != null && provider != null) {
            AuthStatus.LoggedIn(userEntity.toUserDomain(), provider, token)
        } else {
            AuthStatus.NotLoggedIn
        }
    }

    /**
     * ✅ FIXED: Social login with correct request format
     * - Token in Authorization header with "Bearer " prefix
     * - No request body (token is in header only)
     * - Proper error handling and logging
     */
    override suspend fun socialLogin(provider: AuthProvider, accessToken: String): Result<AuthInfoResponse> = runCatching {
        try {
            Napier.i("소셜 로그인 시작: provider=$provider")

            val response = httpClient.post("auth/social/$provider") {
                contentType(ContentType.Application.Json)
                // ✅ FIXED: Add "Bearer " prefix and use Authorization header
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }

            // ✅ Check response status before parsing
            if (response.status.value >= 400) {
                val errorBody = response.bodyAsText()
                Napier.e("소셜 로그인 실패 - Status: ${response.status}, Body: $errorBody")
                throw Exception("Server error: ${response.status}")
            }

            val authInfo = response.body<AuthInfoResponse>()
            settings.putString(SOCIAL_PROVIDER, provider.name)
            saveTokensAndUser(authInfo)

            Napier.i("소셜 로그인 성공: provider=$provider")
            authInfo
        } catch (e: Exception) {
            Napier.e("socialLogin 에러 - provider=$provider: ${e.message}", e)
            throw e
        }
    }

    override suspend fun loginWithGoogle(): Result<AuthInfoResponse> = runCatching {
        val idToken = socialAuthManager.getGoogleToken() ?: throw Exception("Google Login Cancelled")
        socialLogin(AuthProvider.GOOGLE, idToken).getOrThrow()
    }

    override suspend fun loginWithKakao(): Result<AuthInfoResponse> = runCatching {
        val token = socialAuthManager.getKakaoToken()
            ?: throw Exception("카카오 토큰을 획득하지 못했습니다. (응답 Null)")

        socialLogin(AuthProvider.KAKAO, token).getOrThrow()
    }

    override suspend fun loginWithApple(): Result<AuthInfoResponse> = runCatching {
        val token = socialAuthManager.getAppleToken() ?: throw Exception("Apple Login Cancelled")
        socialLogin(AuthProvider.APPLE, token).getOrThrow()
    }

    override suspend fun verifyAuthCode(email: String, code: String): Result<UserResponse> = runCatching {
        val response = httpClient.post("auth/email/verify") {
            contentType(ContentType.Application.Json)
            setBody(EmailVerifyRequest(email, code))
        }.body<AuthInfoResponse>()

        settings.putString(SOCIAL_PROVIDER, "EMAIL")
        saveTokensAndUser(response)
        response.user
    }

    override suspend fun verifyEmailToken(token: String): Result<User> = runCatching {
        val response = httpClient.get("auth/email/verify-token") { parameter("token", token) }.body<AuthInfoResponse>()
        settings.putString(SOCIAL_PROVIDER, "EMAIL")
        saveTokensAndUser(response)
        response.user.responseToUser()
    }

    override suspend fun connectSocial(provider: AuthProvider, jwt: String, socialToken: String): Result<Unit> = runCatching {
        // ✅ FIXED: Use correct header format
        httpClient.post("auth/social/connect") {
            header(HttpHeaders.Authorization, "Bearer $jwt")
            contentType(ContentType.Application.Json)
            setBody(SocialConnectRequest(provider, socialToken))
        }
    }

    override suspend fun sendAuthCode(email: String): Result<Unit> = runCatching {
        httpClient.post("auth/email/send") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("email" to email))
        }
    }

    override suspend fun connectEmail(jwt: String, emailToken: String): Result<HttpResponse> = runCatching {
        httpClient.post("auth/email/connect") {
            header(HttpHeaders.Authorization, "Bearer $jwt")
            contentType(ContentType.Application.Json)
            setBody(EmailConnectRequest(emailToken))
        }
    }.onFailure { Napier.e("이메일 연결 실패: ${it.message}") }

    override suspend fun refreshToken(refreshToken: String): Result<AuthInfoResponse> = runCatching {
        val response = httpClient.post("auth/refresh") {
            header(HttpHeaders.Authorization, "Bearer $refreshToken")
        }.body<AuthInfoResponse>()
        tokenRepository.saveAccessToken(response.accessToken)
        tokenRepository.saveRefreshToken(response.refreshToken)
        response
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        try {
            httpClient.post("auth/logout")
        } catch (e: Exception) {
            Napier.e("서버 로그아웃 요청 실패: ${e.message}")
        } finally {
            clearLocalSession()
        }
    }

    override suspend fun withdraw(): Result<Unit> = runCatching {
        httpClient.delete("auth/withdraw")
        clearLocalSession()
    }

    override suspend fun getUser(): User? = userDao.getUser()?.toUserDomain()

    override suspend fun changePrimaryProvider(jwt: String, provider: AuthProvider): Result<HttpResponse> = runCatching {
        httpClient.post("auth/change-primary-provider") {
            header(HttpHeaders.Authorization, "Bearer $jwt")
            contentType(ContentType.Application.Json)
            setBody(mapOf("provider" to provider))
        }
    }.onEachSuccess { settings.putString(SOCIAL_PROVIDER, provider.name) }

    override suspend fun disconnectProvider(jwt: String, provider: AuthProvider): Result<HttpResponse> = runCatching {
        httpClient.post("auth/disconnect-provider") {
            header(HttpHeaders.Authorization, "Bearer $jwt")
            contentType(ContentType.Application.Json)
            setBody(mapOf("provider" to provider))
        }
    }

    override suspend fun disconnectEmail(jwt: String): Result<HttpResponse> = runCatching {
        httpClient.post("auth/disconnect-email") {
            header(HttpHeaders.Authorization, "Bearer $jwt")
        }
    }

    override suspend fun resetLocalUserData(): Result<Unit> = runCatching {
        settings.remove(SOCIAL_PROVIDER)
        settings.putBoolean(FIRST_LAUNCH, false)
    }

    override suspend fun saveSocialUser(provider: AuthProvider, userResponse: UserResponse): Result<User> = runCatching {
        val user = userResponse.responseToUser()
        userDao.upsertUser(user.toUserEntity())

        settings.putLong("logged_in_user_id", user.userId)
        user.email?.let { settings.putString("key_user_email", it) }

        user
    }.onFailure { e -> Napier.e("소셜 유저 저장 실패: ${e.message}") }

    private suspend fun clearLocalSession() {
        tokenRepository.clearAccessToken()
        tokenRepository.clearRefreshToken()
        settings.remove(SOCIAL_PROVIDER)
        settings.remove("logged_in_user_id")
        settings.remove("key_user_email")
        settings.remove("key_user_nickname")
        settings.remove("key_user_profile_img")
        userDao.clearUser()
    }

    private suspend fun saveTokensAndUser(authInfoResponse: AuthInfoResponse) {
        tokenRepository.saveAccessToken(authInfoResponse.accessToken)
        tokenRepository.saveRefreshToken(authInfoResponse.refreshToken)

        val domainUser = authInfoResponse.user.responseToUser()
        userDao.upsertUser(domainUser.toUserEntity())

        settings.putLong("logged_in_user_id", domainUser.userId)
        settings.putString("key_user_email", domainUser.email.orEmpty())
    }



    private inline fun <T> Result<T>.onEachSuccess(action: (T) -> Unit): Result<T> {
        if (isSuccess) action(getOrThrow())
        return this
    }
}