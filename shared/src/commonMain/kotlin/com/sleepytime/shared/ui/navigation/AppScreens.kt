package com.sleepytime.shared.ui.navigation
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import com.sleepytime.shared.ui.tracking.WakeUpContent
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.russhwolf.settings.ExperimentalSettingsApi
import com.sleepytime.shared.platform.EmailLauncher
import com.sleepytime.shared.ui.setting.AccountSettingContent
import com.sleepytime.shared.ui.setting.SettingContent
import com.sleepytime.shared.ui.alarm.AlarmContract
import com.sleepytime.shared.ui.alarm.AlarmViewModel
import com.sleepytime.shared.ui.auth.AuthContract
import com.sleepytime.shared.ui.auth.AuthViewModel
import com.sleepytime.shared.ui.auth.EmailAuthContent
import com.sleepytime.shared.ui.auth.LoginBenefitContent
import com.sleepytime.shared.ui.auth.WithdrawCompleteContent
import com.sleepytime.shared.ui.auth.WithdrawLoadingContent
import com.sleepytime.shared.ui.auth.WithdrawStep
import com.sleepytime.shared.ui.home.HomeContent
import com.sleepytime.shared.ui.home.HomeContract
import com.sleepytime.shared.ui.home.HomeViewModel
import com.sleepytime.shared.ui.music.MusicContract
import com.sleepytime.shared.ui.music.MusicViewModel
import com.sleepytime.shared.ui.onboarding.OnboardingContent
import com.sleepytime.shared.ui.report.ReportContent
import com.sleepytime.shared.ui.report.ReportContract
import com.sleepytime.shared.ui.report.ReportViewModel
import com.sleepytime.shared.ui.setting.AppInfoContent
import com.sleepytime.shared.ui.alarm.SleepSettingContent
import com.sleepytime.shared.ui.home.CustomBottomTabBar
import com.sleepytime.shared.ui.setting.ProfileEditContent
import com.sleepytime.shared.ui.setting.WithdrawalReasonContent
import com.sleepytime.shared.ui.setting.WithdrawalConfirmDetailContent
import com.sleepytime.shared.ui.setting.SupportContent
import com.sleepytime.shared.ui.setting.VersionHistoryContent
import com.sleepytime.shared.ui.setting.ChatViewModel
import com.sleepytime.shared.ui.component.FaqData
import com.sleepytime.shared.ui.theme.SleepTheme
import com.sleepytime.shared.ui.tracking.TrackingContent
import com.sleepytime.shared.ui.tracking.TrackingContract
import com.sleepytime.shared.ui.tracking.TrackingViewModel
import com.sleepytime.shared.platform.rememberProfileImageLauncher
import com.sleepytime.shared.ui.PermissionGuideContent
import com.sleepytime.shared.ui.onboarding.PermissionViewModel
import com.sleepytime.shared.ui.setting.LicenseCreditContent
import com.sleepytime.shared.ui.setting.PrivacyPolicyContent
import com.sleepytime.shared.ui.setting.TermsContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
        val authViewModel = koinScreenModel<AuthViewModel>()
        OnboardingContent(
            onGuestLogin = { authViewModel.sendIntent(AuthContract.Intent.GuestLoginClicked) },
            onSocialLogin = { authViewModel.sendIntent(AuthContract.Intent.SocialLoginClicked(it)) },
            onEmailLogin = { authViewModel.sendIntent(AuthContract.Intent.EmailLoginClicked) }
        )
        LaunchedEffect(Unit) {
            authViewModel.effect.collect { effect ->
                when (effect) {
                    is AuthContract.Effect.NavigateToHome -> navigator.replaceAll(HomeScreen())

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
        LaunchedEffect(Unit) {
            authViewModel.navigateToHomeEffect.collect {
                navigator.replaceAll(HomeScreen())
            }
        }
        EmailAuthContent(
            authState = authState,
            onEmailChanged = { authViewModel.updateEmail(it) },
            onSendAuthCode = { authViewModel.sendIntent(AuthContract.Intent.SendAuthCodeClicked(it)) },
            emailLauncher = object : EmailLauncher {
                override fun openEmailApp(email: String) {}
            })
    }
}

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalSettingsApi
@InternalVoyagerApi
object LoginBenefitScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        LaunchedEffect(Unit) {
            authViewModel.effect.collect { effect ->
                when (effect) {
                    is AuthContract.Effect.NavigateToHome -> navigator.replaceAll(HomeScreen())

                    is AuthContract.Effect.NavigateToEmailAuth -> navigator.push(
                        EmailAuthScreen(
                            effect.token, effect.from
                        )
                    )

                    else -> Unit
                }
            }
        }
        LoginBenefitContent(
            onSocialLogin = { authViewModel.sendIntent(AuthContract.Intent.SocialLoginClicked(it)) },
            onEmailLogin = { authViewModel.sendIntent(AuthContract.Intent.EmailLoginClicked) },
            onLater = { navigator.pop() })
    }
}
@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalSettingsApi
@InternalVoyagerApi
object WithdrawLoadingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(Unit) {
            delay(1500.milliseconds)
            navigator.replace(WithdrawCompleteScreen)
        }
        WithdrawLoadingContent()
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
    val initialTab: String? = null,
    val sessionId: String? = null
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        val homeViewModel = koinScreenModel<HomeViewModel>()
        val alarmViewModel = koinScreenModel<AlarmViewModel>()
        val musicViewModel = koinScreenModel<MusicViewModel>()
        val trackingViewModel = koinScreenModel<TrackingViewModel>()
        val trackingState by trackingViewModel.state.collectAsState()
        val reportViewModel = koinScreenModel<ReportViewModel>()
        val homeState by homeViewModel.state.collectAsState()
        val musicState by musicViewModel.state.collectAsState()
        LaunchedEffect(initialTab, sessionId) {
            if (initialTab != null) {
                homeViewModel.sendIntent(HomeContract.Intent.SelectBottomTab(initialTab))
            }
        }
        LaunchedEffect(homeState.selectedTab) {
            if (homeState.selectedTab == "리포트") {
                reportViewModel.refreshToLatestSession()
            }
        }
        LaunchedEffect(Unit) {
            homeViewModel.effect.collect { effect ->
                when (effect) {
                    is HomeContract.Effect.NavigateToSleepSetting -> navigator.push(
                        SleepSettingScreen
                    )
                    is HomeContract.Effect.NavigateToReport -> {
                        homeViewModel.sendIntent(HomeContract.Intent.SelectBottomTab("리포트"))
                    }
                    is HomeContract.Effect.NavigateToPermissionGuide -> navigator.push(
                        PermissionGuideScreen())
                    else -> {}
                }
            }
        }
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
                            musicTitle = trackingState.musicTitle,
                            startTrackingOnComplete = true
                        )
                    )
                    else -> {}
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SleepTheme.gradients.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            ) {
                when (homeState.selectedTab) {
                    "리포트" -> {
                        ReportContent(
                            trackingState = trackingViewModel.state.collectAsState().value,
                            reportState = reportViewModel.state.collectAsState().value,
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
                            }
                        )
                    }

                    "마이페이지" -> SettingContent(
                        reportState = reportViewModel.state.collectAsState().value,
                        authViewModel = authViewModel,
                        onNavigateToLoginBenefit = { navigator.push(LoginBenefitScreen) },
                        onNavigateToAccountSetting = { navigator.push(AccountSettingScreen) },
                        onNavigateToNotification = { /*navigator.push(NotificationSettingScreen)*/ },
                        onNavigateToSleepSetting = { navigator.push(SleepSettingScreen) },
                        onNavigateToAppInfo = { navigator.push(AppInfoScreen) },
                        onNavigateToSupport = { navigator.push(SupportScreen) },
                        onDateSelected = {
                            reportViewModel.sendIntent(ReportContract.Intent.SelectDate(it))
                        },
                        onPrevClicked = { unit ->
                            reportViewModel.sendIntent(ReportContract.Intent.PrevClicked(unit))
                        },
                        onNextClicked = { unit ->
                            reportViewModel.sendIntent(ReportContract.Intent.NextClicked(unit))
                        },
                    )

                    else -> HomeContent(
                        alarmState = alarmViewModel.state.collectAsState().value,
                        musicState = musicState,
                        trackingState = trackingState,
                        reportState = reportViewModel.state.collectAsState().value,
                        elapsedSleepMusicSeconds = musicViewModel.elapsedSleepMusicSeconds.collectAsState().value,
                        onStartTracking = { musicTitle ->
                            // 💡 수면 시작 버튼을 누르면 항상 PermissionGuideScreen을 거칩니다.
                            // 필요한 권한이 없으면 권한 설정 UI + 안내 가이드를, 모두 허용된 상태라면
                            // 안내 가이드만 보여준 뒤 이어서 측정을 시작합니다.
                            navigator.push(
                                PermissionGuideScreen(
                                    musicTitle = musicTitle,
                                    startTrackingOnComplete = true
                                )
                            )
                        },
                        onTogglePlaying = { musicViewModel.sendIntent(MusicContract.Intent.TogglePlaying) },
                        onMusicSelected = { music -> musicViewModel.sendIntent(MusicContract.Intent.MusicSelected(music)) },
                        onToggleFavorite = { music -> musicViewModel.sendIntent(MusicContract.Intent.ToggleFavorite(music)) },
                        onNavigateToSleepSetting = { navigator.push(SleepSettingScreen) },
                        onSetTimer = { minutes -> musicViewModel.sendIntent(MusicContract.Intent.SetTimer(minutes)) },
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
    val musicTitle: String? = null,
    val startTrackingOnComplete: Boolean = false
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val permissionViewModel = koinScreenModel<PermissionViewModel>()
        val trackingViewModel = koinScreenModel<TrackingViewModel>()
        val permissionState by permissionViewModel.state.collectAsState()
        val trackingState by trackingViewModel.state.collectAsState()

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
            permissionState = permissionState,
            onContinue = {
                permissionViewModel.markOnboardingDone()
                if (startTrackingOnComplete) {
                    trackingViewModel.sendIntent(
                        TrackingContract.Intent.StartTracking(trackingState.durationMillis, musicTitle)
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
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val trackingViewModel = koinScreenModel<TrackingViewModel>()
        val trackingState by trackingViewModel.state.collectAsState()
        val alarmViewModel = koinScreenModel<AlarmViewModel>()
        val musicViewModel = koinScreenModel<MusicViewModel>()

        LaunchedEffect(Unit) {
            trackingViewModel.effect.collect { effect ->
                when (effect) {
                    is TrackingContract.Effect.NavigateToWakeUp -> {
                        navigator.push(WakeUpScreen)
                    }
                    is TrackingContract.Effect.NavigateToReport -> {
                        navigator.replaceAll(
                            HomeScreen(
                                initialTab = "리포트",
                                sessionId = effect.sessionId
                            )
                        )
                    }
                    is TrackingContract.Effect.NavigateToHome -> {
                        navigator.replaceAll(HomeScreen()) // 기본 홈으로 복귀
                    }
                    is TrackingContract.Effect.NavigateToPermissionGuide -> navigator.push(
                        PermissionGuideScreen(
                            musicTitle = trackingState.musicTitle,
                            startTrackingOnComplete = true
                        )
                    )
                    else -> {}
                }
            }
        }
        LaunchedEffect(trackingState.isAlarmTriggered) {
            if (trackingState.isAlarmTriggered) navigator.push(WakeUpScreen)
        }
        TrackingContent(
            trackingState = trackingState,
            musicState = musicViewModel.state.collectAsState().value,
            elapsedSleepMusicSeconds = musicViewModel.elapsedSleepMusicSeconds.collectAsState().value,
            elapsedSleepTimeMillis = trackingViewModel.elapsedSleepTimeMillis.collectAsState().value,
            onFinishTracking = { trackingViewModel.sendIntent(TrackingContract.Intent.FinishTracking) },
            onDiscardTracking = { trackingViewModel.sendIntent(TrackingContract.Intent.DiscardTracking) },
            onTogglePlaying = { musicViewModel.sendIntent(MusicContract.Intent.TogglePlaying) },
            onMusicSelected = { music -> musicViewModel.sendIntent(MusicContract.Intent.MusicSelected(music)) },
            onSetTimer = { min -> musicViewModel.sendIntent(MusicContract.Intent.SetTimer(min)) },
            onToggleFavorite = { music -> musicViewModel.sendIntent(MusicContract.Intent.ToggleFavorite(music)) },
            onChangeAlarmHour = { hour -> alarmViewModel.sendIntent(AlarmContract.Intent.ChangeAlarmHour(hour)) },
            onChangeAlarmMinute = { minute, index ->
                alarmViewModel.sendIntent(AlarmContract.Intent.ChangeAlarmMinute(minute, index))
            },
            onToggleRecommend = { alarmViewModel.sendIntent(AlarmContract.Intent.ToggleRecommend) },
        )
    }
}

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalSettingsApi
@InternalVoyagerApi
object WakeUpScreen : Screen {
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
                                initialTab = "리포트",
                                sessionId = effect.sessionId
                            )
                        )
                    }
                    else -> {}
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
                alarmViewModel.sendIntent(AlarmContract.Intent.ChangeAlarmMinute(minute, index)) },
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
            onToggleRecommend = { alarmViewModel.sendIntent(AlarmContract.Intent.ToggleRecommend) },
            onToggleSleepReminder = { alarmViewModel.sendIntent(AlarmContract.Intent.ToggleSleepReminder(it)) },
            onChangeReminderTime = { hour, minute ->
                alarmViewModel.sendIntent(AlarmContract.Intent.ChangeReminderTime(hour, minute)) },
        )
    }
}

@ExperimentalMaterial3Api
object SupportScreen : Screen {
    @Composable
    override fun Content() {
        val chatViewModel = koinScreenModel<ChatViewModel>()

        SupportContent(
            allItems = FaqData.items,
            onChatClick = { chatViewModel.openChat() },
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

        AccountSettingContent(
            state = authState,
            onEditProfileClick = { navigator.push(ProfileEditScreen) },
            onLogoutClick = { authViewModel.sendIntent(AuthContract.Intent.LogoutClicked) },
            onWithdrawClick = { navigator.push(WithdrawalReasonScreen) },
            onSocialConnect = { authViewModel.sendIntent(AuthContract.Intent.SocialConnectClicked(it)) },
            onEmailConnect = { authViewModel.sendIntent(AuthContract.Intent.EmailConnectClicked) },
            onSocialDisConnect = { authViewModel.sendIntent(AuthContract.Intent.SocialDisConnectClicked(it)) },
            onEmailDisConnect = { authViewModel.sendIntent(AuthContract.Intent.EmailDisconnectClicked) }
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

        val imageLauncher = rememberProfileImageLauncher { bytes ->
            authViewModel.sendIntent(AuthContract.Intent.UpdateProfileImage(bytes))
        }

        ProfileEditContent(
            user = authState.user,
            onBackClick = { navigator.pop() },
            onUpdateNickName = { authViewModel.sendIntent(AuthContract.Intent.UpdateNickname(it)) },
            onUpdateEmail = { authViewModel.sendIntent(AuthContract.Intent.UpdateEmail(it)) },
            onSaveClick = { nickname, email ->
                authViewModel.sendIntent(AuthContract.Intent.SaveProfile(nickname, email))
                navigator.pop()
            },
            onImageChangeClick = { option ->
                when (option) {
                    0 -> authViewModel.sendIntent(AuthContract.Intent.ResetProfileImage)
                    1 -> imageLauncher.launchAlbum()
                    2 -> imageLauncher.launchCamera()
                }
            }
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

        WithdrawalReasonContent(
            onReasonSelected = { reason ->
                authViewModel.sendIntent(AuthContract.Intent.SelectWithdrawReason(reason))
                navigator.push(WithdrawalConfirmDetailScreen)
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
            if (authState.withdrawStep == WithdrawStep.LOADING) {
                navigator.push(WithdrawLoadingScreen)
            }
        }

        WithdrawalConfirmDetailContent(
            onWithdrawClick = {
                authViewModel.sendIntent(AuthContract.Intent.WithdrawConfirmed)
            },
            onBackClick = { navigator.pop() }
        )
    }
}

//object NotificationSettingScreen : Screen {
//    @Composable
//    override fun Content() {
//        val authViewModel = koinScreenModel<AuthViewModel>()
//        val authState by authViewModel.state.collectAsState()
//
//        NotificationSettingContent(
//            authState = authState,
//            onIntent = { authViewModel.sendIntent(it) }
//        )
//    }
//}

@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class, ExperimentalSettingsApi::class)
object AppInfoScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        AppInfoContent(
            onNavigateToVersionHistory = { navigator.push(VersionHistoryScreen) },
            onNavigateToLicenseCredit = { navigator.push(LicenseCreditScreen) },
            onNavigateToTerms = { navigator.push(TermsScreen) },
            onNavigateToPrivacy = { navigator.push(PrivacyPolicyScreen) },
            onManageSubscription = {},
            onRestorePurchase = {},
        )
    }
}

/** 버전 히스토리 / 업데이트 정보 화면 */
object VersionHistoryScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        VersionHistoryContent(
            onBackClick = { navigator.pop() }
        )
    }
}

object LicenseCreditScreen : Screen {
    @Composable
    override fun Content() {
        LicenseCreditContent()
    }
}

// 💡 약관/개인정보처리방침 네이티브 화면 전환: 기존에는 외부 URL을 브라우저로 열었지만,
// 저장소에 있던 실제 문서 내용을 그대로 옮겨 앱 내에서 바로 볼 수 있도록 연결합니다.
object TermsScreen : Screen {
    @Composable
    override fun Content() {
        TermsContent()
    }
}

object PrivacyPolicyScreen : Screen {
    @Composable
    override fun Content() {
        PrivacyPolicyContent()
    }
}
