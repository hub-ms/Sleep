package com.sleepytime.shared.ui.home

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanFlow
import com.russhwolf.settings.coroutines.getIntFlow
import com.sleepytime.shared.data.local.dao.SleepSessionDao
import com.sleepytime.shared.util.PreferencesKeys.Alarm.IS_TIMER
import com.sleepytime.shared.util.PreferencesKeys.Alarm.TIMER_MINUTES
import com.sleepytime.shared.platform.MusicPlayer
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.domain.repository.AuthRepository
import com.sleepytime.shared.domain.repository.SleepMusicRepository
import com.sleepytime.shared.domain.repository.SleepSessionRepository
import com.sleepytime.shared.domain.repository.SleepSettingsRepository
import com.sleepytime.shared.enum_.ReportTab
import com.sleepytime.shared.ui.auth.AuthContract
import com.sleepytime.shared.ui.environment.EnvironmentContract
import com.sleepytime.shared.ui.report.DemoReportFactory
import com.sleepytime.shared.ui.report.ReportContract
import com.sleepytime.shared.util.SleepSessionUtil.toReportData
import io.github.aakira.napier.Napier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

@ExperimentalSettingsApi
class HomeViewModel(
    private val musicPlayer: MusicPlayer,
    private val settings: ObservableSettings,
    private val sleepSettingsRepository: SleepSettingsRepository,
    private val sleepMusicRepository: SleepMusicRepository,
    private val authRepository: AuthRepository,
    private val sleepSessionRepository: SleepSessionRepository,
    private val sleepSessionDao: SleepSessionDao,
) : ScreenModel {
    private val initialDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val yesterday = initialDate.minus(1, DateTimeUnit.DAY)
    private val _state = MutableStateFlow(HomeContract.State())
    val state = _state.asStateFlow()

    private val _authState = MutableStateFlow(AuthContract.State())

    private val _reportState = MutableStateFlow(
        ReportContract.State(
            date = yesterday,
            isPreview = true,
            sessionDates = emptySet(),
            reportData = DemoReportFactory.createPreviewData(0L, yesterday),
            weeklyChartData = DemoReportFactory.createPreviewData(0L, yesterday)
        )
    )

    private val currentUser: User?
        get() = _authState.value.user

    private val _environmentState = MutableStateFlow<EnvironmentContract.State>(
        EnvironmentContract.State.Success(
            temperature = 0f,
            humidity = 0f,
            precipitation = 0f,
            nx = "",
            ny = ""
        )
    )

    private val _effect = MutableSharedFlow<HomeContract.Effect>()
    val effect = _effect.asSharedFlow()

    private val _intentChannel = Channel<HomeContract.Intent>(Channel.BUFFERED)

    init {
        screenModelScope.launch {
            val user = authRepository.getUser()
            _authState.update { it.copy(user = user) }

            loadYesterdayReport()
            observeSleepSettings()
            _intentChannel.receiveAsFlow().collect { intent ->
                processIntent(intent)
            }
        }
    }
    private fun observeSleepSettings() {
        Napier.d(tag = "HomeViewModel", message = "수면 설정 관찰(Observe) 시작")

        sleepSettingsRepository.observeSettings()
            .onEach { alarm ->
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val wakeUpTime = LocalDateTime(
                    year = today.year,
                    monthNumber = today.monthNumber,
                    dayOfMonth = today.dayOfMonth,
                    hour = alarm.hour,
                    minute = alarm.minute,
                    second = 0,
                    nanosecond = 0
                )
                val isTimer = settings.getBooleanFlow(IS_TIMER, false).first()
                val timerMinutes = settings.getIntFlow(TIMER_MINUTES, 0).first()

                _state.update { state ->
                    state.copy(
                        wakeUpTime = wakeUpTime,
                        musicName = alarm.sound.titleRes,
                        isTimer = isTimer,
                        timerMinutes = timerMinutes,
                        isRestoring = true
                    )
                }

                val musicId = alarm.sound.id
                sleepMusicRepository.getMusicByMusicName(musicId)?.let { music ->
                    if (!musicPlayer.isPlaying) { // 현재 재생중이 아닐 때만 플레이 조건문 권장
                        musicPlayer.loadMusic(music.musicName)
                        musicPlayer.play(music.musicName, startSeconds = 0)
                    }
                }
            }.launchIn(screenModelScope)
    }
    fun sendIntent(intent: HomeContract.Intent) {
        screenModelScope.launch {
            _intentChannel.send(intent)
        }
    }

    private suspend fun processIntent(intent: HomeContract.Intent) {
        when (intent) {
            is HomeContract.Intent.SleepSettingClicked -> {
                Napier.d(tag = "HomeVM", message = "SleepSettingClicked")
                _effect.emit(HomeContract.Effect.NavigateToSleepSetting)
            }
            is HomeContract.Intent.SleepSummaryClicked -> {
                val latestSession = sleepSessionRepository.getLatestSession()

                if (latestSession != null) {
                    _effect.emit(
                        HomeContract.Effect.NavigateToReport(latestSession.sessionId)
                    )
                } else {
                    Napier.e(tag = "HomeVM", message = "No sleep session found")
                }
            }

            is HomeContract.Intent.SleepMusicClicked -> _effect.emit(HomeContract.Effect.NavigateToSleepMusicSelection)
            is HomeContract.Intent.ToggleTimer -> {
                val newTimer = !_state.value.isTimer
                _state.update { it.copy(isTimer = newTimer) }
                settings.putBoolean(IS_TIMER, newTimer)
            }
            is HomeContract.Intent.SelectBottomTab -> {
                _state.update { it.copy(selectedTab = intent.tab) }
            }

            is HomeContract.Intent.SetTimerMinutes -> {
                _state.update { it.copy(timerMinutes = intent.minutes) }
                settings.putInt(TIMER_MINUTES, intent.minutes)
            }


            is HomeContract.Intent.TutorialClicked-> {
                _effect.emit(HomeContract.Effect.NavigateToTutorial)
            }
        }
    }
    private suspend fun loadYesterdayReport() {
        val yesterday = Clock.System.todayIn(TimeZone.currentSystemDefault())
            .minus(1, DateTimeUnit.DAY)
        val epochMs = yesterday
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()

        val session = sleepSessionRepository.getSessionByDate(epochMs)

        val (reportData, isPreview) = if (session == null) {
            DemoReportFactory.createPreviewData(
                currentUser?.userId ?: 0L,
                yesterday
            ) to true
        } else {
            // 세션 있음 → 실측 데이터
            session.toReportData(yesterday) to false
        }

        _reportState.update {
            it.copy(
                date       = yesterday,
                isPreview  = isPreview,
                reportData = reportData
            )
        }
        Napier.d("loadYesterdayReport 완료: date=$yesterday, isPreview=$isPreview, scores=${reportData.dailyScores.keys}")
    }
}
