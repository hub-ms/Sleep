package com.sleepytime.shared.ui.home

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanFlow
import com.russhwolf.settings.coroutines.getIntFlow
import com.sleepytime.shared.util.PreferencesKeys.Alarm.IS_TIMER
import com.sleepytime.shared.util.PreferencesKeys.Alarm.TIMER_MINUTES
import com.sleepytime.shared.platform.MusicPlayer
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.domain.repository.AuthRepository
import com.sleepytime.shared.domain.repository.SleepMusicRepository
import com.sleepytime.shared.domain.repository.SleepSessionRepository
import com.sleepytime.shared.domain.repository.SleepSettingsRepository
import com.sleepytime.shared.platform.SoundType
import com.sleepytime.shared.ui.auth.AuthContract
import com.sleepytime.shared.ui.report.DemoReportFactory
import com.sleepytime.shared.ui.report.ReportContract
import com.sleepytime.shared.util.PreferencesKeys
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

    private val _effect = MutableSharedFlow<HomeContract.Effect>()
    val effect = _effect.asSharedFlow()

    private val _intentChannel = Channel<HomeContract.Intent>(Channel.BUFFERED)

    init {
        screenModelScope.launch {
            val user = authRepository.getUser()
            _authState.update { it.copy(user = user) }

            loadYesterdayReport()
            observeSleepSettings()
            restoreLastPlayedSleepMusic()

            // 💡 최초실행 권한 플로우 연결: 완성돼 있었지만 네비게이션에 연결되지 않았던
            // PermissionScreen을, 홈에 처음 진입할 때(권한 온보딩을 아직 끝내지 않았을 때) 띄웁니다.
            if (!settings.getBoolean(PreferencesKeys.App.PERMISSION_ONBOARDING_DONE, false)) {
                _effect.emit(HomeContract.Effect.NavigateToPermissionGuide)
            }

            _intentChannel.receiveAsFlow().collect { intent ->
                processIntent(intent)
            }
        }
    }

    fun sendIntent(intent: HomeContract.Intent) {
        screenModelScope.launch {
            _intentChannel.send(intent)
        }
    }

    private suspend fun processIntent(intent: HomeContract.Intent) {
        when (intent) {
            is HomeContract.Intent.SleepSettingClicked -> _effect.emit(HomeContract.Effect.NavigateToSleepSetting)
            is HomeContract.Intent.SleepSummaryClicked -> {
                val latestSession = sleepSessionRepository.getLatestSession()

                latestSession?.let {
                    _effect.emit(HomeContract.Effect.NavigateToReport(latestSession.sessionId))
                }
            }

            is HomeContract.Intent.SleepMusicClicked -> _effect.emit(HomeContract.Effect.NavigateToSleepMusicSelection)
            is HomeContract.Intent.ToggleTimer -> {
                val newTimer = !_state.value.isTimer
                _state.update { it.copy(isTimer = newTimer) }
                settings.putBoolean(IS_TIMER, newTimer)
            }
            is HomeContract.Intent.SelectBottomTab -> _state.update { it.copy(selectedTab = intent.tab) }
            is HomeContract.Intent.SetTimerMinutes -> {
                _state.update { it.copy(timerMinutes = intent.minutes) }
                settings.putInt(TIMER_MINUTES, intent.minutes)
            }
        }
    }
    private suspend fun loadYesterdayReport() {
        val yesterday = Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(1, DateTimeUnit.DAY)

        val session = sleepSessionRepository.getSessionByDate(yesterday)

        val (reportData, isPreview) = if (session == null) {
            DemoReportFactory.createPreviewData(
                currentUser?.userId ?: 0L,
                yesterday
            ) to true
        } else session.toReportData(yesterday) to false

        _reportState.update {
            it.copy(
                date       = yesterday,
                isPreview  = isPreview,
                reportData = reportData
            )
        }
        Napier.d("loadYesterdayReport 완료: date=$yesterday, isPreview=$isPreview, scores=${reportData.dailyScores.keys}")
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
            }.launchIn(screenModelScope)
    }
    private suspend fun restoreLastPlayedSleepMusic() {
        val lastMusicName = settings.getStringOrNull(PreferencesKeys.SleepMusic.LAST_PLAYED_MUSIC_NAME)
            ?: return
        val wasPlaying = settings.getBoolean(PreferencesKeys.SleepMusic.WAS_PLAYING, false)
        val lastPosition = settings.getInt(PreferencesKeys.SleepMusic.LAST_PLAYED_POSITION_SECONDS, 0)

        val music = sleepMusicRepository.getMusicByMusicName(lastMusicName) ?: return

        if (!musicPlayer.isPlaying) {
            musicPlayer.loadMusic(music.musicName)
            if (wasPlaying) {
                musicPlayer.seek(lastPosition)
                musicPlayer.play(musicName = music.musicName, type = SoundType.SLEEP, startSeconds = lastPosition)
            }
        }
    }
}
