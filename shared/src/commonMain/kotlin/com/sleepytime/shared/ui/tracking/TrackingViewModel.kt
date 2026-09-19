package com.sleepytime.shared.ui.tracking

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.repository.AuthRepository
import com.sleepytime.shared.platform.MusicPlayer
import com.sleepytime.shared.platform.SensorBridge
import com.sleepytime.shared.platform.TrackingManager
import com.sleepytime.shared.domain.repository.SleepSettingsRepository
import com.sleepytime.shared.ui.music.MusicContract
import com.sleepytime.shared.util.DateTimeUtil.tickerFlow
import com.sleepytime.shared.util.IdGenerator.generateSessionId
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
class TrackingViewModel(
    private val musicPlayer: MusicPlayer,
    private val authRepository: AuthRepository,
    private val sensorBridge: SensorBridge,
    private val sleepSettingsRepository: SleepSettingsRepository,
    internal val trackingManager: TrackingManager,
) : ScreenModel {


    private val _state = MutableStateFlow(TrackingContract.State())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<TrackingContract.Effect>()
    val effect = _effect.asSharedFlow()

    private val _musicEffect = MutableSharedFlow<MusicContract.Effect>()

    private val _environmentHistory = MutableStateFlow<List<EnvironmentFeature.Snapshot>>(emptyList())
    val environmentHistory = _environmentHistory.asStateFlow()

    private val _currentTime = MutableStateFlow(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))

    val elapsedSleepTimeMillis: StateFlow<Long> = trackingManager.trackingState
        .map { it.elapsedMillis }
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    init {
        Napier.d("elapsedSleepTimeMillis: $elapsedSleepTimeMillis")
        screenModelScope.launch {
            trackingManager.trackingState.collect { _state.value = it }
        }
        screenModelScope.launch {
            trackingManager.trackingState
                .map { state -> if (state.isFinished) state.sessionId else null }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { sessionId ->
                    stopAllSensors()
                    _effect.emit(TrackingContract.Effect.NavigateToReport(sessionId))
                }
        }
        screenModelScope.launch {
            tickerFlow(1000.milliseconds).collect { _ ->
                _currentTime.value = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                if (state.value.isTracking) {
                    val snapshot = EnvironmentFeature.Snapshot(
                        noise = sensorBridge.latestNoiseStats.last,
                    )
                    Napier.d("snapshot=$snapshot")
                    _state.update { current ->
                        current.copy(
                            environmentHistory = (current.environmentHistory + snapshot).takeLast(300)
                        )
                    }
                }
            }
        }
        screenModelScope.launch {
            trackingManager.wakeAlarmEvent.collect { sessionId ->
                if (state.value.isTracking) {
                    _effect.emit(TrackingContract.Effect.NavigateToWakeUp(sessionId))
                }
            }
        }
        screenModelScope.launch {
            trackingManager.trackingState
                .map { it.permissionDenied }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    _effect.emit(TrackingContract.Effect.NavigateToPermissionGuide)
                }
        }
    }
     fun sendIntent(intent: TrackingContract.Intent) {
        when (intent) {
            is TrackingContract.Intent.StartTracking -> {
                screenModelScope.launch {
                    val currentUserType = authRepository.getUserContext()
                    val sessionId = generateSessionId(currentUserType)

                    // 1. 설정된 알람 시각 가져오기
                    val alarm = sleepSettingsRepository.observeSettings().first()
                    val tz = TimeZone.currentSystemDefault()
                    val now = Clock.System.now()
                    val today = now.toLocalDateTime(tz)

                    var wakeUpTime = LocalDateTime(
                        year = today.year,
                        monthNumber = today.monthNumber,
                        dayOfMonth = today.dayOfMonth,
                        hour = alarm.hour,
                        minute = alarm.minute,
                        second = 0,
                        nanosecond = 0
                    ).toInstant(tz)

                    if (wakeUpTime <= now) {
                        wakeUpTime = wakeUpTime.plus(1, DateTimeUnit.DAY, tz)
                    }

                    // 2. 정확한 durationMillis 계산
                    val calculatedDurationMillis = wakeUpTime.toEpochMilliseconds() - now.toEpochMilliseconds()

                    _state.update {
                        it.copy(
                            sessionId = sessionId,
                            durationMillis = calculatedDurationMillis
                        )
                    }

                    startAllSensors()
                    trackingManager.start(sessionId, calculatedDurationMillis, intent.musicTitle)
                    _effect.emit(TrackingContract.Effect.NavigateToTracking(calculatedDurationMillis, sessionId))
                }
            }
            is TrackingContract.Intent.FinishTracking -> {
                screenModelScope.launch {
                    stopAllSensors()
                    val currentSessionId = state.value.sessionId ?: ""
                    val currentMusicTitle = state.value.musicTitle
                    trackingManager.finish(currentSessionId, currentMusicTitle)
                    withTimeoutOrNull(10_000L.milliseconds) {
                        trackingManager.trackingState.first { it.isFinished }
                    }
                    _effect.emit(TrackingContract.Effect.NavigateToReport(sessionId = currentSessionId))
                }
            }
            is TrackingContract.Intent.DiscardTracking -> {
                screenModelScope.launch {
                    stopAllSensors()
                    val currentSessionId = state.value.sessionId ?: ""
                    val currentMusicTitle = state.value.musicTitle
                    trackingManager.discard(currentSessionId, currentMusicTitle)
                    _effect.emit(TrackingContract.Effect.NavigateToHome)
                }
            }
        }
    }
    private fun startAllSensors() {
        sensorBridge.startNoiseSensor(screenModelScope)
    }

    private fun stopAllSensors() {
        sensorBridge.stopNoiseSensor()
    }
}
