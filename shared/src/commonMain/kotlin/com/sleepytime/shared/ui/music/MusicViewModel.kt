package com.sleepytime.shared.ui.music

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.sleepytime.shared.platform.MusicPlayer
import com.sleepytime.shared.domain.repository.SleepMusicRepository
import com.sleepytime.shared.platform.TrackingManager
import com.sleepytime.shared.util.DateTimeUtil.tickerFlow
import io.github.aakira.napier.Napier
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
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class MusicViewModel(
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
        loadMusicList()
    }
    fun sendIntent(intent: MusicContract.Intent) {
        screenModelScope.launch {
            _intentChannel.send(intent)
        }
    }

    private suspend fun processIntent(intent: MusicContract.Intent) {
        when (intent) {
            is MusicContract.Intent.MusicSelected -> {
                Napier.d(tag = "MusicViewModel", message = "music: ${intent.music}")
                val musicName = intent.music?.musicName
                val currentTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                _state.update {
                    it.copy(
                        selectedMusic = intent.music,
                        isPlaying = true,
                        startTime = currentTime,
                        elapsedSeconds = 0
                    )
                }
                musicName?.let { musicPlayer.play(it, 0) }
                _effect.emit(MusicContract.Effect.NavigateBack(intent.music))
            }
            is MusicContract.Intent.ToggleSleepMusic -> {
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
                } else {
                    val pausedAt = state.value.elapsedSeconds
                    _state.update {
                        it.copy(
                            isPlaying = true,
                            startTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                            elapsedSeconds = 0
                        )
                    }
                    currentMusic?.let {
                         musicPlayer.seek(pausedAt)
                         musicPlayer.play(it, startSeconds = pausedAt)
                    }
                }
            }
            is MusicContract.Intent.ToggleAlarmPreview -> {
                _state.update {
                    it.copy(isPlaying = !it.isPlaying)
                }
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
