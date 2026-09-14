@file:OptIn(ExperimentalSettingsApi::class, InternalVoyagerApi::class)

package com.sleepytime.shared.ui.auth

import androidx.compose.material3.ExperimentalMaterial3Api
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
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
import com.sleepytime.shared.util.PreferencesKeys.Settings.KEY_PUSH_ENABLED
import com.sleepytime.shared.util.PreferencesKeys.Settings.KEY_REMINDER_ENABLED
import com.sleepytime.shared.util.PreferencesKeys.Settings.KEY_REMINDER_HOUR
import com.sleepytime.shared.util.PreferencesKeys.Settings.KEY_REMINDER_MINUTE
import com.sleepytime.shared.util.PreferencesKeys.Settings.KEY_REPORT_DELIVERY_METHOD
import com.sleepytime.shared.util.PreferencesKeys.Settings.KEY_UPDATE_ENABLED
import com.sleepytime.shared.util.PreferencesKeys.Settings.KEY_WEEKLY_REPORT_ENABLED
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
        // 1. 온보딩 여부 및 알림 초기값 로드
        screenModelScope.launch {
            val isFirstLaunch = settings.getBoolean(FIRST_LAUNCH, true)
            _state.update {
                it.copy(
                    isPushEnabled = settings.getBoolean(KEY_PUSH_ENABLED, true),
                    isReminderEnabled = settings.getBoolean(KEY_REMINDER_ENABLED, true),
                    isWeeklyReportEnabled = settings.getBoolean(KEY_WEEKLY_REPORT_ENABLED, true),
                    isUpdateEnabled = settings.getBoolean(KEY_UPDATE_ENABLED, true),
                    reminderHour = settings.getInt(KEY_REMINDER_HOUR, 23),
                    reminderMinute = settings.getInt(KEY_REMINDER_MINUTE, 0),
                    reportDeliveryMethod = runCatching {
                        AuthContract.ReportDeliveryMethod.valueOf(
                            settings.getString(KEY_REPORT_DELIVERY_METHOD, AuthContract.ReportDeliveryMethod.PUSH.name)
                        )
                    }.getOrDefault(AuthContract.ReportDeliveryMethod.PUSH)
                )
            }
            _startDestination.value = if (isFirstLaunch) OnboardingScreen else checkAndRestoreSession()
        }

        // 2. 단일 인텐트 파이프라인 collect
        _intentChannel.receiveAsFlow()
            .onEach { processIntent(it) }
            .launchIn(screenModelScope)

        // 3. 리액티브 핵심: AppState 대신 AuthStatus 실시간 관찰 분기 처리
        observeAuthStatusPipeline()
    }

    private suspend fun checkAndRestoreSession(): Screen {
        return when {
            tokenRepository.isAccessTokenValid() -> HomeScreen()
            tokenRepository.isRefreshTokenAvailable() -> {
                val refreshToken = tokenRepository.getRefreshToken()!!
                authRepository.refreshToken(refreshToken)
                    .fold(
                        onSuccess = { HomeScreen() },
                        onFailure = {
                            // refresh 서버 요청 자체가 실패(네트워크 등)
                            HomeScreen() // 낙관적으로 홈 진입, 이후 API 401 시 Auth 플러그인이 재시도
                        }
                    )
            }
            else -> OnboardingScreen
        }
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
        if (_state.value.isLoading && intent is AuthContract.Intent.SocialLoginClicked) {
            Napier.w("이미 로그인 처리 중입니다.")
            return
        }
        screenModelScope.launch { _intentChannel.send(intent) }
    }

    private suspend fun processIntent(intent: AuthContract.Intent) = when (intent) {
        is AuthContract.Intent.SocialLoginClicked -> socialLogin(intent.provider)
        is AuthContract.Intent.SocialConnectClicked -> connectSocial(intent.provider)
        is AuthContract.Intent.SocialDisConnectClicked -> {
            val jwt = tokenRepository.getAccessToken()
            if (jwt != null) {
                authRepository.disconnectProvider(jwt = jwt, provider = intent.provider)
                    .onSuccess {
                        _state.update { it.copy(connectedProviders = it.connectedProviders - intent.provider) }
                    }.onFailure { error ->
                        _state.update { it.copy(message = error.message ?: "연결 해제 실패") }
                    }
            } else {
                _state.update { it.copy(message = "연결을 해제할 수 없습니다. 로그인 상태를 확인해주세요.") }
            }
        }


        is AuthContract.Intent.EmailLoginClicked -> {
            _effect.emit(AuthContract.Effect.NavigateToEmailAuth(null, null))
        }
        is AuthContract.Intent.EmailConnectClicked -> _effect.emit(
            AuthContract.Effect.NavigateToEmailAuth(null, "connect")
        )
        is AuthContract.Intent.EmailDisconnectClicked -> {
            val jwt = tokenRepository.getAccessToken()
            if (jwt != null) {
                authRepository.disconnectEmail(jwt)
                    .onSuccess { _state.update { it.copy(isEmailConnected = false) } }
                    .onFailure { error -> _state.update { it.copy(message = error.message ?: "이메일 연결 해제 실패") } }
            } else {
                _state.update { it.copy(message = "이메일 연결을 해제할 수 없습니다. 로그인 상태를 확인해주세요.") }
            }
        }





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
        is AuthContract.Intent.UpdateNickname -> {
            _state.update { it.copy(user = it.user?.copy(nickname = intent.nickname)) }
        }
        is AuthContract.Intent.UpdateEmail -> {
            _state.update { it.copy(user = it.user?.copy(email = intent.email)) }
        }
        is AuthContract.Intent.SaveProfile -> {
            screenModelScope.launch {
                authRepository.updateProfile(intent.nickname, intent.email, null)
                    .onSuccess {
                        _state.update { it.copy(message = "프로필이 저장되었습니다.") }
                    }
                    .onFailure { error ->
                        _state.update { it.copy(message = error.message ?: "저장 실패") }
                    }
            }
        }
        is AuthContract.Intent.ResetProfileImage -> {
            screenModelScope.launch {
                // 💡 여기서 null을 보내면 서버에서 기본 이미지로 처리하도록 규약 (필요시 수정)
                authRepository.updateProfile(null, null, null) 
                    .onSuccess {
                        _state.update { it.copy(message = "기본 이미지로 설정되었습니다.") }
                    }
            }
        }
        is AuthContract.Intent.UpdateProfileImage -> {
            screenModelScope.launch {
                authRepository.updateProfile(null, null, intent.imageBytes)
                    .onSuccess {
                        _state.update { it.copy(message = "프로필 이미지가 변경되었습니다.") }
                    }
                    .onFailure { error ->
                        _state.update { it.copy(message = error.message ?: "이미지 변경 실패") }
                    }
            }
        }

        is AuthContract.Intent.WithdrawClicked -> {
            _state.update { it.copy(withdrawStep = WithdrawStep.WARNING) }
        }
        is AuthContract.Intent.SelectWithdrawReason -> {
            _state.update { it.copy(withdrawReason = intent.reason) }
        }
        is AuthContract.Intent.WithdrawContinue -> {
            _state.update { it.copy(withdrawStep = WithdrawStep.CONFIRM_INPUT) }
        }
        // FIX: 새로 추가한 분기입니다. CONFIRM_INPUT 단계에서 사용자가 입력하는 문구를
        // State.withdrawInput에 반영해서, "탈퇴"라는 문구를 정확히 입력했는지 검증할 수 있게 합니다.
        is AuthContract.Intent.WithdrawInputChanged -> {
            _state.update { it.copy(withdrawInput = intent.input) }
        }
        is AuthContract.Intent.WithdrawPause -> {
            settings.putBoolean("is_session_paused", true) // SessionManager 대체 로컬 동기화
            _effect.emit(AuthContract.Effect.NavigateToHome)
        }
        is AuthContract.Intent.WithdrawDisableNotification -> {
            settings.putBoolean(KEY_PUSH_ENABLED, false) // SessionManager 대체 로컬 동기화
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
                // FIX: 이전엔 jwt 또는 provider가 없을 때 아무 피드백 없이 조용히 무시됐습니다.
                // 최소한 원인을 알 수 있도록 메시지를 남깁니다.
                _state.update { it.copy(message = "대표 계정을 변경할 수 없습니다. 로그인 상태를 확인해주세요.") }
            }
        }

        is AuthContract.Intent.LoginBenefitClicked -> _effect.emit(AuthContract.Effect.NavigateToLoginBenefit)

        is AuthContract.Intent.TogglePushNotification -> {
            settings.putBoolean(KEY_PUSH_ENABLED, intent.enabled)
            _state.update { it.copy(isPushEnabled = intent.enabled) }
        }
        is AuthContract.Intent.ToggleSleepReminder -> {
            settings.putBoolean(KEY_REMINDER_ENABLED, intent.enabled)
            _state.update { it.copy(isReminderEnabled = intent.enabled) }
        }
        is AuthContract.Intent.ToggleWeeklyReport -> {
            settings.putBoolean(KEY_WEEKLY_REPORT_ENABLED, intent.enabled)
            _state.update { it.copy(isWeeklyReportEnabled = intent.enabled) }
        }
        is AuthContract.Intent.ToggleUpdate -> {
            settings.putBoolean(KEY_UPDATE_ENABLED, intent.enabled)
            _state.update { it.copy(isUpdateEnabled = intent.enabled) }
        }
        is AuthContract.Intent.ChangeReminderTime -> {
            settings.putInt(KEY_REMINDER_HOUR, intent.hour)
            settings.putInt(KEY_REMINDER_MINUTE, intent.minute)
            _state.update { it.copy(reminderHour = intent.hour, reminderMinute = intent.minute) }
        }
        is AuthContract.Intent.ChangeReportDeliveryMethod -> {
            settings.putString(KEY_REPORT_DELIVERY_METHOD, intent.method.name)
            _state.update { it.copy(reportDeliveryMethod = intent.method) }
        }

        is AuthContract.Intent.LogoutClicked -> {
            screenModelScope.launch {
                authRepository.logout()
            }
        }
        is AuthContract.Intent.LogoutConfirmed -> {
            screenModelScope.launch {
                authRepository.logout()
            }
        }
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
    fun refreshSocialProfile() {
        screenModelScope.launch {
            val provider = state.value.userType.let { (it as? User.AuthInfo.Member)?.authProvider } ?: return@launch
            if (!provider.isSocial) return@launch // 이메일 유저는 건너뜀

            socialAuthService.getSocialToken(provider)
                .onSuccess { token ->
                    authRepository.refreshSocialProfile(provider, token)
                        .onFailure { error ->
                            // 💡 수정: 갱신 실패해도 세션을 만료시키지 않고 로그만 출력
                            Napier.w("소셜 프로필 자동 갱신 실패 (네트워크 또는 토큰 만료): ${error.message}")
                            // 아무 동작 하지 않음 (기존 세션 유지)
                        }
                }
                .onFailure { error ->
                    // 카카오톡 로그인 세션이 끊겨있을 경우 등
                    Napier.w("소셜 토큰 획득 실패: ${error.message}")
                }
        }
    }
    private suspend fun handleSessionExpired() {
        tokenRepository.clearAccessToken()
        tokenRepository.clearRefreshToken()
        _state.update {
            it.copy(
                isAuthenticated = false,
                userType = User.AuthInfo.Guest,
                message = "세션이 만료되었습니다. 다시 로그인해주세요."
            )
        }
    }
}