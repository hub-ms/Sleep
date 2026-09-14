package com.sleepytime.shared.ui.music

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.russhwolf.settings.ObservableSettings
import com.sleepytime.shared.platform.MusicPlayer
import com.sleepytime.shared.domain.repository.SleepMusicRepository
import com.sleepytime.shared.platform.SoundType
import com.sleepytime.shared.platform.TrackingManager
import com.sleepytime.shared.util.DateTimeUtil.tickerFlow
import com.sleepytime.shared.util.PreferencesKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
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
) : ScreenModel {

    private val _state = MutableStateFlow(MusicContract.State())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<MusicContract.Effect>()
    val effect = _effect.asSharedFlow()

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
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    val elapsedSleepMusicSeconds: StateFlow<Int> = elapsedSleepMusicSecondsFlow

    init {
        screenModelScope.launch {
            _intentChannel.receiveAsFlow().collect { intent ->
                processIntent(intent)
            }
        }
        screenModelScope.launch {
            trackingManager.trackingState.collect { trackingState ->
                if (!trackingState.isTracking) {
                    _state.update { it.copy(isPlaying = false) }
                }
            }
        }
        // 💡 타이머 처리: 1초마다 남은 시간 감소 및 자동 종료
        screenModelScope.launch {
            tickerFlow(1.seconds).collect {
                val currentState = _state.value
                if (currentState.isPlaying && currentState.remainingSeconds != null) {
                    val nextSeconds = (currentState.remainingSeconds - 1).coerceAtLeast(0)
                    if (nextSeconds == 0) {
                        sendIntent(MusicContract.Intent.TogglePlaying)
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
        screenModelScope.launch {
            _intentChannel.send(intent)
        }
    }

    private suspend fun processIntent(intent: MusicContract.Intent) {
        when (intent) {
            is MusicContract.Intent.SetTimer -> {
                _state.update { 
                    it.copy(
                        timerMinutes = intent.minutes,
                        remainingSeconds = intent.minutes?.let { m -> m * 60 }
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
            is MusicContract.Intent.SleepMusicClicked -> {
                _effect.emit(MusicContract.Effect.NavigateToSleepMusicSelection)
            }
            is MusicContract.Intent.ToggleAlarmPreview -> {
                _state.update {
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    it.copy(isPlaying = true, startTime = now, elapsedSeconds = 0)
                }
            }
            is MusicContract.Intent.ToggleFavorite -> {
                val targetMusic = intent.music
                screenModelScope.launch {
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
        screenModelScope.launch {
            musicRepository.getAllMusic().collect { musicList ->
                _state.update {
                    it.copy(musicList = musicList)
                }
            }
        }
    }
}
