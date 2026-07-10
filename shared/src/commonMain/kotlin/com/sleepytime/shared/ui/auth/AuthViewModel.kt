@file:OptIn(ExperimentalSettingsApi::class)

package com.sleepytime.shared.ui.auth

import androidx.compose.material3.ExperimentalMaterial3Api
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.sleepytime.shared.domain.model.AuthStatus
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.domain.repository.AuthRepository
import com.sleepytime.shared.domain.repository.TokenRepository
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.platform.SocialAuthService
import com.sleepytime.shared.ui.navigation.HomeScreen
import com.sleepytime.shared.ui.navigation.OnboardingScreen
import com.sleepytime.shared.util.PreferencesKeys.App.FIRST_LAUNCH
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val tokenRepository: TokenRepository,
    private val socialAuthService: SocialAuthService,
    private val settings: ObservableSettings,
) : ScreenModel {

    private val _startDestination = MutableStateFlow<Screen?>(null)

    private val _state = MutableStateFlow(AuthContract.State())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<AuthContract.Effect>()
    val effect = _effect.asSharedFlow()

    private val _intentChannel = Channel<AuthContract.Intent>(Channel.BUFFERED)

    val navigateToHomeEffect = effect.filterIsInstance<AuthContract.Effect.NavigateToHome>()

    init {
        // 1. 온보딩 여부에 따른 초기 목적지 판단
        screenModelScope.launch {
            _startDestination.value = if (settings.getBoolean(FIRST_LAUNCH, true)) OnboardingScreen else HomeScreen()
        }

        // 2. 단일 인텐트 파이프라인 collect
        _intentChannel.receiveAsFlow()
            .onEach { processIntent(it) }
            .launchIn(screenModelScope)

        // 3. 리액티브 핵심: AppState 대신 AuthStatus 실시간 관찰 분기 처리
        observeAuthStatusPipeline()
    }

    private fun observeAuthStatusPipeline() {
        authRepository.observeAuthStatus()
            .onEach { authStatus ->
                when (authStatus) {
                    is AuthStatus.Loading -> {
                        _state.update { it.copy(isLoading = true) }
                    }
                    is AuthStatus.LoggedIn -> {
                        _state.update {
                            it.copy(
                                isAuthenticated = true,
                                isLoading = false,
                                user = authStatus.user,
                                email = authStatus.user.email.orEmpty(),
                                connectedProviders = authStatus.user.connectedProviders + authStatus.provider,
                                isEmailConnected = authStatus.user.isEmailConnected,
                                userType = User.AuthInfo.Member(
                                    memberEmail = authStatus.user.email.orEmpty(),
                                    id = authStatus.user.userId.toString(),
                                    authProvider = authStatus.provider
                                )
                            )
                        }
                        _effect.emit(AuthContract.Effect.NavigateToHome)
                    }
                    is AuthStatus.NotLoggedIn, AuthStatus.LoggedOut, AuthStatus.FirstLaunch -> {
                        _state.update {
                            it.copy(
                                isAuthenticated = false,
                                isLoading = false,
                                userType = User.AuthInfo.Guest
                            )
                        }
                    }
                    is AuthStatus.TokenExpired -> {
                        _state.update {
                            it.copy(
                                isAuthenticated = false,
                                isLoading = false,
                                userType = User.AuthInfo.Guest,
                                message = "세션이 만료되었습니다. 다시 로그인해주세요."
                            )
                        }
                    }
                }
            }.launchIn(screenModelScope)
    }

    fun sendIntent(intent: AuthContract.Intent) {
        screenModelScope.launch { _intentChannel.send(intent) }
    }

    private suspend fun processIntent(intent: AuthContract.Intent) = when (intent) {
        is AuthContract.Intent.SocialLoginClicked -> socialLogin(intent.provider)
        is AuthContract.Intent.SocialConnectClicked -> connectSocial(intent.provider)
        is AuthContract.Intent.EmailLoginClicked -> {
            _effect.emit(AuthContract.Effect.NavigateToEmailAuth(null, null))
        }
        is AuthContract.Intent.EmailConnectClicked -> _effect.emit(
            AuthContract.Effect.NavigateToEmailAuth(null, "connect")
        )
        is AuthContract.Intent.SendAuthCodeClicked -> sendAuthCode(intent.email)
        is AuthContract.Intent.VerifyEmailToken -> {
            intent.token?.let {
                if (intent.from == "connect") connectEmail(it) else verifyToken(it)
            }
        }
        is AuthContract.Intent.DeepLinkAuthSuccess -> {
            _state.update { it.copy(message = "인증이 완료되었습니다.") }
        }
        is AuthContract.Intent.EmailLoginSubmitted -> _effect.emit(AuthContract.Effect.NavigateToHome)
        is AuthContract.Intent.GuestLoginClicked -> _effect.emit(AuthContract.Effect.NavigateToHome)

        is AuthContract.Intent.WithdrawClicked -> {
            _state.update { it.copy(withdrawStep = WithdrawStep.WARNING) }
        }
        is AuthContract.Intent.WithdrawContinue -> {
            _state.update { it.copy(withdrawStep = WithdrawStep.CONFIRM_INPUT) }
        }
        is AuthContract.Intent.WithdrawPause -> {
            settings.putBoolean("is_session_paused", true) // SessionManager 대체 로컬 동기화
            _effect.emit(AuthContract.Effect.NavigateToHome)
        }
        is AuthContract.Intent.WithdrawDisableNotification -> {
            settings.putBoolean("is_notification_enabled", false) // SessionManager 대체 로컬 동기화
            _effect.emit(AuthContract.Effect.NavigateToHome)
        }
        is AuthContract.Intent.WithdrawToGuest -> {
            tokenRepository.clearAccessToken()
            tokenRepository.clearRefreshToken()
            _state.update {
                it.copy(
                    isAuthenticated = false,
                    withdrawStep = WithdrawStep.NONE
                )
            }
            _effect.emit(AuthContract.Effect.NavigateToHome)
        }
        is AuthContract.Intent.WithdrawConfirmed -> {
            _state.update { it.copy(withdrawStep = WithdrawStep.LOADING) }
            withdraw()
        }
        is AuthContract.Intent.WithdrawCancelled -> {
            _state.update { it.copy(withdrawStep = WithdrawStep.NONE, withdrawInput = "") }
        }
        is AuthContract.Intent.ResetDataClicked -> resetUserData()

        is AuthContract.Intent.ChangePrimaryProvider -> {
            val jwt = tokenRepository.getAccessToken()
            if (jwt != null && intent.provider != null) {
                authRepository.changePrimaryProvider(jwt = jwt, provider = intent.provider)
                    .onSuccess { _effect.emit(AuthContract.Effect.NavigateToHome) }
                    .onFailure { error -> _state.update { it.copy(message = error.message ?: "변경 실패") } }
            } else {

            }
        }
        is AuthContract.Intent.DisconnectProvider -> {
            val jwt = tokenRepository.getAccessToken()
            if (jwt != null) {
                authRepository.disconnectProvider(jwt = jwt, provider = intent.provider)
                    .onSuccess {
                        _state.update { it.copy(connectedProviders = it.connectedProviders - intent.provider) }
                    }.onFailure { error ->
                        _state.update { it.copy(message = error.message ?: "연결 해제 실패") }
                    }
            } else {

            }
        }
        is AuthContract.Intent.EmailDisconnectClicked -> {
            val jwt = tokenRepository.getAccessToken()
            if (jwt != null) {
                authRepository.disconnectEmail(jwt)
                    .onSuccess { _state.update { it.copy(isEmailConnected = false) } }
                    .onFailure { error -> _state.update { it.copy(message = error.message ?: "이메일 연결 해제 실패") } }
            } else {

            }
        }
        is AuthContract.Intent.LoginBenefitClicked -> _effect.emit(AuthContract.Effect.NavigateToLoginBenefit)
        is AuthContract.Intent.LogoutClicked -> {}
        is AuthContract.Intent.LogoutConfirmed -> {}
        is AuthContract.Intent.LogoutCancelled -> {}
    }

    private suspend fun socialLogin(provider: AuthProvider) {
        Napier.d("socialLogin 시작: $provider")
        _state.update { it.copy(isLoading = true, message = null) }

        val loginResult = when (provider) {
            AuthProvider.GOOGLE -> authRepository.loginWithGoogle()
            AuthProvider.KAKAO -> authRepository.loginWithKakao()
            AuthProvider.APPLE -> authRepository.loginWithApple()
            else -> {
                Result.failure(IllegalArgumentException("지원하지 않는 소셜 로그인 공급자입니다: $provider"))
            }
        }

        loginResult
            .onSuccess { authInfoResponse ->
                Napier.d("소셜 로그인 성공 응답 완료: $provider")
            }
            .onFailure { error ->
                Napier.e("소셜 로그인 최종 실패 ($provider): ${error.message}", error)
                _state.update { it.copy(isLoading = false, message = error.message ?: "로그인에 실패했습니다.") }
            }
    }

    private fun connectSocial(provider: AuthProvider) {
        screenModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            socialAuthService.getSocialToken(provider)
                .onSuccess { socialToken ->
                    val jwt = tokenRepository.getAccessToken() ?: return@onSuccess
                    authRepository.connectSocial(provider, jwt, socialToken)
                        .onSuccess {
                            _state.update {
                                it.copy(isLoading = false, connectedProviders = it.connectedProviders + provider)
                            }
                        }
                        .onFailure { error ->
                            _state.update { it.copy(isLoading = false, message = error.message) }
                        }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, message = error.message) }
                }
        }
    }

    private fun connectEmail(emailToken: String) {
        screenModelScope.launch {
            val jwt = tokenRepository.getAccessToken() ?: return@launch
            authRepository.connectEmail(jwt, emailToken)
                .onSuccess { _state.update { it.copy(isEmailConnected = true) } }
                .onFailure { _state.update { it.copy(message = it.message ?: "이메일 연결 실패") } }
        }
    }

    fun updateEmail(email: String) {
        val hasAtSymbol = email.contains("@")
        val hasValidDomain = email.contains("@") && email.substringAfter("@").contains(".") &&
                email.substringAfter("@").substringBefore(".").isNotEmpty() &&
                email.substringAfterLast(".", "").length >= 2
        val isEmailValid = hasAtSymbol && hasValidDomain
        _state.update {
            it.copy(
                email = email,
                isEmailValid = isEmailValid,
                hasAtSymbol = hasAtSymbol,
                hasValidDomain = hasValidDomain
            )
        }
    }

    private fun sendAuthCode(email: String) {
        screenModelScope.launch {
            _state.update { it.copy(isLoading = true, message = null) }
            authRepository.sendAuthCode(email)
                .onSuccess { _state.update { it.copy(isLoading = false, email = email) } }
                .onFailure { _state.update { it.copy(isLoading = false, message = "이메일 발송 실패") } }
        }
    }

    private fun verifyToken(token: String) {
        screenModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            authRepository.verifyEmailToken(token)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, isAuthenticated = true) }
                    _effect.emit(AuthContract.Effect.NavigateToHome)
                }
                .onFailure { _state.update { it.copy(isLoading = false, message = "인증 실패") } }
        }
    }

    private suspend fun withdraw() {
        runCatching { authRepository.withdraw() }
            .onSuccess {
                _state.update {
                    it.copy(
                        withdrawStep = WithdrawStep.NONE,
                        withdrawInput = "",
                        isAuthenticated = false
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(withdrawStep = WithdrawStep.NONE, message = error.message ?: "탈퇴 실패") }
            }
    }

    private fun resetUserData() {
        screenModelScope.launch {
            runCatching { authRepository.resetLocalUserData() }
                .onSuccess { _state.update { it.copy(resetCompleted = true, message = null) } }
                .onFailure { _state.update { it.copy(message = "데이터 초기화 실패") } }
        }
    }
}