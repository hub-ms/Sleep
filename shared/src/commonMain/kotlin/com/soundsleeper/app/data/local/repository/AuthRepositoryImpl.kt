package com.soundsleeper.app.data.local.repository

import com.soundsleeper.app.data.auth.AuthStatusResolver
import com.soundsleeper.app.data.auth.UserCacheManager
import com.soundsleeper.app.data.local.dao.UserDao
import com.soundsleeper.app.data.local.mapper.toUserDomain
import com.soundsleeper.app.data.preferences.AppPreferencesManager
import com.soundsleeper.app.data.remote.api.AuthApi
import com.soundsleeper.app.data.remote.dto.request.EmailConnectRequest
import com.soundsleeper.app.data.remote.dto.request.EmailVerifyRequest
import com.soundsleeper.app.data.remote.dto.request.SocialConnectRequest
import com.soundsleeper.app.data.remote.dto.response.AuthInfoResponse
import com.soundsleeper.app.data.remote.dto.response.UserResponse
import com.soundsleeper.app.data.remote.mapper.responseToUser
import com.soundsleeper.app.data.auth.SessionManager
import com.soundsleeper.app.domain.model.AuthStatus
import com.soundsleeper.app.domain.model.User
import com.soundsleeper.app.domain.repository.AuthRepository
import com.soundsleeper.app.domain.repository.TokenRepository
import com.soundsleeper.app.enum_.AuthProvider
import com.soundsleeper.app.platform.PurchaseIdentityManager
import com.soundsleeper.app.platform.SocialAuthManager
import io.github.aakira.napier.Napier
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val tokenRepository: TokenRepository,
    private val userDao: UserDao,
    private val socialAuthManager: SocialAuthManager,
    private val authStatusResolver: AuthStatusResolver,
    private val userCacheManager: UserCacheManager,
    private val sessionManager: SessionManager,
    private val appPreferencesManager: AppPreferencesManager,
    private val purchaseIdentityManager: PurchaseIdentityManager,
) : AuthRepository {

    override suspend fun getUserContext(): User.AuthInfo {
        if (!tokenRepository.isSessionAlive()) return User.AuthInfo.Guest

        val provider = appPreferencesManager.getProvider() ?: return User.AuthInfo.Guest
        val user = userDao.getUser() ?: return User.AuthInfo.Guest

        return User.AuthInfo.Member(
            memberEmail = user.email,
            authId = user.userId.toString(),
            provider = provider
        )
    }

    override fun observeAuthStatus(): Flow<AuthStatus> = combine(
        userDao.observeUser(),
        tokenRepository.observeAccessToken()
    ) { userEntity, accessToken ->
        val provider = appPreferencesManager.getProvider()
        val validAccessToken = accessToken ?: tokenRepository.getAccessToken() ?: ""
        val hasRefreshToken = tokenRepository.isRefreshTokenAvailable()
        val isAccessTokenValid = tokenRepository.isAccessTokenValid()
        authStatusResolver.resolve(
            userEntity = userEntity,
            provider = provider,
            accessToken = validAccessToken,
            hasRefreshToken = hasRefreshToken,
            isAccessTokenValid = isAccessTokenValid,
        )
    }
    override suspend fun socialLogin(
        provider: AuthProvider,
        accessToken: String,
        restore: Boolean,
    ): Result<AuthInfoResponse> =
        authApi.socialLogin(provider, accessToken, restore)
            .onSuccess { authInfo ->
                appPreferencesManager.saveProvider(provider)
                saveAuthInfo(authInfo)
                Napier.i("소셜 로그인 성공: provider=$provider")
            }

    override suspend fun loginWithGoogle(restore: Boolean): Result<AuthInfoResponse> = runCatching {
        val idToken = socialAuthManager.getGoogleToken() ?: throw Exception("Google Login Cancelled")
        socialLogin(AuthProvider.GOOGLE, idToken, restore).getOrThrow()
    }

    override suspend fun loginWithKakao(restore: Boolean): Result<AuthInfoResponse> = runCatching {
        val token = socialAuthManager.getKakaoToken()
            ?: throw Exception("카카오 토큰을 획득하지 못했습니다. (응답 Null)")
        socialLogin(AuthProvider.KAKAO, token, restore).getOrThrow()
    }

    override suspend fun verifyEmailToken(token: String): Result<User> =
        authApi.verifyEmailToken(token)
            .onSuccess { response ->
                appPreferencesManager.saveProvider(AuthProvider.EMAIL)
                saveAuthInfo(response)
            }
            .map { it.user.responseToUser() }

    override suspend fun connectSocial(provider: AuthProvider, jwt: String, socialToken: String): Result<Unit> =
        authApi.connectSocial(SocialConnectRequest(provider, socialToken))

    override suspend fun sendAuthCode(email: String): Result<Unit> = authApi.sendAuthCode(email)

    override suspend fun verifyAuthCode(email: String, code: String): Result<AuthInfoResponse> =
        authApi.verifyAuthCode(EmailVerifyRequest(email = email, code = code))
            .onSuccess { authInfo ->
                appPreferencesManager.saveProvider(AuthProvider.EMAIL)
                saveAuthInfo(authInfo)
            }

    override suspend fun connectEmail(jwt: String, email: String, code: String): Result<HttpResponse> =
        authApi.connectEmail(EmailConnectRequest(email = email, code = code))

    override suspend fun refreshToken(refreshToken: String): Result<AuthInfoResponse> =
        authApi.refreshToken(refreshToken)
            .onSuccess { response ->
                tokenRepository.saveAccessToken(response.accessToken)
                tokenRepository.saveRefreshToken(response.refreshToken)
            }

    override suspend fun logout(): Result<Unit> = runCatching {
        authApi.logout()
        sessionManager.clearSession()
        userCacheManager.clearUser()
        purchaseIdentityManager.reset()
    }

    override suspend fun withdraw(reason: String?): Result<Unit> =
        authApi.withdraw(reason).onSuccess {
            sessionManager.clearSession()
            userCacheManager.clearUser()
            purchaseIdentityManager.reset()
        }

    override suspend fun getUser(): User? = userDao.getUser()?.toUserDomain()

    override suspend fun changePrimaryProvider(jwt: String, provider: AuthProvider): Result<HttpResponse> =
        authApi.changePrimaryProvider(provider)
            .onSuccess {
                appPreferencesManager.saveProvider(provider)
            }

    override suspend fun disconnectSocial(jwt: String, provider: AuthProvider): Result<HttpResponse> =
        authApi.disconnectSocial(provider)

    override suspend fun disconnectEmail(jwt: String): Result<HttpResponse> =
        authApi.disconnectEmail()

    override suspend fun resetLocalUserData(): Result<Unit> = runCatching {
        appPreferencesManager.resetLocalUserData()
    }

    override suspend fun updateProfile(
        nickname: String?,
        email: String?,
        imageBytes: ByteArray?,
        resetImage: Boolean,
        socialProvider: AuthProvider?,
        socialAccessToken: String?,
    ): Result<User> = authApi.updateProfile(
        nickname = nickname,
        email = email,
        imageBytes = imageBytes,
        resetImage = resetImage,
        socialProvider = socialProvider,
        socialAccessToken = socialAccessToken,
    ).map { response ->
        response.responseToUser().also {
            userCacheManager.saveUser(it)
        }
    }

    override suspend fun getChannelTalkHash(): Result<String> =
        authApi.getChannelTalkHash().map { it.memberHash }

    override suspend fun saveSocialUser(provider: AuthProvider, userResponse: UserResponse): Result<User> = runCatching {
        userResponse.responseToUser().also {
            userCacheManager.saveUser(it)
        }
    }.onFailure { e -> Napier.e("소셜 유저 저장 실패: ${e.message}") }
    private suspend fun saveAuthInfo(authInfoResponse: AuthInfoResponse) {
        saveSignupFlag(authInfoResponse)

        val user = authInfoResponse.user.responseToUser()

        userCacheManager.saveUser(user)
        purchaseIdentityManager.identify(user.userId.toString())
        sessionManager.saveTokens(
            accessToken = authInfoResponse.accessToken,
            refreshToken = authInfoResponse.refreshToken
        )
    }
    private fun saveSignupFlag(
        authInfoResponse: AuthInfoResponse
    ) {
        appPreferencesManager.saveSignupFlag(authInfoResponse.isNewUser)
    }
}