package com.sleepytime.shared.ui.navigation// navigation/AppScreens.kt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.platform.EmailLauncher
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.ui.setting.AccountSettingContent
import com.sleepytime.shared.ui.setting.SettingContent
import com.sleepytime.shared.ui.environment.SleepEnvironmentContent
import com.sleepytime.shared.ui.music.SleepMusicSelectionContent
import com.sleepytime.shared.ui.alarm.AlarmContract
import com.sleepytime.shared.ui.alarm.AlarmViewModel
import com.sleepytime.shared.ui.auth.AuthContract
import com.sleepytime.shared.ui.auth.AuthViewModel
import com.sleepytime.shared.ui.auth.DisconnectConfirmContent
import com.sleepytime.shared.ui.auth.EmailAuthContent
import com.sleepytime.shared.ui.auth.EmailManageContent
import com.sleepytime.shared.ui.auth.LoginBenefitContent
import com.sleepytime.shared.ui.auth.SocialAccountDetailContent
import com.sleepytime.shared.ui.auth.WithdrawCompleteContent
import com.sleepytime.shared.ui.auth.WithdrawConfirmContent
import com.sleepytime.shared.ui.auth.WithdrawLoadingContent
import com.sleepytime.shared.ui.auth.WithdrawStep
import com.sleepytime.shared.ui.home.CustomBottomTabBar
import com.sleepytime.shared.ui.home.HomeContent
import com.sleepytime.shared.ui.home.HomeContract
import com.sleepytime.shared.ui.home.HomeViewModel
import com.sleepytime.shared.ui.music.MusicContract
import com.sleepytime.shared.ui.music.MusicViewModel
import com.sleepytime.shared.ui.onboarding.OnboardingContent
import com.sleepytime.shared.ui.onboarding.OnboardingContract
import com.sleepytime.shared.ui.onboarding.OnboardingViewModel
import com.sleepytime.shared.ui.report.ReportContent
import com.sleepytime.shared.ui.report.ReportContract
import com.sleepytime.shared.ui.report.ReportViewModel
import com.sleepytime.shared.ui.setting.AppInfoContent
import com.sleepytime.shared.ui.setting.DataSettingContent
import com.sleepytime.shared.ui.setting.NotificationSettingContent
import com.sleepytime.shared.ui.alarm.SleepSettingContent
import com.sleepytime.shared.ui.setting.SupportContent
import com.sleepytime.shared.ui.tracking.TrackingContent
import com.sleepytime.shared.ui.tracking.TrackingContract
import com.sleepytime.shared.ui.tracking.TrackingViewModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
object OnboardingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        val onboardingViewModel = koinScreenModel<OnboardingViewModel>()
        OnboardingContent(
            onboardingState = onboardingViewModel.state.collectAsState().value,
            permissionState = onboardingViewModel.permissionState.collectAsState().value,
            onNextStepClicked = { onboardingViewModel.sendIntent(OnboardingContract.Intent.NextStep) },
            onUpdatePermission = { k, v -> onboardingViewModel.updatePermission(k, v) },
            onGuestLogin = { authViewModel.sendIntent(AuthContract.Intent.GuestLoginClicked) },
            onSocialLogin = { authViewModel.sendIntent(AuthContract.Intent.SocialLoginClicked(it)) },
            onEmailLogin = { authViewModel.sendIntent(AuthContract.Intent.EmailLoginClicked) })
        LaunchedEffect(Unit) {
            authViewModel.effect.collect { effect ->
                when (effect) {
                    is AuthContract.Effect.NavigateToHome -> navigator.replaceAll(HomeScreen)

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
                navigator.replaceAll(HomeScreen)
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
object LoginBenefitScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        LaunchedEffect(Unit) {
            authViewModel.effect.collect { effect ->
                when (effect) {
                    is AuthContract.Effect.NavigateToHome -> navigator.replaceAll(HomeScreen)

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

@OptIn(ExperimentalMaterial3Api::class)
data class SocialAccountDetailScreen(val provider: AuthProvider) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        val authState by authViewModel.state.collectAsState()
        SocialAccountDetailContent(
            provider = provider,
            isPrimary = provider == authState.primaryProvider,
            isConnected = authState.connectedProviders.contains(provider),
            onBack = { navigator.pop() },
            onChangePrimary = {
                authViewModel.sendIntent(
                    AuthContract.Intent.ChangePrimaryProvider(
                        provider
                    )
                )
            },
            onDisconnect = { navigator.push(DisconnectConfirmScreen(provider)) })
    }
}

data class DisconnectConfirmScreen(val provider: AuthProvider) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        DisconnectConfirmContent(provider = provider, onBack = { navigator.pop() }, onDisconnect = {
            authViewModel.sendIntent(AuthContract.Intent.DisconnectProvider(provider))
            navigator.pop()
        })
    }
}

object EmailManageScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        val authState by authViewModel.state.collectAsState()
        EmailManageContent(
            userType = authState.userType,
            isEmailConnected = authState.isEmailConnected,
            onConnectClicked = { authViewModel.sendIntent(AuthContract.Intent.EmailConnectClicked) },
            onDisconnectClicked = { authViewModel.sendIntent(AuthContract.Intent.EmailDisconnectClicked) },
            onBack = { navigator.pop() })
    }
}

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
object WithdrawConfirmScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        val authState by authViewModel.state.collectAsState()
        LaunchedEffect(authState.withdrawStep) {
            if (authState.withdrawStep == WithdrawStep.LOADING) navigator.push(WithdrawLoadingScreen)
        }
        WithdrawConfirmContent(
            onOptionSelected = { option ->
                when (option) {
                    "잠시 쉬기 (데이터 보존)" -> authViewModel.sendIntent(AuthContract.Intent.WithdrawPause)
                    "알림 끄기" -> authViewModel.sendIntent(AuthContract.Intent.WithdrawDisableNotification)
                    "게스트로 전환" -> authViewModel.sendIntent(AuthContract.Intent.WithdrawToGuest)
                    "그래도 탈퇴할게요" -> authViewModel.sendIntent(AuthContract.Intent.WithdrawConfirmed)
                }
            })
    }
}

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
object WithdrawLoadingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(Unit) {
            delay(1500)
            navigator.replace(WithdrawCompleteScreen)
        }
        WithdrawLoadingContent()
    }
}

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
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
object HomeScreen : Screen {
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

        var isCalendarShow by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            homeViewModel.effect.collect { effect ->
                when (effect) {
                    is HomeContract.Effect.NavigateToSleepSetting -> navigator.push(
                        SleepSettingScreen
                    )

                    is HomeContract.Effect.NavigateToReport -> navigator.push(ReportScreen(effect.sessionId))
                    is HomeContract.Effect.NavigateToSleepMusicSelection -> navigator.push(
                        SleepMusicSelectionScreen
                    )

                    else -> {}
                }
            }
        }
        LaunchedEffect(Unit) {
            trackingViewModel.effect.collect { effect ->
                when (effect) {
                    is TrackingContract.Effect.NavigateToTracking -> navigator.push(
                        TrackingScreen(
                            effect.duration,
                            effect.sessionId
                        )
                    )

                    else -> {}
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (homeState.selectedTab) {
                    "리포트" -> {
                        LaunchedEffect(trackingState) {
                            reportViewModel.updateTrackingState(trackingState)
                        }
                        ReportContent(
                            user = authViewModel.state.collectAsState().value.user,
                            authState = authViewModel.state.collectAsState().value,
                            trackingState = trackingState,
                            reportState = reportViewModel.state.collectAsState().value,
                            sleepStageHistory = reportViewModel.sleepStageHistory.collectAsState().value,
                            isCalendarShow = isCalendarShow,
                            onCalendarToggleClicked = {
                                isCalendarShow = !isCalendarShow
                            },
                            onReportModeSelected = {
                                reportViewModel.sendIntent(
                                    ReportContract.Intent.SelectReportMode(it)
                                )
                            },
                            onDateSelected = {
                                reportViewModel.sendIntent(
                                    ReportContract.Intent.SelectDate(it)
                                )
                            },
                            onPrevMonthClicked = {
                                reportViewModel.sendIntent(ReportContract.Intent.PrevMonthClicked(it))
                            },
                            onNextMonthClicked = {
                                reportViewModel.sendIntent(ReportContract.Intent.NextMonthClicked(it))
                            },
                            onNavigateToSleepEnvironment = { history, hr, noise ->
                                navigator.push(SleepEnvironmentScreen(history, hr, noise))
                            }
                        )
                    }

                    "마이페이지" -> SettingContent(
                        authViewModel = authViewModel,
                        onNavigateToLoginBenefit = { navigator.push(LoginBenefitScreen) },
                        onNavigateToAccountSetting = { navigator.push(AccountSettingScreen) },
                        onNavigateToNotification = { navigator.push(NotificationSettingScreen) },
                        onNavigateToSupport = { navigator.push(SupportScreen) },
                        onNavigateToAppInfo = { navigator.push(AppInfoScreen) },
                        onNavigateToLegalInfo = { navigator.push(DataSettingScreen) })

                    else -> HomeContent(
                        homeState = homeState,
                        authState = authViewModel.state.collectAsState().value,
                        alarmState = alarmViewModel.state.collectAsState().value,
                        musicState = musicViewModel.state.collectAsState().value,
                        trackingState = trackingState,
                        reportState = reportViewModel.state.collectAsState().value,
                        elapsedSleepMusicSeconds = musicViewModel.elapsedSleepMusicSeconds.collectAsState().value,
                        onSleepSettingClicked = { homeViewModel.sendIntent(HomeContract.Intent.SleepSettingClicked) },
                        onSleepSummaryClicked = { homeViewModel.sendIntent(HomeContract.Intent.SleepSummaryClicked) },
                        onToggleSleepMusicClicked = { musicViewModel.sendIntent(MusicContract.Intent.ToggleSleepMusic) },
                        onSleepMusicClicked = { homeViewModel.sendIntent(HomeContract.Intent.SleepMusicClicked) },
                        onStartTrackingClicked = { duration, musicTitle ->
                            trackingViewModel.sendIntent(
                                TrackingContract.Intent.StartTracking(
                                    duration,
                                    musicTitle,
                                )
                            )
                        },
                        onBottomTabSelected = { tab ->
                            homeViewModel.sendIntent(HomeContract.Intent.SelectBottomTab(tab))
                        })
                }
            }
            CustomBottomTabBar(
                homeState = homeState, onBottomTabSelected = { tab ->
                    homeViewModel.sendIntent(HomeContract.Intent.SelectBottomTab(tab))
                }, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
data class TrackingScreen(
    val duration: Int, val sessionId: String
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val trackingViewModel = koinScreenModel<TrackingViewModel>()
        val trackingState by trackingViewModel.state.collectAsState()
        val reportViewModel = koinScreenModel<ReportViewModel>()
        val musicViewModel = koinScreenModel<MusicViewModel>()
        val alarmViewModel = koinScreenModel<AlarmViewModel>()

        LaunchedEffect(trackingState.isFinished, trackingState.finishedSessionId) {
            val sessionId = trackingState.finishedSessionId
            if (trackingState.isFinished && sessionId != null) {
                reportViewModel.sendIntent(ReportContract.Intent.LoadFinishedSession(sessionId))
            }
        }

        LaunchedEffect(Unit) {
            trackingViewModel.effect.collect { effect ->
                when (effect) {
                    is TrackingContract.Effect.NavigateToReport -> {
                        Napier.d("NavigateToReport")
                        navigator.replace(ReportScreen(effect.sessionId))
                    }

                    is TrackingContract.Effect.NavigateToSleepMusicSelection -> navigator.push(
                        SleepMusicSelectionScreen
                    )
                    is TrackingContract.Effect.NavigateToHome -> navigator.push(
                        HomeScreen
                    )
                    else -> {}
                }
            }
        }

        TrackingContent(
            trackingState = trackingViewModel.state.collectAsState().value,
            musicState = musicViewModel.state.collectAsState().value,
            elapsedSleepTimeSeconds = trackingViewModel.elapsedSleepTimeSeconds.collectAsState().value,
            elapsedSleepMusicSeconds = musicViewModel.elapsedSleepMusicSeconds.collectAsState().value,
            onFinishTracking = { trackingViewModel.sendIntent(TrackingContract.Intent.FinishTracking) },
            onDiscardTracking = { trackingViewModel.sendIntent(TrackingContract.Intent.DiscardTracking) },
            onToggleSleepMusicClicked = { musicViewModel.sendIntent(MusicContract.Intent.ToggleSleepMusic) },
            onChangeMusic = { trackingViewModel.sendIntent(TrackingContract.Intent.ChangeMusicClicked) },
            onUpdateEndTime = { h, m -> trackingViewModel.trackingManager.updateEndTime(h, m) },
        )
    }
}

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
data class SleepEnvironmentScreen(
    val environmentHistory: List<EnvironmentFeature.Snapshot>,
    val avgHeartRate: Float,
    val avgNoise: Float,
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        SleepEnvironmentContent(
            avgHeartRate = avgHeartRate,
            avgNoise = avgNoise,
            environmentHistory = environmentHistory,
            onBack = { navigator.pop() })
    }
}

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
object SleepMusicSelectionScreen : Screen {
    @Composable
    override fun Content() {
        val musicViewModel = koinScreenModel<MusicViewModel>()
        SleepMusicSelectionContent(
            musicState = musicViewModel.state.collectAsState().value,
            elapsedSeconds = musicViewModel.elapsedSleepMusicSeconds.collectAsState().value,
            onTabSelected = {},
            onMusicSelected = { musicViewModel.sendIntent(MusicContract.Intent.MusicSelected(it)) },
            onTogglePlay = { musicViewModel.sendIntent(MusicContract.Intent.ToggleSleepMusic) })
    }
}

// ── 리포트 ────────────────────────────────────

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
data class ReportScreen(val sessionId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val reportViewModel = koinScreenModel<ReportViewModel>()
        val reportState by reportViewModel.state.collectAsState()

        val trackingViewModel = koinScreenModel<TrackingViewModel>()
        val trackingState by trackingViewModel.state.collectAsState()

        val authViewModel = koinScreenModel<AuthViewModel>()
        val authState by authViewModel.state.collectAsState()


        val sleepStageHistory by reportViewModel.sleepStageHistory.collectAsState()

        var isCalendarShow by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            reportViewModel.effect.collect { effect ->
                when (effect) {
                    is ReportContract.Effect.NavigateToSleepEnvironment -> {
                        navigator.push(
                            SleepEnvironmentScreen(
                                reportState.reportData.environmentHistory,
                                reportState.reportData.avgHeartRate,
                                reportState.reportData.avgNoise,
                            )
                        )
                    }
                }
            }
        }
        LaunchedEffect(trackingState) {
            reportViewModel.updateTrackingState(trackingState)
        }
        ReportContent(
            user = authViewModel.state.collectAsState().value.user,
            trackingState = trackingState,
            reportState = reportState,
            authState = authState,
            sleepStageHistory = sleepStageHistory,
            isCalendarShow = isCalendarShow,
            onCalendarToggleClicked = {
                isCalendarShow = !isCalendarShow
            },
            onReportModeSelected = {
                reportViewModel.sendIntent(ReportContract.Intent.SelectReportMode(it))
            },
            onDateSelected = {
                reportViewModel.sendIntent(ReportContract.Intent.SelectDate(it))
            },
            onPrevMonthClicked = {
                reportViewModel.sendIntent(ReportContract.Intent.PrevMonthClicked(it))
            },
            onNextMonthClicked = {
                reportViewModel.sendIntent(ReportContract.Intent.NextMonthClicked(it))
            },
            onNavigateToSleepEnvironment = { history, hr, noise ->
                navigator.push(SleepEnvironmentScreen(history, hr, noise))
            },

        )
    }
}
object SleepSettingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val alarmViewModel = koinScreenModel<AlarmViewModel>()
        val musicViewModel = koinScreenModel<MusicViewModel>()
        LaunchedEffect(Unit) {
            alarmViewModel.effect.collect { effect ->
                when (effect) {
                    is AlarmContract.Effect.NavigateToHome -> navigator.pop()
                }
            }
        }
        SleepSettingContent(
            alarmState = alarmViewModel.state.collectAsState().value,
            musicState = musicViewModel.state.collectAsState().value,
            onChangeAlarmHour = { alarmViewModel.sendIntent(AlarmContract.Intent.ChangeAlarmHour(it)) },
            onChangeAlarmMinute = { m, i ->
                alarmViewModel.sendIntent(
                    AlarmContract.Intent.ChangeAlarmMinute(
                        m, i
                    )
                )
            },
            onToggleAlarm = { alarmViewModel.sendIntent(AlarmContract.Intent.ToggleAlarm) },
            onToggleAlarmPreview = { musicViewModel.sendIntent(MusicContract.Intent.ToggleAlarmPreview) },
            onSelectAlarmSound = {
                alarmViewModel.sendIntent(
                    AlarmContract.Intent.SelectAlarmSound(
                        it
                    )
                )
            },
            onChangeVolume = { alarmViewModel.sendIntent(AlarmContract.Intent.ChangeVolume(it)) },
            onToggleVibration = { alarmViewModel.sendIntent(AlarmContract.Intent.ToggleVibration) },
            onToggleSmartAlarm = { alarmViewModel.sendIntent(AlarmContract.Intent.ToggleSmartAlarm) },
            onSelectSmartAlarmRange = {
                alarmViewModel.sendIntent(
                    AlarmContract.Intent.SelectSmartAlarmRange(
                        it
                    )
                )
            },
            onToggleGradualVolume = { alarmViewModel.sendIntent(AlarmContract.Intent.ToggleGradualVolume) },
            onToggleAutoTracking = { alarmViewModel.sendIntent(AlarmContract.Intent.ToggleAutoTracking) },
            onSelectSleepTrackingMode = {
                alarmViewModel.sendIntent(
                    AlarmContract.Intent.SelectSleepTrackingMode(
                        it
                    )
                )
            },
            onSave = { alarmViewModel.sendIntent(AlarmContract.Intent.SaveButtonClicked) })
    }
}

object SupportScreen : Screen {
    @Composable
    override fun Content() {
        SupportContent(
            mainTab = 0,
            searchQuery = "",
            allItems = listOf(),
            onTabSelected = { },
            onSearchQueryChanged = {})
    }
}

@ExperimentalTime
@ExperimentalMaterial3Api
@ExperimentalCoroutinesApi
object AccountSettingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        AccountSettingContent(
            state = AuthContract.State(),
            onSocialClick = { navigator.push(SocialAccountDetailScreen(it)) },
            onEmailClick = { navigator.push(EmailManageScreen) },
            onLogoutClick = { authViewModel.sendIntent(AuthContract.Intent.LogoutClicked) },
            onWithdrawClick = { authViewModel.sendIntent(AuthContract.Intent.WithdrawClicked) },
            onResetClick = { authViewModel.sendIntent(AuthContract.Intent.ResetDataClicked) },
            onWithdrawConfirm = { navigator.push(WithdrawConfirmScreen) },
            onWithdrawCancel = { authViewModel.sendIntent(AuthContract.Intent.WithdrawCancelled) },
        )
    }
}

@ExperimentalTime
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
object SettingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authViewModel = koinScreenModel<AuthViewModel>()
        SettingContent(
            authViewModel = authViewModel,
            onNavigateToLoginBenefit = { navigator.push(LoginBenefitScreen) },
            onNavigateToAccountSetting = { navigator.push(EmailManageScreen) },
            onNavigateToNotification = { navigator.push(NotificationSettingScreen) },
            onNavigateToSupport = { navigator.push(SupportScreen) },
            onNavigateToAppInfo = { navigator.push(AppInfoScreen) },
            onNavigateToLegalInfo = { navigator.push(DataSettingScreen) })
    }
}

object NotificationSettingScreen : Screen {
    @Composable
    override fun Content() {
        NotificationSettingContent()
    }
}

object AppInfoScreen : Screen {
    @Composable
    override fun Content() {
        AppInfoContent()
    }
}

object DataSettingScreen : Screen {
    @Composable
    override fun Content() {
        DataSettingContent()
    }
}
