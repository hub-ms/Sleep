package com.soundsleeper.app.ui.music

import com.soundsleeper.app.enum_.PredictionStageType
import com.soundsleeper.app.ui.common.AppScopedScreenModel
import com.russhwolf.settings.ObservableSettings
import com.soundsleeper.app.platform.MusicPlayer
import com.soundsleeper.app.domain.repository.SleepMusicRepository
import com.soundsleeper.app.platform.SoundType
import com.soundsleeper.app.platform.TrackingManager
import com.soundsleeper.app.util.DateTimeUtil.tickerFlow
import com.soundsleeper.app.util.PreferencesKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class MusicViewModel(
    private val settings: ObservableSettings,
    private val musicRepository: SleepMusicRepository,
    private val musicPlayer: MusicPlayer,
    private val trackingManager: TrackingManager
)  : AppScopedScreenModel() {

    private val _state = MutableStateFlow(MusicContract.State())
    val state = _state.asStateFlow()

    private val _intentChannel = Channel<MusicContract.Intent>(Channel.BUFFERED)
    private val elapsedSleepMusicSecondsFlow = _state
        .map { it to it.startTime }
        .distinctUntilChanged()
        .flatMapLatest { (currentState, startTime) ->
            if (!currentState.isPlaying || startTime == null) {
                flowOf(currentState.elapsedSeconds)
            } else {
                tickerFlow(1000.milliseconds).map {
                    val now = Clock.System.now()
                    val startInstant = startTime.toInstant(TimeZone.currentSystemDefault())
                    (now - startInstant).inWholeSeconds.toInt().coerceAtLeast(0)
                }
            }
        }
        .stateIn(
            scope = modelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    val elapsedSleepMusicSeconds: StateFlow<Int> = elapsedSleepMusicSecondsFlow

    init {
        modelScope.launch {
            _intentChannel.receiveAsFlow().collect { intent ->
                processIntent(intent)
            }
        }
        modelScope.launch {
            // 깊은 잠이 몇 번 연속으로 잡혔는지. 단계 판정은 한두 번씩 튀기 때문에
            // 한 번 잡혔다고 바로 끄면 아직 깨어 있는데 음악이 멈추는 일이 생긴다.
            var consecutiveDeepSleep = 0

            // 예전에는 trackingState 전체를 받았다. 그 상태는 elapsedMillis 때문에 1초마다
            // 바뀌므로, 잠들었다는 판정이 한 번만 나와도 3초 만에 연속 3회가 채워져
            // 디바운스가 사실상 없는 것과 같았다. 단계가 실제로 바뀔 때만 센다.
            trackingManager.trackingState
                .map { it.isTracking to it.currentSleepStageType }
                .distinctUntilChanged()
                .collect { (isTracking, stage) ->
                    if (!isTracking) {
                        consecutiveDeepSleep = 0
                        _state.update { it.copy(isPlaying = false) }
                        return@collect
                    }

                    if (!_state.value.isAutoStopEnabled) {
                        consecutiveDeepSleep = 0
                        return@collect
                    }

                    if (stage == PredictionStageType.N3) {
                        consecutiveDeepSleep++
                        if (consecutiveDeepSleep >= AUTO_STOP_STAGE_STREAK) {
                            consecutiveDeepSleep = 0
                            // 깊은 잠에 들었으니 음악은 제 역할을 다했다. 자동 모드 자체는
                            // 유지해 다음 측정에서도 같은 방식으로 동작하게 둔다.
                            sendIntent(MusicContract.Intent.StopPlaying)
                        }
                    } else {
                        // 얕아지거나 깨어난 판정이 나오면 처음부터 센다.
                        consecutiveDeepSleep = 0
                    }
                }
        }
        // 💡 타이머 처리: 1초마다 남은 시간 감소 및 자동 종료
        modelScope.launch {
            tickerFlow(1.seconds).collect {
                val currentState = _state.value
                // isPlaying 조건을 걸면 측정 중에는 타이머가 돌지 않는다. 그때 음악은
                // AndroidTrackingManager 가 직접 틀기 때문에 이 플래그가 false 로 남는다.
                if (currentState.remainingSeconds != null) {
                    val nextSeconds = (currentState.remainingSeconds - 1).coerceAtLeast(0)
                    if (nextSeconds == 0) {
                        sendIntent(MusicContract.Intent.StopPlaying)
                        _state.update { it.copy(timerMinutes = null, remainingSeconds = null) }
                    } else {
                        _state.update { it.copy(remainingSeconds = nextSeconds) }
                    }
                }
            }
        }
        loadMusicList()
    }
    fun sendIntent(intent: MusicContract.Intent) {
        modelScope.launch {
            _intentChannel.send(intent)
        }
    }

    private suspend fun processIntent(intent: MusicContract.Intent) {
        when (intent) {
            is MusicContract.Intent.SetTimer -> {
                _state.update {
                    it.copy(
                        timerMinutes = intent.minutes,
                        remainingSeconds = intent.minutes?.let { m -> m * 60 },
                        isAutoStopEnabled = false
                    )
                }
            }
            is MusicContract.Intent.SetAutoStopTimer -> {
                _state.update {
                    it.copy(
                        timerMinutes = null,
                        remainingSeconds = null,
                        isAutoStopEnabled = true
                    )
                }
            }
            is MusicContract.Intent.MusicSelected -> {
                val music = intent.music
                if (music == null) {
                    // 🐛 버그 수정: 이전에는 intent.music?.let{...}으로 감싸져 있어
                    // MusicSelected(null)(카드 재클릭으로 선택 해제)이 아무 동작도 하지 않는
                    // no-op이었습니다. 선택 해제 시 재생을 멈추고 상태를 초기화합니다.
                    musicPlayer.stop()
                    _state.update {
                        it.copy(
                            selectedMusic = null,
                            isPlaying = false,
                            startTime = null,
                            elapsedSeconds = 0
                        )
                    }
                    settings.putBoolean(PreferencesKeys.SleepMusic.WAS_PLAYING, false)
                    return
                }

                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                _state.update {
                    it.copy(
                        selectedMusic = music,
                        isPlaying = intent.isAutoPlay,
                        startTime = if (intent.isAutoPlay) now else null,
                        elapsedSeconds = 0
                    )
                }

                if (intent.isAutoPlay) {
                    musicPlayer.loadMusic(music.musicName)
                    musicPlayer.play(music.musicName, type = SoundType.SLEEP, startSeconds = 0)

                    settings.putString(PreferencesKeys.SleepMusic.LAST_PLAYED_MUSIC_NAME, music.musicName)
                    settings.putBoolean(PreferencesKeys.SleepMusic.WAS_PLAYING, true)
                    settings.putInt(PreferencesKeys.SleepMusic.LAST_PLAYED_POSITION_SECONDS, 0)
                }
            }
            is MusicContract.Intent.TogglePlaying -> {
                val currentIsPlaying = state.value.isPlaying
                val currentElapsed = elapsedSleepMusicSeconds.value
                val currentMusic = state.value.selectedMusic?.musicName

                if (currentIsPlaying) {
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            elapsedSeconds = currentElapsed,
                        )
                    }
                    musicPlayer.pause()

                    settings.putBoolean(PreferencesKeys.SleepMusic.WAS_PLAYING, false)
                    settings.putInt(PreferencesKeys.SleepMusic.LAST_PLAYED_POSITION_SECONDS, currentElapsed)
                } else {
                    val pausedAt = state.value.elapsedSeconds
                    val now = Clock.System.now()
                    val adjustedStartTime = now.minus(pausedAt.seconds).toLocalDateTime(TimeZone.currentSystemDefault())

                    _state.update {
                        it.copy(
                            isPlaying = true,
                            startTime = adjustedStartTime,
                        )
                    }
                    currentMusic?.let {
                        musicPlayer.seek(pausedAt)
                        musicPlayer.play(it, type = SoundType.SLEEP, startSeconds = pausedAt)

                        settings.putBoolean(PreferencesKeys.SleepMusic.WAS_PLAYING, true)
                        settings.putInt(PreferencesKeys.SleepMusic.LAST_PLAYED_POSITION_SECONDS, pausedAt)
                    }
                }
            }
            is MusicContract.Intent.StopPlaying -> {
                // 이미 멈춰 있으면 아무 일도 하지 않는다. 토글과 다른 점이 바로 이것이다.
                if (state.value.isPlaying) {
                    val currentElapsed = elapsedSleepMusicSeconds.value
                    _state.update { it.copy(isPlaying = false, elapsedSeconds = currentElapsed) }
                    settings.putBoolean(PreferencesKeys.SleepMusic.WAS_PLAYING, false)
                    settings.putInt(
                        PreferencesKeys.SleepMusic.LAST_PLAYED_POSITION_SECONDS,
                        currentElapsed
                    )
                }
                // 측정 중에는 트래킹 매니저가 직접 틀어 둔 재생이 돌고 있을 수 있다.
                // 그쪽은 이 플래그를 보지 않으므로 플레이어를 직접 멈춰야 실제로 꺼진다.
                musicPlayer.pause()
            }
            is MusicContract.Intent.ToggleAlarmPreview -> {
                _state.update {
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    it.copy(isPlaying = true, startTime = now, elapsedSeconds = 0)
                }
            }
            is MusicContract.Intent.ToggleFavorite -> {
                val targetMusic = intent.music
                modelScope.launch {
                    musicRepository.toggleFavorite(targetMusic.musicName)
                }
                _state.update { s ->
                    val newList = s.musicList.map { m ->
                        if (m.musicName == targetMusic.musicName) m.copy(isFavorite = !m.isFavorite)
                        else m
                    }
                    val newSelected = if (s.selectedMusic?.musicName == targetMusic.musicName) {
                        s.selectedMusic.copy(isFavorite = !s.selectedMusic.isFavorite)
                    } else s.selectedMusic
                    s.copy(musicList = newList, selectedMusic = newSelected)
                }
            }
            is MusicContract.Intent.ToggleLooping -> {
                _state.update { it.copy(isLooping = !it.isLooping) }
            }
        }
    }

    private fun loadMusicList() {
        modelScope.launch {
            musicRepository.getAllMusic().collect { musicList ->
                _state.update {
                    it.copy(musicList = musicList)
                }
            }
        }
    }

    private companion object {
        /**
         * 이만큼 연속으로 깊은 잠 판정이 나와야 끈다. 단계 판정은 30초에 한 번 나오므로
         * 3회면 약 1분 30초 동안 깊은 잠이 유지됐다는 뜻이다.
         */
        const val AUTO_STOP_STAGE_STREAK = 3
    }
}
