package com.soundsleeper.app.ui.home

import com.soundsleeper.app.ui.common.AppScopedScreenModel
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanFlow
import com.russhwolf.settings.coroutines.getIntFlow
import com.soundsleeper.app.util.PreferencesKeys.Alarm.IS_TIMER
import com.soundsleeper.app.util.PreferencesKeys.Alarm.TIMER_MINUTES
import com.soundsleeper.app.platform.MusicPlayer
import com.soundsleeper.app.domain.model.User
import com.soundsleeper.app.domain.repository.AuthRepository
import com.soundsleeper.app.domain.repository.SleepMusicRepository
import com.soundsleeper.app.domain.repository.SleepSessionRepository
import com.soundsleeper.app.domain.repository.SleepSettingRepository
import com.soundsleeper.app.platform.SoundType
import com.soundsleeper.app.ui.auth.AuthContract
import com.soundsleeper.app.ui.report.DemoReportFactory
import com.soundsleeper.app.ui.report.ReportContract
import com.soundsleeper.app.util.PreferencesKeys
import com.soundsleeper.app.util.SleepSessionUtil.toReportData
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
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

@ExperimentalSettingsApi
class HomeViewModel(
    private val musicPlayer: MusicPlayer,
    private val settings: ObservableSettings,
    private val sleepSettingRepository: SleepSettingRepository,
    private val sleepMusicRepository: SleepMusicRepository,
    private val authRepository: AuthRepository,
    private val sleepSessionRepository: SleepSessionRepository,
)  : AppScopedScreenModel() {
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
        modelScope.launch {
            val user = authRepository.getUser()
            _authState.update { it.copy(user = user) }

            loadYesterdayReport()
            observeSleepSettings()
            restoreLastPlayedSleepMusic()

            // 권한 안내/수면 가이드는 홈 진입 시가 아니라, "수면 시작" 버튼을 눌렀을 때만
            // (beginTracking, AppScreens.kt) 최초 1회씩 뜨도록 바뀌었다. 여기서 더 띄우지 않는다.

            _intentChannel.receiveAsFlow().collect { intent ->
                processIntent(intent)
            }
        }
    }

    fun sendIntent(intent: HomeContract.Intent) {
        modelScope.launch {
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
        } else session.toReportData() to false

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

        sleepSettingRepository.observeSettings()
            .onEach { savedSettings ->
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val wakeUpTime = LocalDateTime(
                    year = today.year,
                    month = today.month,
                    day = today.day,
                    hour = savedSettings.alarm.hour,
                    minute = savedSettings.alarm.minute,
                    second = 0,
                    nanosecond = 0
                )
                val isTimer = settings.getBooleanFlow(IS_TIMER, false).first()
                val timerMinutes = settings.getIntFlow(TIMER_MINUTES, 0).first()

                _state.update { state ->
                    state.copy(
                        wakeUpTime = wakeUpTime,
                        musicName = savedSettings.alarm.sound.titleRes,
                        isTimer = isTimer,
                        timerMinutes = timerMinutes,
                        isRestoring = true
                    )
                }
            }.launchIn(modelScope)
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
