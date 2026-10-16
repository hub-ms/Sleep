
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
import com.soundsleeper.app.resources.auth_disconnect_failed
import com.soundsleeper.app.resources.auth_disconnect_unavailable
import com.soundsleeper.app.resources.auth_email_connect_failed
import com.soundsleeper.app.resources.auth_email_connected_format
import com.soundsleeper.app.resources.auth_email_disconnect_failed
import com.soundsleeper.app.resources.auth_email_disconnect_unavailable
import com.soundsleeper.app.resources.auth_email_send_failed
import com.soundsleeper.app.resources.auth_login_failed
import com.soundsleeper.app.resources.auth_primary_provider_change_unavailable
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
import kotlinx.coroutines.channels.BufferOverflow
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

    // 버퍼 없는 기본 SharedFlow였을 때는, 카카오/구글 로그인이 외부 앱(카카오톡 전환, 구글 계정
    // 선택 picker)을 거치며 화면이 잠깐 recompose되는 사이 구독이 끊겼다 다시 붙는 타이밍에
    // NavigateToHome emit이 그대로 유실됐다. 로그인 자체(state)는 반영되는데 "홈으로 이동"만
    // 안 일어나던 원인. 버퍼를 둬서 구독자가 없는 순간에 emit돼도 다음 구독 시작 시 바로 전달한다.
    private val _effect = MutableSharedFlow<AuthContract.Effect>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
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
                                // 연결 수단은 로컬 캐시에서 알 수 없다. 서버에 물어보기 전까지는
                                // "지금 로그인한 수단 하나는 분명히 연결돼 있다"까지만 안다.
                                //
                                // 예전에는 여기서 user.connectedProviders(아무도 채우지 않아 항상 빈
                                // 집합) + authStatus.provider 를 넣었다. 이 Flow 는 유저 행이 바뀔
                                // 때마다 다시 흐르므로, 방금 연결한 수단이 조용히 지워지고 방금 해제한
                                // 수단이 되살아났다. 집합이 1개로 고정되는 바람에 해제 버튼이 계속
                                // 비활성(눌러도 무반응)이기도 했다.
                                connectedProviders = it.connectedProviders + authStatus.provider,
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
                        // 연결 수단 조회도 같은 이유로 전환 때만 한다(계정 화면은 들어올 때 따로 묻는다).
                        if (!wasAuthenticated) {
                            refreshConnectedProviders()
                            emitPostLoginNavigation()
                        }
                    }

                    is AuthStatus.NotLoggedIn, AuthStatus.LoggedOut, AuthStatus.FirstLaunch -> {
                        _state.update {
                            it.copy(
                                isAuthenticated = false,
                                isLoading = false,
                                userType = User.AuthInfo.Guest,
                                // 로그아웃한 계정의 연결 수단이 다음 로그인으로 새지 않게 비운다.
                                connectedProviders = emptySet(),
                                isEmailConnected = false,
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

    /**
     * 연결된 로그인 수단을 서버 기준으로 다시 맞춘다.
     *
     * 서버의 해제 가드와 같은 계산(AuthService.connectedAuthMethods)이 내려오므로, 이 값을
     * 쓰는 한 화면의 판단과 서버의 판단이 어긋나지 않는다.
     *
     * 실패하면 기존 값을 그대로 둔다. 비워 버리면 "유일한 로그인 수단"으로 오판해 해제를
     * 막아 버리는데, 그건 네트워크가 잠깐 끊긴 것치고는 너무 센 결과다.
     */
    private suspend fun refreshConnectedProviders() {
        authRepository.fetchConnectedProviders()
            .onSuccess { providers ->
                _state.update {
                    it.copy(
                        connectedProviders = providers,
                        isEmailConnected = AuthProvider.EMAIL in providers,
                    )
                }
            }
            .onFailure { Napier.w("연결된 로그인 수단 조회 실패: ${it.message}") }
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
                        // 낙관적으로 먼저 지워 화면이 즉시 반응하게 하고, 서버 기준으로 다시 맞춘다.
                        _state.update { it.copy(connectedProviders = it.connectedProviders - intent.provider) }
                        refreshConnectedProviders()
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
                        refreshConnectedProviders()
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
        is AuthContract.Intent.MessageShown -> _state.update { it.copy(message = null) }

        is AuthContract.Intent.WithdrawClicked -> {
            _state.update { it.copy(withdrawStep = WithdrawStep.WARNING) }
        }

        is AuthContract.Intent.SelectWithdrawReason -> {
            _state.update { it.copy(withdrawReason = intent.reason) }
        }

        is AuthContract.Intent.WithdrawConfirmed -> {
            _state.update { it.copy(withdrawStep = WithdrawStep.DONE) }
            withdraw()
        }

        is AuthContract.Intent.WithdrawCancelled -> {
            _state.update { it.copy(withdrawStep = WithdrawStep.NONE) }
        }
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

        // 계정 화면은 들어올 때마다 서버에 연결 수단을 다시 묻는다. 예전에는 로그인 "전환"
        // 시점(observeAuthStatusPipeline)에서만 불러서, 다른 기기에서 수단을 연결·해제하고
        // 돌아오면 화면이 옛 상태를 그대로 보여줬다.
        is AuthContract.Intent.AccountScreenShown -> refreshConnectedProviders()

        // 탈퇴 대안. 화면 이동은 Effect 로 올려 AppScreens 가 처리한다.
        is AuthContract.Intent.AlternativeTipClicked,
        is AuthContract.Intent.AlternativeGuideClicked ->
            _effect.emit(AuthContract.Effect.NavigateToSleepGuide)

        is AuthContract.Intent.AlternativePrivacyClicked ->
            _effect.emit(AuthContract.Effect.NavigateToPrivacyPolicy)

        // 1:1 문의는 채널톡 메신저를 직접 띄운다(ChatViewModel.openChat 과 같은 절차).
        is AuthContract.Intent.AlternativeSupportClicked -> openSupportChat()
    }

    /**
     * 채널톡 메신저를 띄운다.
     *
     * 로그인 상태면 memberHash 까지 받아 boot 한 뒤 띄워야 상담 이력이 계정에 붙는다.
     * 해시 조회가 실패해도 익명으로라도 열어 주는 편이 "눌러도 아무 일도 없는" 것보다 낫다.
     */
    private suspend fun openSupportChat() {
        val user = runCatching { authRepository.getUser() }.getOrNull()
        if (user != null) {
            val memberHash = authRepository.getChannelTalkHash()
                .onFailure { Napier.e("getChannelTalkHash 실패: ${it.message}", it) }
                .getOrNull()
            chatSupportManager.boot(
                user.userId.toString(),
                user.email,
                user.nickname,
                memberHash,
            )
        }
        chatSupportManager.showMessenger()
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
                            refreshConnectedProviders()
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
                    refreshConnectedProviders()
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