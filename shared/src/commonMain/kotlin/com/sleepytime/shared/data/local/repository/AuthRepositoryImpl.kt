package com.sleepytime.shared.data.local.repository

import com.russhwolf.settings.ObservableSettings
import com.sleepytime.shared.data.remote.dto.request.UpdateUserRequest
import com.sleepytime.shared.data.local.dao.UserDao
import com.sleepytime.shared.data.local.mapper.toUserDomain
import com.sleepytime.shared.data.local.mapper.toUserEntity
import com.sleepytime.shared.data.remote.api.AuthApi
import com.sleepytime.shared.data.remote.dto.request.EmailConnectRequest
import com.sleepytime.shared.data.remote.dto.request.EmailVerifyRequest
import com.sleepytime.shared.data.remote.dto.request.SocialConnectRequest
import com.sleepytime.shared.data.remote.dto.response.AuthInfoResponse
import com.sleepytime.shared.data.remote.dto.response.UserResponse
import com.sleepytime.shared.data.remote.mapper.responseToUser
import com.sleepytime.shared.domain.model.AuthStatus
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.domain.repository.AuthRepository
import com.sleepytime.shared.domain.repository.TokenRepository
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.platform.SocialAuthManager
import com.sleepytime.shared.util.PreferencesKeys.App.FIRST_LAUNCH
import com.sleepytime.shared.util.PreferencesKeys.Auth.SOCIAL_PROVIDER
import io.github.aakira.napier.Napier
import io.ktor.client.statement.HttpResponse
import io.ktor.client.utils.EmptyContent.contentType
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val tokenRepository: TokenRepository,
    private val userDao: UserDao,
    private val socialAuthManager: SocialAuthManager,
    private val settings: ObservableSettings
) : AuthRepository {

    override suspend fun getUserContext(): User.AuthInfo {
        if (!tokenRepository.isSessionAlive()) return User.AuthInfo.Guest

        val providerString = settings.getStringOrNull(SOCIAL_PROVIDER) ?: return User.AuthInfo.Guest
        val savedUserId = settings.getLong("logged_in_user_id", defaultValue = 0L)

        return when (providerString.uppercase()) {
            AuthProvider.KAKAO.name -> User.AuthInfo.Member("", savedUserId.toString(), AuthProvider.KAKAO)
            AuthProvider.GOOGLE.name -> User.AuthInfo.Member("", savedUserId.toString(), AuthProvider.GOOGLE)
            AuthProvider.APPLE.name -> User.AuthInfo.Member("", savedUserId.toString(), AuthProvider.APPLE)
            AuthProvider.EMAIL.name -> User.AuthInfo.Member("", savedUserId.toString(), AuthProvider.EMAIL)
            else -> User.AuthInfo.Guest
        }
    }

    override fun observeAuthStatus(): Flow<AuthStatus> = combine(
        userDao.observeUser(),
        tokenRepository.observeAccessToken()
    ) { userEntity, token ->
        val providerString = settings.getStringOrNull(SOCIAL_PROVIDER)
        val provider = runCatching { AuthProvider.valueOf(providerString.orEmpty().uppercase()) }.getOrNull()

        when {
            // 💡 저장 중 찰나의 null 방지: 토큰은 있는데 유저가 아직 DB에 안 쓰여진 경우 Loading 반환
            !token.isNullOrEmpty() && provider != null && userEntity == null -> AuthStatus.Loading

            // 1. 유저 정보나 Provider가 없으면 완벽한 로그아웃 상태
            userEntity == null || provider == null -> AuthStatus.NotLoggedIn

            // 2. Refresh 토큰이 완전히 만료된 경우
            !tokenRepository.isRefreshTokenAvailable() -> AuthStatus.TokenExpired(
                user = userEntity.toUserDomain(),
                provider = provider
            )

            // 3. Flow로 넘어온 토큰이 있거나, 실제 저장된 Access Token이 검증될 경우
            !token.isNullOrEmpty() || tokenRepository.isAccessTokenValid() -> {
                val validToken = if (!token.isNullOrEmpty()) token else tokenRepository.getAccessToken().orEmpty()
                AuthStatus.LoggedIn(userEntity.toUserDomain(), provider, validToken)
            }

            // 4. 그 외 경우만 NotLoggedIn
            else -> AuthStatus.NotLoggedIn
        }
    }

    // AuthRepositoryImpl.kt
    override suspend fun socialLogin(provider: AuthProvider, accessToken: String): Result<AuthInfoResponse> =
        authApi.socialLogin(provider, accessToken)
            .onSuccess { authInfo ->
                settings.putString(SOCIAL_PROVIDER, provider.name)
                saveTokensAndUser(authInfo)
                Napier.i("소셜 로그인 성공: provider=$provider")
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

    override suspend fun verifyAuthCode(email: String, code: String): Result<UserResponse> =
        authApi.verifyAuthCode(EmailVerifyRequest(email, code))
            .onSuccess { response ->
                settings.putString(SOCIAL_PROVIDER, "EMAIL")
                saveTokensAndUser(response)
            }
            .map { it.user }

    override suspend fun verifyEmailToken(token: String): Result<User> =
        authApi.verifyEmailToken(token)
            .onSuccess { response ->
                settings.putString(SOCIAL_PROVIDER, "EMAIL")
                saveTokensAndUser(response)
            }
            .map { it.user.responseToUser() }

    override suspend fun connectSocial(provider: AuthProvider, jwt: String, socialToken: String): Result<Unit> =
        authApi.connectSocial(SocialConnectRequest(provider, socialToken))

    override suspend fun sendAuthCode(email: String): Result<Unit> = authApi.sendAuthCode(email)

    override suspend fun connectEmail(jwt: String, emailToken: String): Result<HttpResponse> =
        authApi.connectEmail(EmailConnectRequest(emailToken))

    override suspend fun refreshToken(refreshToken: String): Result<AuthInfoResponse> =
        authApi.refreshToken(refreshToken)
            .onSuccess { response ->
                tokenRepository.saveAccessToken(response.accessToken)
                tokenRepository.saveRefreshToken(response.refreshToken)
            }

    override suspend fun logout(): Result<Unit> = runCatching {
        authApi.logout() // 실패해도 계속 진행 (AuthApi 내부에서 이미 로깅함)
        clearLocalSession()
    }

    override suspend fun withdraw(reason: String?): Result<Unit> =
        authApi.withdraw(reason).onSuccess { clearLocalSession() }

    override suspend fun getUser(): User? = userDao.getUser()?.toUserDomain()

    override suspend fun changePrimaryProvider(jwt: String, provider: AuthProvider): Result<HttpResponse> =
        authApi.changePrimaryProvider(provider)
            .onSuccess { settings.putString(SOCIAL_PROVIDER, provider.name) }

    override suspend fun disconnectProvider(jwt: String, provider: AuthProvider): Result<HttpResponse> =
        authApi.disconnectProvider(provider)

    override suspend fun disconnectEmail(jwt: String): Result<HttpResponse> = authApi.disconnectEmail()

    override suspend fun resetLocalUserData(): Result<Unit> = runCatching {
        settings.remove(SOCIAL_PROVIDER)
        settings.putBoolean(FIRST_LAUNCH, false)
    }

    override suspend fun updateProfile(
        nickname: String?,
        email: String?,
        imageBytes: ByteArray?
    ): Result<Unit> = runCatching {
        authApi.updateProfile(nickname, email, imageBytes).getOrThrow()

        // 💡 성공 시 로컬 정보 동기화를 위해 유저 정보 재조회 (이미지 URL이 서버에서 바뀌었을 수 있음)
        val userResponse = authApi.getUserInfo().getOrNull()
        userResponse?.let {
            val domainUser = it.responseToUser()
            userDao.upsertUser(domainUser.toUserEntity())
            settings.putString("key_user_nickname", domainUser.nickname)
            domainUser.profileImageUrl?.let { url -> settings.putString("key_user_profile_img", url) }
        }
    }

    override suspend fun saveSocialUser(provider: AuthProvider, userResponse: UserResponse): Result<User> = runCatching {
        val user = userResponse.responseToUser()
        userDao.upsertUser(user.toUserEntity())
        settings.putLong("logged_in_user_id", user.userId)
        user.email?.let { settings.putString("key_user_email", it) }
        user
    }.onFailure { e -> Napier.e("소셜 유저 저장 실패: ${e.message}") }

    override suspend fun refreshSocialProfile(provider: AuthProvider, socialAccessToken: String): Result<Unit> =
        authApi.refreshSocialProfile(provider, socialAccessToken)
            .map { userResponse ->
                val domainUser = userResponse.responseToUser()
                userDao.upsertUser(domainUser.toUserEntity())
                settings.putString("key_user_profile_img", domainUser.profileImageUrl.orEmpty())
            }

    private suspend fun clearLocalSession() {
        tokenRepository.clearAccessToken()
        tokenRepository.clearRefreshToken()
        settings.remove(SOCIAL_PROVIDER)
        settings.remove("logged_in_user_id")
        settings.remove("key_user_email")
        settings.remove("key_user_nickname")
        settings.remove("key_user_profile_img")
        userDao.deleteUser()
    }

    private suspend fun saveTokensAndUser(authInfoResponse: AuthInfoResponse) {
        val profileUrl = authInfoResponse.user.profileImageUrl
        val domainUser = authInfoResponse.user.responseToUser()

        // 💡 1. 유저 정보를 먼저 DB에 Upsert (Flip-flop 방지 핵심)
        userDao.upsertUser(domainUser.toUserEntity())
        
        settings.putLong("logged_in_user_id", domainUser.userId)
        settings.putString("key_user_email", domainUser.email.orEmpty())
        profileUrl?.let { settings.putString("key_user_profile_img", it) }

        // 💡 2. 그 다음 토큰을 저장하여 observeAuthStatus Flow를 트리거
        tokenRepository.saveAccessToken(authInfoResponse.accessToken)
        tokenRepository.saveRefreshToken(authInfoResponse.refreshToken)
    }
}