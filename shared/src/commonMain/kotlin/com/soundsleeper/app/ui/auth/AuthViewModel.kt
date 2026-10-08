
package com.soundsleeper.app.ui.auth

import com.russhwolf.settings.ObservableSettings
import com.soundsleeper.app.data.remote.api.LastAuthMethodError
import com.soundsleeper.app.data.remote.api.WithdrawnAccountError
import com.soundsleeper.app.domain.model.AuthStatus
import com.soundsleeper.app.domain.model.User
import com.soundsleeper.app.domain.repository.AuthRepository
import com.soundsleeper.app.domain.repository.TokenRepository
import com.soundsleeper.app.enum_.AuthProvider
import com.soundsleeper.app.platform.ChatSupportManager
import com.soundsleeper.app.platform.CrashReporter
import com.soundsleeper.app.platform.SocialAuthService
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.auth_change_failed
import com.soundsleeper.app.resources.auth_data_reset_failed
import com.soundsleeper.app.resources.auth_disconnect_failed
import com.soundsleeper.app.resources.auth_disconnect_unavailable
import com.soundsleeper.app.resources.auth_email_connect_failed
import com.soundsleeper.app.resources.auth_email_connected_format
import com.soundsleeper.app.resources.auth_email_disconnect_failed
import com.soundsleeper.app.resources.auth_email_disconnect_unavailable
import com.soundsleeper.app.resources.auth_email_send_failed
import com.soundsleeper.app.resources.auth_login_failed
import com.soundsleeper.app.resources.auth_primary_provider_change_unavailable
import com.soundsleeper.app.resources.auth_profile_image_reset
import com.soundsleeper.app.resources.auth_profile_image_reset_failed
import com.soundsleeper.app.resources.auth_profile_image_update_failed
import com.soundsleeper.app.resources.auth_profile_image_updated
import com.soundsleeper.app.resources.auth_profile_save_failed
import com.soundsleeper.app.resources.auth_profile_saved
import com.soundsleeper.app.resources.auth_session_expired
import com.soundsleeper.app.resources.auth_unsupported_provider
import com.soundsleeper.app.resources.auth_verification_complete
import com.soundsleeper.app.resources.auth_verification_failed
import com.soundsleeper.app.resources.auth_withdraw_failed
import com.soundsleeper.app.resources.auth_withdrawn_account_fallback
import com.soundsleeper.app.ui.common.AppScopedScreenModel
import com.soundsleeper.app.util.PreferencesKeys.App.PENDING_SIGNUP_PAYWALL
import com.soundsleeper.app.util.PreferencesKeys.Settings.KEY_PUSH_ENABLED
import io.github.aakira.napier.Napier
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val socialAuthService: SocialAuthService,
    private val settings: ObservableSettings,
    private val chatSupportManager: ChatSupportManager,
    private val crashReporter: CrashReporter,
    private val tokenRepository: TokenRepository
) : AppScopedScreenModel() {
    private val _state = MutableStateFlow(AuthContract.State())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<AuthContract.Effect>()
    val effect = _effect.asSharedFlow()

    private val _intentChannel = Channel<AuthContract.Intent>(Channel.BUFFERED)
    init {
        _intentChannel.receiveAsFlow()
            .onEach { processIntent(it) }
            .launchIn(modelScope)
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
                        val wasAuthenticated = _state.value.isAuthenticated
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
                                    authId = authStatus.user.userId.toString(),
                                    provider = authStatus.provider
                                )
                            )
                        }
                        // 크래시 리포트를 어떤 사용자의 것인지 구분할 수 있게 한다(PII 아닌 opaque id만).
                        crashReporter.setUserId(authStatus.user.userId.toString())
                        // 로그인 "전환" 시에만 홈으로 보낸다. 이 Flow는 로컬 유저 테이블을 보고 있어
                        // 프로필 갱신처럼 유저 행이 바뀔 때마다 LoggedIn을 다시 흘리는데, 그때마다
                        // emit하면 프로필 수정 직후 화면이 홈으로 튄다.
                        if (!wasAuthenticated) emitPostLoginNavigation()
                    }

                    is AuthStatus.NotLoggedIn, AuthStatus.LoggedOut, AuthStatus.FirstLaunch -> {
                        _state.update {
                            it.copy(
                                isAuthenticated = false,
                                isLoading = false,
                                userType = User.AuthInfo.Guest
                            )
                        }
                        crashReporter.setUserId(null)
                    }

                    is AuthStatus.TokenExpired -> {
                        _state.update {
                            it.copy(
                                isAuthenticated = false,
                                isLoading = false,
                                userType = User.AuthInfo.Guest,
                                message = getString(Res.string.auth_session_expired)
                            )
                        }
                        crashReporter.setUserId(null)
                    }
                }
            }.launchIn(modelScope)
    }

    /**
     * 로그인 성공 직후의 목적지를 고른다.
     *
     * 방금 가입한 사용자는 홈이 아니라 가입 로딩 화면으로 보낸다(이어서 페이월).
     * 플래그는 응답을 저장하는 시점(AuthRepositoryImpl.saveTokensAndUser)에 이미 기록돼 있다.
     */
    private suspend fun emitPostLoginNavigation() {
        if (settings.getBoolean(PENDING_SIGNUP_PAYWALL, false)) {
            _effect.emit(AuthContract.Effect.NavigateToSignUpLoading)
        } else {
            _effect.emit(AuthContract.Effect.NavigateToHome)
        }
    }

    /** 가입 직후 1회용 플래그를 소비한다. 가입 로딩 화면이 페이월로 넘어갈 때 호출한다. */
    fun consumeSignUpPending() {
        settings.putBoolean(PENDING_SIGNUP_PAYWALL, false)
    }

    fun sendIntent(intent: AuthContract.Intent) {
        if (_state.value.isLoading && intent is AuthContract.Intent.SocialLoginClicked) {
            Napier.w("이미 로그인 처리 중입니다.")
            return
        }
        modelScope.launch { _intentChannel.send(intent) }
    }

    private suspend fun processIntent(intent: AuthContract.Intent) = when (intent) {
        is AuthContract.Intent.SocialLoginClicked -> socialLogin(intent.provider)
        is AuthContract.Intent.SocialConnectClicked -> connectSocial(intent.provider)
        is AuthContract.Intent.SocialDisConnectClicked -> {
            val jwt = tokenRepository.getAccessToken()
            if (jwt != null) {
                authRepository.disconnectSocial(jwt = jwt, provider = intent.provider)
                    .onSuccess {
                        _state.update { it.copy(connectedProviders = it.connectedProviders - intent.provider) }
                    }.onFailure { error ->
                        // 마지막 수단 차단(409)은 일반 실패와 달리 차단 안내로 보여준다.
                        if (error is LastAuthMethodError) {
                            _state.update { it.copy(lastAuthMethodBlockMessage = error.message) }
                        } else {
                            val fallback = getString(Res.string.auth_disconnect_failed)
                            _state.update { it.copy(message = error.message ?: fallback) }
                        }
                    }
            } else {
                _state.update { it.copy(message = getString(Res.string.auth_disconnect_unavailable)) }
            }
        }

        is AuthContract.Intent.LastAuthMethodBlockDismissed ->
            _state.update { it.copy(lastAuthMethodBlockMessage = null) }

        is AuthContract.Intent.WithdrawnAccountDismissed ->
            _state.update {
                it.copy(
                    withdrawnAccountMessage = null,
                    isWithdrawnAccountRestorable = false,
                    withdrawnAccountProvider = null,
                )
            }

        is AuthContract.Intent.WithdrawnAccountRestoreConfirmed -> {
            val provider = _state.value.withdrawnAccountProvider
            _state.update {
                it.copy(
                    withdrawnAccountMessage = null,
                    isWithdrawnAccountRestorable = false,
                    withdrawnAccountProvider = null,
                )
            }
            if (provider != null) socialLogin(provider, restore = true) else Unit
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
                    .onSuccess {
                        _state.update {
                            it.copy(
                                isEmailConnected = false,
                                connectedProviders = it.connectedProviders - AuthProvider.EMAIL
                            )
                        }
                    }
                    .onFailure { error ->
                        if (error is LastAuthMethodError) {
                            _state.update { it.copy(lastAuthMethodBlockMessage = error.message) }
                        } else {
                            val fallback = getString(Res.string.auth_email_disconnect_failed)
                            _state.update {
                                it.copy(
                                    message = error.message ?: fallback
                                )
                            }
                        }
                    }
            } else {
                _state.update { it.copy(message = getString(Res.string.auth_email_disconnect_unavailable)) }
            }
        }


        is AuthContract.Intent.SendAuthCodeClicked -> sendAuthCode(intent.email)
        is AuthContract.Intent.SubmitAuthCode -> {
            // from == "connect" 이면 로그인된 계정에 이메일을 붙이고, 아니면 이메일로 로그인한다.
            if (intent.isConnectFlow) connectEmail(intent.email, intent.code)
            else verifyAuthCode(intent.email, intent.code)
        }
        is AuthContract.Intent.EmailConnectedMessageShown ->
            _state.update { it.copy(emailConnectedMessage = null) }
        is AuthContract.Intent.VerifyEmailToken -> {
            intent.token?.let { verifyToken(it) }
        }

        is AuthContract.Intent.DeepLinkAuthSuccess -> {
            _state.update { it.copy(message = getString(Res.string.auth_verification_complete)) }
        }

        is AuthContract.Intent.EmailLoginSubmitted -> _effect.emit(AuthContract.Effect.NavigateToHome)
        is AuthContract.Intent.GuestLoginClicked -> _effect.emit(AuthContract.Effect.NavigateToHome)
        is AuthContract.Intent.UpdateNickname -> {
            _state.update { it.copy(user = it.user?.copy(nickname = intent.nickname)) }
        }

        is AuthContract.Intent.SaveProfile -> {
            modelScope.launch {
                _state.update { it.copy(isProfileImageUploading = true) }
                authRepository.updateProfile(nickname = intent.nickname)
                    .onSuccess { user ->
                        _state.update {
                            it.copy(
                                user = user,
                                isProfileImageUploading = false,
                                message = getString(Res.string.auth_profile_saved)
                            )
                        }
                        _effect.emit(AuthContract.Effect.ProfileSaved)
                    }
                    .onFailure { error ->
                        // 닉네임 길이·중복·30일 제한은 모두 서버가 판단해 문구로 내려준다.
                        val fallback = getString(Res.string.auth_profile_save_failed)
                        _state.update {
                            it.copy(
                                isProfileImageUploading = false,
                                message = error.message ?: fallback
                            )
                        }
                    }
            }
        }
        is AuthContract.Intent.ResetProfileImage -> {
            modelScope.launch {
                _state.update { it.copy(isProfileImageUploading = true) }
                authRepository.updateProfile(resetImage = true)
                    .onSuccess { user ->
                        _state.update {
                            it.copy(
                                user = user,
                                isProfileImageUploading = false,
                                message = getString(Res.string.auth_profile_image_reset)
                            )
                        }
                    }
                    .onFailure { error ->   // 누락되어 실패가 조용히 묻히던 부분
                        val fallback = getString(Res.string.auth_profile_image_reset_failed)
                        _state.update {
                            it.copy(
                                isProfileImageUploading = false,
                                message = error.message ?: fallback
                            )
                        }
                    }
            }
        }

        is AuthContract.Intent.UpdateProfileImage -> {
            modelScope.launch {
                _state.update { it.copy(isProfileImageUploading = true) }
                authRepository.updateProfile(imageBytes = intent.imageBytes)
                    .onSuccess { user ->
                        _state.update {
                            it.copy(
                                user = user,
                                isProfileImageUploading = false,
                                message = getString(Res.string.auth_profile_image_updated)
                            )
                        }
                    }
                    .onFailure { error ->
                        val fallback = getString(Res.string.auth_profile_image_update_failed)
                        _state.update {
                            it.copy(
                                isProfileImageUploading = false,
                                message = error.message ?: fallback
                            )
                        }
                    }
            }
        }

        is AuthContract.Intent.MessageShown -> _state.update { it.copy(message = null) }

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
                    .onFailure { error ->
                        val fallback = getString(Res.string.auth_change_failed)
                        _state.update {
                            it.copy(
                                message = error.message ?: fallback
                            )
                        }
                    }
            } else {
                // FIX: 이전엔 jwt 또는 provider가 없을 때 아무 피드백 없이 조용히 무시됐습니다.
                // 최소한 원인을 알 수 있도록 메시지를 남깁니다.
                _state.update { it.copy(message = getString(Res.string.auth_primary_provider_change_unavailable)) }
            }
        }

        is AuthContract.Intent.LoginBenefitClicked -> _effect.emit(AuthContract.Effect.NavigateToLoginBenefit)


        is AuthContract.Intent.LogoutClicked -> {
            modelScope.launch {
                authRepository.logout()
                chatSupportManager.shutdown()
            }
            _effect.emit(AuthContract.Effect.NavigateToHome)
        }

        is AuthContract.Intent.LogoutConfirmed -> {
            modelScope.launch {
                authRepository.logout()
                chatSupportManager.shutdown()
            }
        }

        is AuthContract.Intent.LogoutCancelled -> {}
    }

    private suspend fun socialLogin(provider: AuthProvider, restore: Boolean = false) {
        Napier.d("socialLogin 시작: $provider (restore=$restore)")
        _state.update {
            it.copy(isLoading = true, message = null, withdrawnAccountMessage = null)
        }

        val loginResult = when (provider) {
            AuthProvider.GOOGLE -> authRepository.loginWithGoogle(restore)
            AuthProvider.KAKAO -> authRepository.loginWithKakao(restore)
            else -> {
                Result.failure(IllegalArgumentException(getString(Res.string.auth_unsupported_provider, provider.toString())))
            }
        }

        loginResult
            .onSuccess {
                Napier.d("소셜 로그인 성공 응답 완료: $provider")
            }
            .onFailure { error ->
                Napier.e("소셜 로그인 최종 실패 ($provider): ${error.message}", error)
                // 탈퇴한 계정은 일반 실패와 다르게 다룬다. 유예 기간이 남았으면 복구할지
                // 물어야 하고, 지났으면 다시 가입하라고 안내해야 한다.
                val withdrawn = error.findWithdrawnAccountError()
                if (withdrawn != null) {
                    val message = withdrawn.message ?: getString(Res.string.auth_withdrawn_account_fallback)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            withdrawnAccountMessage = message,
                            // 서버가 복구 가능할 때만 "복구하시겠어요?"를 보낸다.
                            isWithdrawnAccountRestorable = message.contains("복구"),
                            withdrawnAccountProvider = provider,
                        )
                    }
                    return@onFailure
                }
                val fallback = getString(Res.string.auth_login_failed)
                _state.update {
                    it.copy(
                        isLoading = false,
                        message = error.message ?: fallback
                    )
                }
            }
    }

    /**
     * 소셜 로그인은 Result 를 여러 겹으로 감싸 돌려주므로(저장소의 runCatching 안에서
     * getOrThrow) 원인을 cause 사슬까지 따라가며 찾아야 한다.
     */
    private fun Throwable.findWithdrawnAccountError(): WithdrawnAccountError? {
        var current: Throwable? = this
        while (current != null) {
            if (current is WithdrawnAccountError) return current
            current = current.cause
        }
        return null
    }

    private fun connectSocial(provider: AuthProvider) {
        modelScope.launch {
            _state.update { it.copy(isLoading = true) }
            socialAuthService.getSocialToken(provider)
                .onSuccess { socialToken ->
                    val jwt = tokenRepository.getAccessToken() ?: return@onSuccess
                    authRepository.connectSocial(provider, jwt, socialToken)
                        .onSuccess {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    connectedProviders = it.connectedProviders + provider
                                )
                            }
                        }
                        .onFailure { error ->
                            _state.update { it.copy(isLoading = false, message = error.message) }
                            Napier.e("소셜 연결 실패: ${error.message}")
                        }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, message = error.message) }
                    Napier.e("소셜 토큰 획득 실패: ${error.message}")
                }
        }
    }

    /** 메일로 받은 코드로 로그인/가입한다. */
    private fun verifyAuthCode(email: String, code: String) {
        modelScope.launch {
            _state.update { it.copy(isLoading = true, message = null) }
            authRepository.verifyAuthCode(email, code)
                .onSuccess {
                    _state.update { it.copy(isLoading = false) }
                    // 토큰 저장이 끝나면 observeAuthStatus 가 LoggedIn 을 흘려
                    // emitPostLoginNavigation 이 알아서 목적지를 고른다.
                }
                .onFailure { error ->
                    val fallback = getString(Res.string.auth_verification_failed)
                    _state.update {
                        it.copy(isLoading = false, message = error.message ?: fallback)
                    }
                }
        }
    }

    private fun connectEmail(email: String, code: String) {
        modelScope.launch {
            val jwt = tokenRepository.getAccessToken() ?: return@launch
            _state.update { it.copy(isLoading = true, message = null) }
            authRepository.connectEmail(jwt, email, code)
                .onSuccess {
                    // 화면이 실제로 보는 건 connectedProviders 다. 예전처럼 isEmailConnected 만
                    // 바꾸면 연결에 성공해도 행은 계속 "연결하기"로 남는다.
                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            isEmailConnected = true,
                            connectedProviders = current.connectedProviders + AuthProvider.EMAIL,
                            user = current.user?.copy(email = email),
                            emailConnectedMessage = getString(Res.string.auth_email_connected_format, email),
                        )
                    }
                }
                .onFailure { error ->
                    val fallback = getString(Res.string.auth_email_connect_failed)
                    _state.update {
                        it.copy(isLoading = false, message = error.message ?: fallback)
                    }
                }
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
        modelScope.launch {
            _state.update { it.copy(isLoading = true, message = null) }
            authRepository.sendAuthCode(email)
                // isCodeSent 는 선언만 돼 있고 한 번도 true 가 되지 않아, 코드 입력 단계로
                // 넘어갈 방법이 없었다.
                .onSuccess {
                    _state.update { it.copy(isLoading = false, email = email, isCodeSent = true) }
                }
                .onFailure { _state.update { it.copy(isLoading = false, message = getString(Res.string.auth_email_send_failed)) } }
        }
    }

    private fun verifyToken(token: String) {
        modelScope.launch {
            _state.update { it.copy(isLoading = true) }
            authRepository.verifyEmailToken(token)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, isAuthenticated = true) }
                    emitPostLoginNavigation()
                }
                .onFailure { _state.update { it.copy(isLoading = false, message = getString(Res.string.auth_verification_failed)) } }
        }
    }

    private suspend fun withdraw() {
        runCatching { authRepository.withdraw() }
            .onSuccess {
                _state.update {
                    it.copy(
                        withdrawStep = WithdrawStep.DONE,
                        withdrawInput = "",
                        isAuthenticated = false
                    )
                }
            }
            .onFailure { error ->
                // 실패를 NONE이 아니라 FAILED로 남겨야 로딩 화면이 완료 화면으로 넘어가지 않는다.
                val fallback = getString(Res.string.auth_withdraw_failed)
                _state.update {
                    it.copy(
                        withdrawStep = WithdrawStep.FAILED,
                        message = error.message ?: fallback
                    )
                }
            }
    }

    private fun resetUserData() {
        modelScope.launch {
            runCatching { authRepository.resetLocalUserData() }
                .onSuccess { _state.update { it.copy(resetCompleted = true, message = null) } }
                .onFailure { _state.update { it.copy(message = getString(Res.string.auth_data_reset_failed)) } }
        }
    }

    fun refreshSocialProfile() {
        modelScope.launch {
            val provider = state.value.userType.let { (it as? User.AuthInfo.Member)?.provider }
                ?: return@launch
            if (!provider.isSocial) return@launch // 이메일 유저는 건너뜀

            socialAuthService.getSocialToken(provider)
                .onSuccess { token ->
                    authRepository.updateProfile(
                        socialProvider = provider,
                        socialAccessToken = token,
                    )
                    .onSuccess { user -> _state.update { it.copy(user = user) } }   // 갱신된 이미지 반영
                    .onFailure { error ->
                        Napier.w("소셜 프로필 자동 갱신 실패: ${error.message}")
                    }
                }
                .onFailure { error ->
                    // 카카오톡 로그인 세션이 끊겨있을 경우 등
                    Napier.w("소셜 토큰 획득 실패: ${error.message}")
                }
        }
    }

}