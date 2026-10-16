@file:OptIn(ExperimentalStdlibApi::class)

package com.soundsleeper.app.ui.navigation
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.soundsleeper.app.platform.rememberPermissionHandler
import com.soundsleeper.app.enum_.PermissionType
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import com.soundsleeper.app.ui.tracking.WakeUpContent
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.russhwolf.settings.ExperimentalSettingsApi
import com.soundsleeper.app.platform.EmailLauncher
import com.soundsleeper.app.ui.setting.AccountSettingContent
import com.soundsleeper.app.ui.setting.SettingContent
import com.soundsleeper.app.ui.alarm.AlarmContract
import com.soundsleeper.app.ui.alarm.AlarmViewModel
import com.soundsleeper.app.ui.auth.AuthContract
import com.soundsleeper.app.ui.auth.AuthViewModel
import com.soundsleeper.app.ui.auth.EmailAuthContent
import com.soundsleeper.app.ui.auth.SignUpLoadingContent
import com.soundsleeper.app.ui.auth.SignUpWelcomeContent
import com.soundsleeper.app.ui.auth.WithdrawCompleteContent
import com.soundsleeper.app.ui.auth.WithdrawStep
import com.soundsleeper.app.ui.home.HomeContent
import com.soundsleeper.app.ui.home.HomeContract
import com.soundsleeper.app.ui.home.HomeViewModel
import com.soundsleeper.app.ui.home.HomeTab
import com.soundsleeper.app.ui.music.MusicContract
import com.soundsleeper.app.ui.music.MusicViewModel
import com.soundsleeper.app.ui.onboarding.OnboardingContent
import com.soundsleeper.app.ui.onboarding.OnboardingSurveyContent
import com.soundsleeper.app.ui.onboarding.SignUpContent
import com.soundsleeper.app.ui.onboarding.SplashContent
import com.soundsleeper.app.domain.model.SleepAnimalType
import com.soundsleeper.app.platform.BiometricAuthenticator
import com.soundsleeper.app.platform.SocialAuthManager
import com.soundsleeper.app.ui.auth.BiometricEnrollDialog
import com.soundsleeper.app.ui.auth.BiometricLockedOverlay
import com.soundsleeper.app.ui.auth.rememberBiometricPromptLabels
import com.soundsleeper.app.ui.auth.EmailFirstSignUpContent
import com.soundsleeper.app.domain.model.resolveAnimalType
import com.soundsleeper.app.util.PreferencesKeys
import com.russhwolf.settings.ObservableSettings
import com.soundsleeper.app.enum_.LegalType
import org.koin.compose.koinInject
import com.soundsleeper.app.ui.paywall.PaywallContract
import com.soundsleeper.app.ui.paywall.PaywallContent
import com.soundsleeper.app.ui.paywall.PaywallViewModel
import com.soundsleeper.app.ui.report.ReportContent
import com.soundsleeper.app.ui.report.ReportContract
import com.soundsleeper.app.ui.report.ReportViewModel
import com.soundsleeper.app.ui.alarm.SleepSettingContent
import com.soundsleeper.app.ui.home.CustomBottomTabBar
import com.soundsleeper.app.ui.setting.ProfileEditContent
import com.soundsleeper.app.ui.setting.WithdrawalReasonContent
import com.soundsleeper.app.ui.setting.WithdrawAlternative
import com.soundsleeper.app.ui.setting.WithdrawalConfirmDetailContent
import com.soundsleeper.app.ui.setting.ChatViewModel
import com.soundsleeper.app.ui.theme.SleepTheme
import com.soundsleeper.app.ui.tracking.TrackingContent
import com.soundsleeper.app.ui.tracking.TrackingContract
import com.soundsleeper.app.ui.tracking.TrackingViewModel
import com.soundsleeper.app.ui.PermissionExplainDialog
import com.soundsleeper.app.ui.PermissionGuideContent
import com.soundsleeper.app.ui.onboarding.PermissionViewModel
import com.soundsleeper.app.ui.setting.AppearanceViewModel
import com.soundsleeper.app.ui.setting.LanguageSettingContent
import com.soundsleeper.app.ui.setting.LegalContent
import com.soundsleeper.app.ui.setting.LegalViewModel
import com.soundsleeper.app.ui.setting.LicenseCreditContent
import com.soundsleeper.app.ui.setting.SettingContract
import com.soundsleeper.app.ui.setting.SettingViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalSettingsApi
@InternalVoyagerApi
object OnboardingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        // 가치 제안만 담당한다. 로그인은 설문·진단 결과를 거친 뒤 SignUpScreen 이 맡는다.
        OnboardingContent(
            onStartSurvey = { navigator.push(OnboardingSurveyScreen) },
            // 설문까지 함께 건너뛴다 — 설문 화면에는 더 이상 자체 건너뛰기가 없다.
            onSkip = { navigator.push(SignUpScreen) }
        )
    }
}

/**
 * 온보딩 설문. 답은 로컬에만 저장한다. 건너뛰기는 앞 화면(가치 제안)에만 있다.
 */
@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalSettingsApi
@InternalVoyagerApi
object OnboardingSurveyScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val settings = koinInject<ObservableSettings>()
        OnboardingSurveyContent(
            onFinish = { answers ->
                // 문항당 답이 여러 개일 수 있어 쉼표로 이어 붙여 저장한다.
                // (ObservableSettings 는 문자열 집합을 그대로 담지 못한다.)
                answers.forEach { (key, value) ->
                    settings.putString(
                        PreferencesKeys.Onboarding.surveyAnswerKey(key),
                        value.joinToString(",")
                    )
                }
                // 답을 해석해 동물 유형까지 지금 정해 둔다. 가입 화면과 이메일 가입
                // 화면이 각자 설문 답을 다시 해석하면 같은 규칙이 두 군데로 갈라진다.
                resolveAnimalType(answers)?.let { animal ->
                    settings.putString(PreferencesKeys.Onboarding.ANIMAL_TYPE, animal.id)
                }
                // 예전에는 여기서 SleepDiagnosisResultScreen 을 거쳤다. 그 화면은 결과를
                // 아무것도 보여주지 않고 "로그인하면 볼 수 있다"고만 말한 뒤 탭을 한 번 더
                // 받는 관문이었다. 이제 가입 화면이 위쪽 절반에서 결과를 직접 보여준다.
                navigator.push(SignUpScreen)
            }
        )
    }
}

/** 가입/로그인. 예전에는 온보딩 페이저 마지막 장에 얹혀 있었다. */
@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalSettingsApi
@InternalVoyagerApi
object SignUpScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        val authState by authViewModel.state.collectAsState()
        val settings = koinInject<ObservableSettings>()
        // 설문을 건너뛴 사용자는 저장된 값이 없어 null 이고, 그러면 캐릭터 영역을 그리지 않는다.
        val animalType = remember {
            SleepAnimalType.fromId(settings.getStringOrNull(PreferencesKeys.Onboarding.ANIMAL_TYPE))
        }
        SignUpContent(
            authState = authState,
            animalType = animalType,
            onGuestLogin = { authViewModel.sendIntent(AuthContract.Intent.GuestLoginClicked) },
            onSocialLogin = { authViewModel.sendIntent(AuthContract.Intent.SocialLoginClicked(it)) },
            onEmailLogin = { authViewModel.sendIntent(AuthContract.Intent.EmailLoginClicked) },
            onNavigateToTerms = { navigator.push(TermsScreen) },
            onNavigateToPrivacy = { navigator.push(PrivacyPolicyScreen) },
            onWithdrawnAccountRestore = {
                authViewModel.sendIntent(AuthContract.Intent.WithdrawnAccountRestoreConfirmed)
            },
            onWithdrawnAccountDismiss = {
                authViewModel.sendIntent(AuthContract.Intent.WithdrawnAccountDismissed)
            },
        )
        LaunchedEffect(Unit) {
            authViewModel.effect.collect { effect ->
                when (effect) {
                    is AuthContract.Effect.NavigateToHome -> navigator.replaceAll(HomeScreen())

                    // 가입 직후에는 홈이 아니라 가입 로딩 → 페이월 순서로 보낸다.
                    is AuthContract.Effect.NavigateToSignUpLoading ->
                        navigator.replaceAll(SignUpLoadingScreen)

                    is AuthContract.Effect.NavigateToEmailAuth -> navigator.push(
                        EmailAuthScreen(
                            effect.token, effect.from
                        )
                    )

                    else -> Unit
                }
            }
        }
    }
}

/**
 * 앱 시작 화면. 로그인 상태가 정해질 때까지 머문 뒤 홈이나 온보딩으로 보낸다.
 *
 * 예전에는 MainActivity 가 무조건 온보딩을 시작 화면으로 띄우고, 인증 파이프라인이 늦게
 * LoggedIn 을 흘리면 그제서야 홈으로 바꿨다. 그래서 이미 로그인한 사용자도 온보딩이
 * 한 번 번쩍이고 지나갔다.
 */
@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalSettingsApi
@InternalVoyagerApi
object SplashScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        val authState by authViewModel.state.collectAsState()
        val settings = koinInject<ObservableSettings>()
        val biometric = koinInject<BiometricAuthenticator>()

        // 지문을 묻다가 취소한 상태. 여기서 멈춰 세우고 다시 시도할 길을 준다 -
        // 바로 온보딩으로 보내 버리면 로그인돼 있는데도 가입 화면이 뜬 것처럼 보인다.
        var isLocked by remember { mutableStateOf(false) }
        var retryToken by remember { mutableIntStateOf(0) }

        val promptLabels = rememberBiometricPromptLabels()

        SplashContent()
        if (isLocked) {
            BiometricLockedOverlay(
                onRetry = { retryToken++ },
                onUseAnotherMethod = {
                    // 생체 인증을 포기한 사람은 평소 로그인으로 돌린다. 저장된 세션은
                    // 지우지 않는다 - 다음에 지문으로 다시 들어올 수 있어야 한다.
                    navigator.replaceAll(OnboardingScreen)
                },
            )
        }

        // isLoading 이 내려가면 로그인 여부가 정해진 것이다. 최소 노출 시간을 함께 둬야
        // 상태가 즉시 정해지는 경우에 화면이 번쩍이고 사라지지 않는다.
        LaunchedEffect(authState.isLoading, authState.isAuthenticated, retryToken) {
            delay(900.milliseconds)
            if (authState.isLoading) return@LaunchedEffect
            if (!authState.isAuthenticated) {
                navigator.replaceAll(OnboardingScreen)
                return@LaunchedEffect
            }

            // 저장된 세션은 이미 복구됐다. 생체 인증은 그 세션을 **쓰기 전에** 사람을
            // 한 번 확인하는 문지기일 뿐, 토큰을 따로 풀어내는 열쇠가 아니다.
            val gateEnabled = settings.getBoolean(
                PreferencesKeys.Auth.BIOMETRIC_LOGIN_ENABLED, false
            ) && biometric.isAvailable()
            if (!gateEnabled) {
                navigator.replaceAll(HomeScreen())
                return@LaunchedEffect
            }

            isLocked = false
            if (biometric.authenticate(
                    promptLabels.title, promptLabels.subtitle, promptLabels.cancel
                )
            ) {
                navigator.replaceAll(HomeScreen())
            } else {
                isLocked = true
            }
        }
    }
}

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalSettingsApi
@InternalVoyagerApi
data class EmailAuthScreen(
    val token: String? = null, val from: String? = null
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        val authState by authViewModel.state.collectAsState()
        // 예전에는 navigateToHomeEffect(= NavigateToHome 만 필터)를 구독해서
        // 가입 직후 목적지를 알 수 없었다. 전체 effect 를 보고 두 갈래를 모두 처리한다.
        LaunchedEffect(Unit) {
            authViewModel.effect.collect { effect ->
                when (effect) {
                    is AuthContract.Effect.NavigateToHome -> navigator.replaceAll(HomeScreen())
                    is AuthContract.Effect.NavigateToSignUpLoading ->
                        navigator.replaceAll(SignUpLoadingScreen)
                    else -> Unit
                }
            }
        }
        // from == "connect" 이면 로그인된 계정에 이메일을 붙이는 흐름이다.
        val isConnectFlow = from == "connect"
        // 예전에는 여기서 아무것도 하지 않는 익명 EmailLauncher 를 만들어 넘겼다. 그래서
        // "인증 메일 보내기"를 눌러도 메일앱이 열리지 않았다. 실제 구현은 Koin 에 있다.
        val emailLauncher = koinInject<EmailLauncher>()
        val socialAuthManager = koinInject<SocialAuthManager>()
        val settings = koinInject<ObservableSettings>()

        // 앱을 깔고 처음 이메일로 가입하는 경우에만 입력 필드 없는 화면을 쓴다.
        // 계정 연동(connect)은 이미 로그인한 사람이 "다른" 주소를 붙이는 것이라 해당 없다.
        var deviceEmail by remember { mutableStateOf<String?>(null) }
        var emailHintChecked by remember { mutableStateOf(false) }
        var preferManualInput by remember { mutableStateOf(false) }
        LaunchedEffect(isConnectFlow) {
            if (!isConnectFlow) deviceEmail = socialAuthManager.getDeviceEmailHint()
            emailHintChecked = true
        }

        val hintedEmail = deviceEmail
        when {
            // OS 가 주소를 알려줬는지 확인하기 전에는 아무것도 그리지 않는다. 입력 화면을
            // 먼저 띄웠다가 카드 화면으로 바꾸면 화면이 한 번 깜빡인다.
            !emailHintChecked -> Unit

            !isConnectFlow && !preferManualInput && hintedEmail != null -> {
                val animalType = remember {
                    SleepAnimalType.fromId(
                        settings.getStringOrNull(PreferencesKeys.Onboarding.ANIMAL_TYPE)
                    )
                }
                EmailFirstSignUpContent(
                    email = hintedEmail,
                    animalType = animalType,
                    isCodeSent = authState.isCodeSent,
                    isLoading = authState.isLoading,
                    onSaveCard = {
                        // 주소 소유를 증명하지 않고 가입을 끝낼 수는 없다. 대신 사용자가
                        // 타이핑할 것은 없다 - 메일의 링크 한 번이면 딥링크로 돌아와
                        // DeepLinkAuthSuccess 가 가입을 마무리한다.
                        authViewModel.updateEmail(hintedEmail)
                        authViewModel.sendIntent(
                            AuthContract.Intent.SendAuthCodeClicked(hintedEmail)
                        )
                        emailLauncher.openEmailApp(hintedEmail)
                    },
                    onOpenMailApp = { emailLauncher.openEmailApp(hintedEmail) },
                    onUseAnotherAddress = { preferManualInput = true },
                    onSocialLogin = {
                        authViewModel.sendIntent(AuthContract.Intent.SocialLoginClicked(it))
                    },
                )
            }

            else -> EmailAuthContent(
                authState = authState,
                isConnectFlow = isConnectFlow,
                onEmailChanged = { authViewModel.updateEmail(it) },
                onSendAuthCode = { authViewModel.sendIntent(AuthContract.Intent.SendAuthCodeClicked(it)) },
                onSubmitAuthCode = { code ->
                    authViewModel.sendIntent(
                        AuthContract.Intent.SubmitAuthCode(
                            email = authState.email,
                            code = code,
                            isConnectFlow = isConnectFlow,
                        )
                    )
                },
                emailLauncher = emailLauncher,
                onSocialLogin = {
                    authViewModel.sendIntent(AuthContract.Intent.SocialLoginClicked(it))
                },
            )
        }

        // 연결이 끝나면 이 화면은 할 일이 없다. 성공 안내는 계정 화면이 띄운다.
        LaunchedEffect(authState.emailConnectedMessage) {
            if (isConnectFlow && authState.emailConnectedMessage != null) navigator.pop()
        }
    }
}
/**
 * 가입 직후 페이월로 넘어가기 전의 짧은 대기 화면.
 *
 * 화면 전환이 곧바로 두 번 일어나면 가입이 끝난 것을 알아채기 어려워서, 1.2초만 머물렀다가
 * 페이월로 넘긴다. 1회용 플래그는 여기서 소비하므로, 페이월에서 되돌아오거나 앱을 다시 켜도
 * 같은 흐름이 반복되지 않는다.
 */
@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalSettingsApi
@InternalVoyagerApi
object SignUpLoadingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()

        LaunchedEffect(Unit) {
            delay(1200.milliseconds)
            authViewModel.consumeSignUpPending()
            // 로딩이 끝나면 곧장 페이월로 보내지 않고 축하 화면을 한 번 거친다.
            navigator.replaceAll(SignUpWelcomeScreen)
        }
        SignUpLoadingContent()
    }
}

/**
 * 가입 축하 화면. 여기서 "시작하기"를 누르면 페이월로 넘어간다.
 *
 * 페이월은 건너뛸 수 있으므로(닫기 → 홈) 이 흐름이 가입을 막지는 않는다.
 */
@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalSettingsApi
@InternalVoyagerApi
object SignUpWelcomeScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        val authState by authViewModel.state.collectAsState()
        val settings = koinInject<ObservableSettings>()
        val biometric = koinInject<BiometricAuthenticator>()

        // 가입이 끝난 직후가 "다음부터 어떻게 로그인할까"를 물을 가장 자연스러운 자리다.
        // 한 번만 묻는다 - 거절한 사람에게 켤 때마다 다시 띄우지 않는다.
        var showBiometricEnroll by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            val alreadyAsked = settings.getBoolean(
                PreferencesKeys.Auth.BIOMETRIC_PROMPT_SHOWN, false
            )
            if (!alreadyAsked && biometric.isAvailable()) showBiometricEnroll = true
        }

        SignUpWelcomeContent(
            nickname = authState.user?.nickname,
            onContinue = { navigator.replaceAll(PaywallScreen(fromSignUp = true)) }
        )

        if (showBiometricEnroll) {
            fun close() {
                settings.putBoolean(PreferencesKeys.Auth.BIOMETRIC_PROMPT_SHOWN, true)
                showBiometricEnroll = false
            }
            BiometricEnrollDialog(
                onConfirm = {
                    // 지금 지문을 받지는 않는다. 방금 가입을 끝낸 사람에게 곧바로
                    // 지문을 대라고 하면 무엇이 더 진행되는지 알 수 없다. 플래그만 켜고
                    // 다음에 앱을 켤 때 스플래시에서 처음 쓴다.
                    settings.putBoolean(PreferencesKeys.Auth.BIOMETRIC_LOGIN_ENABLED, true)
                    close()
                },
                onDismiss = { close() },
            )
        }
    }
}

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalSettingsApi
@InternalVoyagerApi
object WithdrawCompleteScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        WithdrawCompleteContent(
            onNavigateToOnboarding = { navigator.replaceAll(OnboardingScreen) })
    }
}

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalSettingsApi
@InternalVoyagerApi
data class HomeScreen(
    val initialTab: HomeTab? = null,
    val sessionId: String? = null
) : Screen {
    @Composable
    override fun Content() {

        val uriHandler = LocalUriHandler.current
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        val authState by authViewModel.state.collectAsState()
        val homeViewModel = koinScreenModel<HomeViewModel>()
        val alarmViewModel = koinScreenModel<AlarmViewModel>()
        val musicViewModel = koinScreenModel<MusicViewModel>()
        val trackingViewModel = koinScreenModel<TrackingViewModel>()
        val trackingState by trackingViewModel.state.collectAsState()
        val reportViewModel = koinScreenModel<ReportViewModel>()
        val homeState by homeViewModel.state.collectAsState()
        val musicState by musicViewModel.state.collectAsState()
        val chatViewModel = koinScreenModel<ChatViewModel>()
        val settingViewModel = koinScreenModel<SettingViewModel>()

        val permissionViewModel = koinScreenModel<PermissionViewModel>()
        val permissionState by permissionViewModel.state.collectAsState()
        // 권한 요청 뒤 곧바로 측정으로 이어가기 위해 원래 의도를 기억해 둔다.
        var pendingMusicName by remember { mutableStateOf<String?>(null) }
        var hasPendingStart by remember { mutableStateOf(false) }
        // 권한 안내 다이얼로그가 떠 있는 동안 원래 하려던 측정 의도를 기억해 둔다.
        var showPermissionExplain by remember { mutableStateOf(false) }
        var pendingExplainMusicName by remember { mutableStateOf<String?>(null) }

        val permissionProbe = rememberPermissionHandler { type, granted ->
            permissionViewModel.updatePermission(type, granted)
        }

        /** 권한 안내를 거친 뒤(또는 이미 본 적이 있어 건너뛴 뒤) 실제 권한 확인/요청으로 이어간다. */
        fun requestPermissionsThenTrack(musicName: String?) {
            // 최신 권한 상태를 다시 읽어 ViewModel의 상태를 동기화한다(설정에서 권한을 바꿨을 수 있음).
            val current = permissionProbe.checkPermissionState()
            permissionViewModel.updatePermission(PermissionType.AUDIO, current.audio)
            permissionViewModel.updatePermission(PermissionType.NOTIFICATION, current.notification)
            permissionViewModel.updatePermission(PermissionType.ACTIVITY_RECOGNITION, current.activity)
            permissionViewModel.updatePermission(
                PermissionType.BATTERY_OPTIMIZATION,
                current.batteryOptimizationIgnored
            )

            if (!current.isAllGranted()) {
                // 앱이 만든 안내 시트 대신 안드로이드 기본 권한 대화상자를 띄운다.
                // 결과는 위 rememberPermissionHandler 콜백으로 들어오고, 아래 LaunchedEffect 가
                // 전부 허용된 것을 보면 원래 하려던 측정으로 이어 간다.
                pendingMusicName = musicName
                hasPendingStart = true
                permissionProbe.requestTrackingPermissions()
                return
            }

            if (!permissionViewModel.isTrackingGuideShown()) {
                // 첫 측정에만 안내 화면을 거친다.
                navigator.push(
                    PermissionGuideScreen(
                        musicName = musicName,
                        startTrackingOnComplete = true
                    )
                )
                return
            }

            // 권한도 있고 안내도 봤으므로 바로 측정을 시작한다 — 실제 센서/서비스 시작은
            // TrackingViewModel.sendIntent 내부에서 trackingManager.start(...)로 위임된다.
            trackingViewModel.sendIntent(
                TrackingContract.Intent.StartTracking(trackingState.durationMillis, musicName)
            )
        }

        /**
         * "수면 시작" 버튼이 눌렸을 때 실제로 측정을 시작할지 결정하는 함수.
         * 홈 화면의 [com.soundsleeper.app.ui.home.SleepStartButton]→`onStartTracking`이 바로 이 함수를 호출한다.
         * 권한 안내 → 권한 요청(네이티브 대화상자) → 수면 가이드 순서로 최초 1회씩 거치고,
         * 전부 통과한 경우에만 실제로 [TrackingViewModel]에 측정 시작 Intent를 보낸다.
         */
        fun beginTracking(musicName: String?) {
            if (!permissionViewModel.isPermissionGuideShown()) {
                // 왜 이 권한들이 필요한지 먼저 설명한다. 네이티브 권한 대화상자보다 앞선다.
                pendingExplainMusicName = musicName
                showPermissionExplain = true
                return
            }
            requestPermissionsThenTrack(musicName)
        }

        // 이 화면이 리포트용 sessionId를 들고 다시 열렸을 때, 해당 탭을 자동으로 선택한다.
        LaunchedEffect(initialTab, sessionId) {
            if (initialTab != null) {
                homeViewModel.sendIntent(HomeContract.Intent.SelectBottomTab(initialTab))
            }
        }
        // 리포트 탭이 선택될 때마다 보여줄 세션 데이터를 불러온다.
        LaunchedEffect(homeState.selectedTab, sessionId) {
            if (homeState.selectedTab == HomeTab.REPORT) {
                // 측정을 막 끝내고 넘어왔다면 그 세션을 ID로 직접 연다.
                // 최신 세션 추측에만 의존하면 저장 커밋이 늦을 때 미리보기가 떴다.
                if (sessionId != null) {
                    reportViewModel.loadSession(sessionId)
                } else {
                    reportViewModel.refreshToLatestSession()
                }
            }
        }
        LaunchedEffect(Unit) {
            homeViewModel.effect.collect { effect ->
                when (effect) {
                    is HomeContract.Effect.NavigateToSleepSetting -> navigator.push(
                        SleepSettingScreen
                    )
                    is HomeContract.Effect.NavigateToReport -> {
                        homeViewModel.sendIntent(HomeContract.Intent.SelectBottomTab(HomeTab.REPORT))
                    }
                    else -> {}
                }
            }
        }
        // TrackingViewModel이 측정 시작을 완료 처리하면(NavigateToTracking) 측정 화면으로 이동한다.
        // 이것이 홈→측정 화면 전환이 일어나는 실제 지점이다.
        LaunchedEffect(Unit) {
            trackingViewModel.effect.collect { effect ->
                when (effect) {
                    is TrackingContract.Effect.NavigateToTracking -> navigator.push(
                        TrackingScreen(
                            effect.durationMillis,
                            effect.sessionId
                        )
                    )
                    is TrackingContract.Effect.NavigateToPermissionGuide -> navigator.push(
                        PermissionGuideScreen(
                            musicName = trackingState.musicName,
                            startTrackingOnComplete = true
                        )
                    )
                    else -> {}
                }
            }
        }
        LaunchedEffect(Unit) {
            authViewModel.effect.collect { effect ->
                when (effect) {
                    is AuthContract.Effect.NavigateToHome -> navigator.replaceAll(HomeScreen())

                    // 가입 직후에는 홈이 아니라 가입 로딩 → 페이월 순서로 보낸다.
                    is AuthContract.Effect.NavigateToSignUpLoading ->
                        navigator.replaceAll(SignUpLoadingScreen)

                    is AuthContract.Effect.NavigateToEmailAuth -> navigator.push(
                        EmailAuthScreen(
                            effect.token, effect.from
                        )
                    )

                    else -> Unit
                }
            }
        }
        LaunchedEffect(settingViewModel) {
            settingViewModel.effect.collect { effect ->
                when (effect) {
                    SettingContract.Effect.NavigateToPaywall -> navigator.push(PaywallScreen())
                    SettingContract.Effect.NavigateToReview -> uriHandler.openUri("https://play.google.com/store/apps/details?id=com.soundsleeper.app")
                    SettingContract.Effect.NavigateToSubscriptionManage -> uriHandler.openUri("https://play.google.com/store/account/subscriptions?package=com.soundsleeper.app")
                    SettingContract.Effect.NavigateToStorePage -> uriHandler.openUri("https://play.google.com/store/apps/details?id=com.soundsleeper.app")
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SleepTheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            ) {

                when (homeState.selectedTab) {
                    HomeTab.REPORT -> {
                        ReportContent(
                            trackingState = trackingViewModel.state.collectAsState().value,
                            reportState = reportViewModel.state.collectAsState().value,
                            isUserPremium = authState.user?.isPremium == true,
                            onDateSelected = {
                                reportViewModel.sendIntent(ReportContract.Intent.SelectDate(it))
                            },
                            onToggleCalendarExpanded = {
                                reportViewModel.sendIntent(ReportContract.Intent.ToggleCalendar(it))
                            },
                            onPrevClicked = { unit ->
                                reportViewModel.sendIntent(ReportContract.Intent.PrevClicked(unit))
                            },
                            onNextClicked = { unit ->
                                reportViewModel.sendIntent(ReportContract.Intent.NextClicked(unit))
                            },
                            onReportDeleteClicked = { sessionId ->
                                reportViewModel.sendIntent(ReportContract.Intent.DeleteReport(sessionId))
                            },
                            onConfirmDelete = { sessionId ->
                                reportViewModel.sendIntent(ReportContract.Intent.ConfirmDelete(sessionId))
                            },
                            onDismissDeleteDialog = {
                                reportViewModel.sendIntent(ReportContract.Intent.DismissDeleteDialog)
                            },
                            onUpgradeClicked = { navigator.push(PaywallScreen()) },
                            // 조언은 세션이 정해진 뒤에 부른다. 프리미엄이 아니면
                            // ViewModel 쪽에서 호출 자체를 건너뛴다(LLM 비용).
                            onRequestAdvice = {
                                reportViewModel.loadSleepAdvice(
                                    isUserPremium = authState.user?.isPremium == true
                                )
                            }
                        )
                    }


                    HomeTab.MYPAGE -> SettingContent(
                        authViewModel = authViewModel,
                        settingViewModel = settingViewModel,
                        onSocialLogin = { authViewModel.sendIntent(AuthContract.Intent.SocialLoginClicked(it)) },
                        onNavigateToAccountSetting = { navigator.push(AccountSettingScreen) },
                        onNavigateToSleepSetting = { navigator.push(SleepSettingScreen) },
                        onNavigateToLicenseCredit = { navigator.push(LicenseCreditScreen) },
                        onChatClick = { chatViewModel.openChat() },
                        onNavigateToTerms = { navigator.push(TermsScreen) },
                        onNavigateToPrivacy = { navigator.push(PrivacyPolicyScreen) },
                        onNavigateToLanguage = { navigator.push(LanguageSettingScreen) },
                    )

                    else -> HomeContent(
                        alarmState = alarmViewModel.state.collectAsState().value,
                        musicState = musicState,
                        elapsedSleepMusicSeconds = musicViewModel.elapsedSleepMusicSeconds.collectAsState().value,
                        isUserPremium = authState.user?.isPremium == true,
                        onLockedTrackClicked = { navigator.push(PaywallScreen()) },
                        onStartTracking = { musicName -> beginTracking(musicName) },
                        onTogglePlaying = { musicViewModel.sendIntent(MusicContract.Intent.TogglePlaying) },
                        onMusicSelected = { music -> musicViewModel.sendIntent(MusicContract.Intent.MusicSelected(music)) },
                        onToggleFavorite = { music -> musicViewModel.sendIntent(MusicContract.Intent.ToggleFavorite(music)) },
                        onNavigateToSleepSetting = { navigator.push(SleepSettingScreen) },
                        onSetTimer = { minutes ->
                            musicViewModel.sendIntent(
                                if (minutes == null) MusicContract.Intent.SetAutoStopTimer
                                else MusicContract.Intent.SetTimer(minutes)
                            )
                        },
                    )
                }
            }
            CustomBottomTabBar(
                homeState = homeState,
                onBottomTabSelected = { tab ->
                    homeViewModel.sendIntent(HomeContract.Intent.SelectBottomTab(tab))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 시스템 권한 대화상자에서 전부 허용되면 원래 하려던 측정으로 이어 간다.
        // 사용자가 다시 "수면 시작"을 누르게 만들면 방금 한 동작이 헛수고처럼 느껴진다.
        LaunchedEffect(permissionState, hasPendingStart) {
            if (hasPendingStart && permissionState.isAllGranted()) {
                hasPendingStart = false
                requestPermissionsThenTrack(pendingMusicName)
            }
        }

        if (showPermissionExplain) {
            PermissionExplainDialog(
                onConfirm = {
                    permissionViewModel.markPermissionGuideShown()
                    showPermissionExplain = false
                    requestPermissionsThenTrack(pendingExplainMusicName)
                }
            )
        }
    }
}
@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalSettingsApi
@InternalVoyagerApi
data class PermissionGuideScreen(
    // 수면 시작 버튼을 눌러 진입한 경우, 권한 확인이 끝난 뒤 이어서 측정을 시작하기 위해 전달받는 값들.
    // 최초실행 온보딩처럼 단순 안내 목적으로 진입한 경우에는 기본값(null / false)을 사용합니다.
    val musicName: String? = null,
    val startTrackingOnComplete: Boolean = false
) : Screen {
    /**
     * 최초 측정 전 권한(마이크/알림/활동인식/배터리 최적화 제외)을 설명하는 안내 화면.
     * `beginTracking`에서 첫 측정이거나 권한이 비어 있을 때 이 화면으로 진입하며,
     * "계속하기"를 누르면 [startTrackingOnComplete]가 true인 경우 바로 측정 시작 Intent를 보낸다.
     */
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val permissionViewModel = koinScreenModel<PermissionViewModel>()
        val trackingViewModel = koinScreenModel<TrackingViewModel>()
        val trackingState by trackingViewModel.state.collectAsState()

        // 이 화면을 거쳐 측정을 시작하는 경로였다면, 측정이 실제로 시작된 뒤 측정 화면으로 교체한다.
        LaunchedEffect(startTrackingOnComplete) {
            if (startTrackingOnComplete) {
                trackingViewModel.effect.collect { effect ->
                    if (effect is TrackingContract.Effect.NavigateToTracking) {
                        navigator.replace(TrackingScreen(effect.durationMillis, effect.sessionId))
                    }
                }
            }
        }

        PermissionGuideContent(
            onContinue = {
                permissionViewModel.markOnboardingDone()
                // 이 안내를 봤다는 사실을 남겨 두 번째 측정부터는 건너뛴다.
                permissionViewModel.markTrackingGuideShown()
                if (startTrackingOnComplete) {
                    // beginTracking에서 하려던 측정 시작을 여기서 이어서 수행한다.
                    trackingViewModel.sendIntent(
                        TrackingContract.Intent.StartTracking(trackingState.durationMillis, musicName)
                    )
                } else {
                    navigator.pop()
                }
            },
            onUpdatePermission = { type, granted ->
                permissionViewModel.updatePermission(type, granted)
            },
            onBackClick = { navigator.pop() }
        )
    }
}

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalSettingsApi
@InternalVoyagerApi
data class TrackingScreen(
    val durationMillis: Long, val sessionId: String
) : Screen {
    /**
     * 측정 중(실시간 수면 분석) 화면. 수면 사이클의 핵심 화면으로,
     * [TrackingViewModel]의 상태를 구독해 실시간 수면 단계/경과 시간을 보여주고,
     * 트래킹이 끝나면(수동 종료 또는 알람) 아래 effect collector를 통해 기상/리포트/홈으로 이동한다.
     */
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val trackingViewModel = koinScreenModel<TrackingViewModel>()
        val trackingState by trackingViewModel.state.collectAsState()
        val alarmViewModel = koinScreenModel<AlarmViewModel>()
        val musicViewModel = koinScreenModel<MusicViewModel>()
        val authViewModel = koinScreenModel<AuthViewModel>()
        val authState by authViewModel.state.collectAsState()

        val coroutineScope = rememberCoroutineScope()
        val pagerState = rememberPagerState(pageCount = { 2 })

        // 측정 종료 후 어디로 이동할지는 TrackingViewModel이 결정해 effect로 흘려보낸다.
        LaunchedEffect(Unit) {
            trackingViewModel.effect.collect { effect ->
                when (effect) {
                    is TrackingContract.Effect.NavigateToWakeUp -> {
                        // 알람 시각이 되어 자동으로 깨운 경우 — 기상 화면으로 이동.
                        navigator.push(WakeUpScreen)
                    }
                    is TrackingContract.Effect.NavigateToReport -> {
                        // 측정이 정상적으로 끝나 분석 결과가 저장된 경우 —
                        // 홈 화면을 리포트 탭으로 바꿔 끼워 넣어(스택 교체) 결과를 보여준다.
                        navigator.replaceAll(
                            HomeScreen(
                                initialTab = HomeTab.REPORT,
                                sessionId = effect.sessionId
                            )
                        )
                    }
                    is TrackingContract.Effect.NavigateToHome -> {
                        navigator.replaceAll(HomeScreen()) // 기본 홈으로 복귀
                    }
                    is TrackingContract.Effect.NavigateToPermissionGuide -> navigator.push(
                        PermissionGuideScreen(
                            musicName = trackingState.musicName,
                            startTrackingOnComplete = true
                        )
                    )
                    else -> {}
                }
            }
        }
        // 예전에는 여기서도 isAlarmTriggered 를 보고 WakeUpScreen 을 한 번 더 push 했다.
        // isAlarmTriggered 는 측정 중 해제되지 않아서, 이 화면이 다시 구성될 때마다 같은
        // 화면이 스택에 중복으로 쌓였다. 진입 경로는 위의 NavigateToWakeUp 하나로 둔다.
        TrackingContent(
            trackingState = trackingState,
            musicState = musicViewModel.state.collectAsState().value,
            pagerState = pagerState,
            onChangePage = {
                coroutineScope.launch {
                    pagerState.scrollToPage(0)
                }
            },
            elapsedSleepMusicSeconds = musicViewModel.elapsedSleepMusicSeconds.collectAsState().value,
            elapsedSleepTimeMillis = trackingViewModel.elapsedSleepTimeMillis.collectAsState().value,
            onFinishTracking = { trackingViewModel.sendIntent(TrackingContract.Intent.FinishTracking) },
            onDiscardTracking = { trackingViewModel.sendIntent(TrackingContract.Intent.DiscardTracking) },
            onTogglePlaying = { musicViewModel.sendIntent(MusicContract.Intent.TogglePlaying) },
            onMusicSelected = { music -> musicViewModel.sendIntent(MusicContract.Intent.MusicSelected(music)) },
            onToggleFavorite = { music -> musicViewModel.sendIntent(MusicContract.Intent.ToggleFavorite(music)) },
            isUserPremium = authState.user?.isPremium == true,
            onLockedTrackClicked = { navigator.push(PaywallScreen()) },
            onChangeAlarmHour = { hour -> alarmViewModel.sendIntent(AlarmContract.Intent.ChangeAlarmHour(hour)) },
            onChangeAlarmMinute = { minute, index ->
                alarmViewModel.sendIntent(AlarmContract.Intent.ChangeAlarmMinute(minute, index))
            },
        )
    }
}

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalSettingsApi
@InternalVoyagerApi
object WakeUpScreen : Screen {
    /**
     * 알람이 울려 사용자를 깨우는 화면. "알람 끄기"를 누르면 [AlarmViewModel]이
     * 측정을 마무리하고 그 결과(리포트로 갈지 홈으로 갈지)를 effect로 알려준다.
     */
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val alarmViewModel = koinScreenModel<AlarmViewModel>()

        LaunchedEffect(Unit) {
            alarmViewModel.effect.collect { effect ->
                when (effect) {
                    is AlarmContract.Effect.NavigateToReport -> {
                        navigator.replaceAll(
                            HomeScreen(
                                initialTab = HomeTab.REPORT,
                                sessionId = effect.sessionId
                            )
                        )
                    }
                    // 측정이 30분 미만이면 리포트 대신 홈으로 간다. 이 분기가 없어서
                    // 짧게 잰 날에는 일어나기를 눌러도 기상 화면에 그대로 멈춰 있었다.
                    is AlarmContract.Effect.NavigateToHome -> {
                        navigator.replaceAll(HomeScreen())
                    }
                }
            }
        }
        WakeUpContent(
            onStopAlarm = { alarmViewModel.sendIntent(AlarmContract.Intent.StopAlarm) },
        )
    }
}
object SleepSettingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val alarmViewModel = koinScreenModel<AlarmViewModel>()
        LaunchedEffect(Unit) {
            alarmViewModel.effect.collect { effect ->
                when (effect) {
                    is AlarmContract.Effect.NavigateToHome -> navigator.pop()
                    else -> {}
                }
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                alarmViewModel.sendIntent(AlarmContract.Intent.StopAlarmPreview)
            }
        }

        SleepSettingContent(
            alarmState = alarmViewModel.state.collectAsState().value,
            onChangeAlarmHour = { alarmViewModel.sendIntent(AlarmContract.Intent.ChangeAlarmHour(it)) },
            onChangeAlarmMinute = { minute, index ->
                alarmViewModel.sendIntent(AlarmContract.Intent.ChangeAlarmMinute(minute, index))
            },
            onChangeReminderHour = { alarmViewModel.sendIntent(AlarmContract.Intent.ChangeReminderHour(it)) },
            onChangeReminderMinute = { minute, index ->
                alarmViewModel.sendIntent(AlarmContract.Intent.ChangeReminderMinute(minute, index))
            },
            onToggleAlarm = { alarmViewModel.sendIntent(AlarmContract.Intent.ToggleAlarm) },
            onSelectAlarmSound = {
                alarmViewModel.sendIntent(
                    AlarmContract.Intent.SelectAlarmSound(it)
                )
            },
            onChangeVolume = { alarmViewModel.sendIntent(AlarmContract.Intent.ChangeVolume(it)) },
            onToggleVibration = { alarmViewModel.sendIntent(AlarmContract.Intent.ToggleVibration) },
            onToggleSmartAlarm = { alarmViewModel.sendIntent(AlarmContract.Intent.ToggleSmartAlarm) },
            onSelectSmartAlarmRange = {
                alarmViewModel.sendIntent(
                    AlarmContract.Intent.SelectSmartAlarmRange(it)
                )
            },
            onToggleSleepReminder = { alarmViewModel.sendIntent(AlarmContract.Intent.ToggleSleepReminder(it)) },
            onStopAlarmPreview = { alarmViewModel.sendIntent(AlarmContract.Intent.StopAlarmPreview) },
            onBackClick = { navigator.pop() },
        )
    }
}
@ExperimentalTime
@ExperimentalMaterial3Api
@ExperimentalCoroutinesApi
@ExperimentalSettingsApi
@InternalVoyagerApi
object AccountSettingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        val authState by authViewModel.state.collectAsState()
        LaunchedEffect(Unit) {
            authViewModel.effect.collect { effect ->
                when (effect) {
                    is AuthContract.Effect.NavigateToHome -> navigator.push(HomeScreen(initialTab = HomeTab.MYPAGE))
                    // 예전에는 이 분기가 없어서 "연결하기"를 눌러도 아무 일도 일어나지 않았다.
                    // (HomeScreen 의 수집기가 아래에서 같이 반응해 경쟁하기도 했다.)
                    is AuthContract.Effect.NavigateToEmailAuth ->
                        navigator.push(EmailAuthScreen(effect.token, effect.from))
                    else -> Unit
                }
            }
        }
        // 연결된 로그인 수단은 로컬에 캐시하지 않는다. 들어올 때마다 서버에 물어야
        // 다른 기기에서 바뀐 연결 상태도 반영되고, 해제 가드와 판단이 어긋나지 않는다.
        LaunchedEffect(Unit) {
            authViewModel.sendIntent(AuthContract.Intent.AccountScreenShown)
        }
        AccountSettingContent(
            state = authState,
            onEditProfileClick = { navigator.push(ProfileEditScreen) },
            onLogoutClick = { authViewModel.sendIntent(AuthContract.Intent.LogoutClicked) },
            onWithdrawClick = { navigator.push(WithdrawalReasonScreen) },
            onSocialConnect = { authViewModel.sendIntent(AuthContract.Intent.SocialConnectClicked(it)) },
            onEmailConnect = { authViewModel.sendIntent(AuthContract.Intent.EmailConnectClicked) },
            onSocialDisConnect = { authViewModel.sendIntent(AuthContract.Intent.SocialDisConnectClicked(it)) },
            onEmailDisConnect = { authViewModel.sendIntent(AuthContract.Intent.EmailDisconnectClicked) },
            onLastAuthMethodBlockDismiss = {
                authViewModel.sendIntent(AuthContract.Intent.LastAuthMethodBlockDismissed)
            },
            onEmailConnectedMessageShown = {
                authViewModel.sendIntent(AuthContract.Intent.EmailConnectedMessageShown)
            },
            onMessageShown = { authViewModel.sendIntent(AuthContract.Intent.MessageShown) },
        )
    }
}

/** 프로필 수정 화면 */
@ExperimentalTime
@ExperimentalMaterial3Api
@ExperimentalCoroutinesApi
@ExperimentalSettingsApi
object ProfileEditScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        val authState by authViewModel.state.collectAsState()

        LaunchedEffect(Unit) {
            authViewModel.effect.collect { effect ->
                if (effect is AuthContract.Effect.ProfileSaved) navigator.pop()
            }
        }

        ProfileEditContent(
            user = authState.user,
            isImageUploading = authState.isProfileImageUploading,
            message = authState.message,
            onMessageShown = { authViewModel.sendIntent(AuthContract.Intent.MessageShown) },
            onBackClick = { navigator.pop() },
            onUpdateNickName = { authViewModel.sendIntent(AuthContract.Intent.UpdateNickname(it)) },
            onSaveClick = { nickname ->
                authViewModel.sendIntent(AuthContract.Intent.SaveProfile(nickname))
            },
        )
    }
}

/** 회원탈퇴 단계 1: 이유 선택 */
@ExperimentalTime
@ExperimentalMaterial3Api
@ExperimentalCoroutinesApi
@ExperimentalSettingsApi
@InternalVoyagerApi
object WithdrawalReasonScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        val authState = authViewModel.state.collectAsState().value

        // 대안을 고르면 탈퇴 흐름에서 빠져나간다. 홈으로 돌아가는 경우는 흐름을 통째로
        // 벗어나는 것이므로 스택을 교체하고, 안내/약관처럼 읽고 돌아올 화면은 push 한다.
        // (1:1 문의는 채널톡 메신저를 띄우므로 네비게이션이 없다.)
        LaunchedEffect(Unit) {
            authViewModel.effect.collect { effect ->
                when (effect) {
                    is AuthContract.Effect.NavigateToHome ->
                        navigator.replaceAll(HomeScreen(initialTab = HomeTab.MYPAGE))

                    is AuthContract.Effect.NavigateToSleepGuide ->
                        navigator.push(PermissionGuideScreen())

                    is AuthContract.Effect.NavigateToPrivacyPolicy ->
                        navigator.push(PrivacyPolicyScreen)

                    else -> Unit
                }
            }
        }

        WithdrawalReasonContent(
            authState = authState,
            onReasonSelected = { reason ->
                authViewModel.sendIntent(AuthContract.Intent.SelectWithdrawReason(reason))
                navigator.push(WithdrawalConfirmDetailScreen)
            },
            onAlternativeSelected = { alternative ->
                authViewModel.sendIntent(
                    when (alternative) {
                        WithdrawAlternative.TIP -> AuthContract.Intent.AlternativeTipClicked
                        WithdrawAlternative.GUIDE -> AuthContract.Intent.AlternativeGuideClicked
                        WithdrawAlternative.SUPPORT -> AuthContract.Intent.AlternativeSupportClicked
                        WithdrawAlternative.PRIVACY -> AuthContract.Intent.AlternativePrivacyClicked
                    }
                )
            },
            onBackClick = { navigator.pop() }
        )
    }
}

/** 회원탈퇴 단계 2: 안내 및 최종 확인 */
@ExperimentalTime
@ExperimentalMaterial3Api
@ExperimentalCoroutinesApi
@ExperimentalSettingsApi
@InternalVoyagerApi
object WithdrawalConfirmDetailScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        val authState by authViewModel.state.collectAsState()

        LaunchedEffect(authState.withdrawStep) {
            if (authState.withdrawStep == WithdrawStep.DONE) {
                navigator.push(WithdrawCompleteScreen)
            }
        }

        WithdrawalConfirmDetailContent(
            onWithdrawClick = {
                authViewModel.sendIntent(AuthContract.Intent.WithdrawConfirmed)
            },
            onBackClick = { navigator.pop() },
        )
    }
}
@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class, ExperimentalSettingsApi::class,
    InternalVoyagerApi::class
)
data class PaywallScreen(val fromSignUp: Boolean = false) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val paywallViewModel = koinScreenModel<PaywallViewModel>()
        val state by paywallViewModel.state.collectAsState()

        val dismiss: () -> Unit = {
            if (fromSignUp) navigator.replaceAll(HomeScreen()) else navigator.pop()
        }

        LaunchedEffect(Unit) {
            paywallViewModel.sendIntent(PaywallContract.Intent.LoadPaywall)
        }
        LaunchedEffect(Unit) {
            paywallViewModel.effect.collect { effect ->
                when (effect) {
                    is PaywallContract.Effect.NavigateBack -> dismiss()
                    is PaywallContract.Effect.RequireLogin -> navigator.push(HomeScreen(initialTab = HomeTab.MYPAGE))
                    is PaywallContract.Effect.ShowToast -> Unit
                }
            }
        }
        PaywallContent(
            state = state,
            onSelectPlan = { paywallViewModel.sendIntent(PaywallContract.Intent.SelectPlan(it)) },
            onSubscribeClicked = { paywallViewModel.sendIntent(PaywallContract.Intent.SubscribeClicked) },
            onDismissError = { paywallViewModel.sendIntent(PaywallContract.Intent.DismissError) },
            onNavigateToTerms = { navigator.push(TermsScreen) },
            onNavigateToPrivacy = { navigator.push(PrivacyPolicyScreen) },
            onBackClick = dismiss,
        )
    }
}
object LicenseCreditScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        LicenseCreditContent(onBackClick = { navigator.pop() })
    }
}
@ExperimentalTime
object LanguageSettingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val appearanceViewModel = koinScreenModel<AppearanceViewModel>()
        val language by appearanceViewModel.language.collectAsState()
        LanguageSettingContent(
            currentLanguage = language,
            onSelectLanguage = { appearanceViewModel.setLanguage(it) },
            onBackClick = { navigator.pop() }
        )
    }
}
object TermsScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinScreenModel<LegalViewModel> { parametersOf(LegalType.TERMS) }
        val state by viewModel.state.collectAsState()

        LegalContent(
            type = LegalType.TERMS,
            state = state,
            onRetry = viewModel::load,
            onBackClick = { navigator.pop() },
        )
    }
}

object PrivacyPolicyScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinScreenModel<LegalViewModel> { parametersOf(LegalType.PRIVACY) }
        val state by viewModel.state.collectAsState()

        LegalContent(
            type = LegalType.PRIVACY,
            state = state,
            onRetry = viewModel::load,
            onBackClick = { navigator.pop() },
        )
    }
}
