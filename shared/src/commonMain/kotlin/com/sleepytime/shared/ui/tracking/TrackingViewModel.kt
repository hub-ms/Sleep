package com.sleepytime.shared.ui.tracking

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.repository.AuthRepository
import com.sleepytime.shared.platform.SensorBridge
import com.sleepytime.shared.platform.TrackingManager
import com.sleepytime.shared.util.DateTimeUtil.tickerFlow
import com.sleepytime.shared.util.IdGenerator.generateSessionId
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
class TrackingViewModel(
    private val authRepository: AuthRepository,
    private val sensorBridge: SensorBridge,
    internal val trackingManager: TrackingManager,
) : ScreenModel {


    private val _state = MutableStateFlow(TrackingContract.State())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<TrackingContract.Effect>()
    val effect = _effect.asSharedFlow()

    private val _environmentHistory = MutableStateFlow<List<EnvironmentFeature.Snapshot>>(emptyList())
    val environmentHistory = _environmentHistory.asStateFlow()

    private val _currentTime = MutableStateFlow(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))

    val elapsedSleepTimeSeconds: StateFlow<Int> = trackingManager.trackingState
        .map { it.elapsedSeconds }
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    init {
        Napier.d("elapsedSleepTimeSeconds: $elapsedSleepTimeSeconds")
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
                        heartRate = sensorBridge.latestHeartRateStats.last,
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
    }
     fun sendIntent(intent: TrackingContract.Intent) {
        when (intent) {
            is TrackingContract.Intent.StartTracking -> {
                screenModelScope.launch {
                    val currentUserType = authRepository.getUserContext()
                    val sessionId = generateSessionId(currentUserType)

                    _state.update {
                        it.copy(
                            sessionId = sessionId
                        )
                    }

                    startAllSensors()
                    trackingManager.start(sessionId, intent.duration, intent.musicTitle)
                    _effect.emit(TrackingContract.Effect.NavigateToTracking(intent.duration, sessionId))
                }
            }
            is TrackingContract.Intent.FinishTracking -> {
                screenModelScope.launch {
                    stopAllSensors()
                    trackingManager.finish()

                    val currentSessionId = state.value.sessionId ?: ""
                    _effect.emit(TrackingContract.Effect.NavigateToReport(sessionId = currentSessionId))
                }
            }
            is TrackingContract.Intent.DiscardTracking -> {
                screenModelScope.launch {
                    stopAllSensors()
                    trackingManager.discard()
                    _effect.emit(TrackingContract.Effect.NavigateToHome)
                }
            }
            is TrackingContract.Intent.ChangeMusicClicked -> {
                screenModelScope.launch {
                    _effect.emit(TrackingContract.Effect.NavigateToSleepMusicSelection)
                }
            }
            else -> {}
        }
    }
    private fun startAllSensors() {
        sensorBridge.startHeartRateSensor(screenModelScope)
        sensorBridge.startNoiseSensor(screenModelScope)
    }

    private fun stopAllSensors() {
        sensorBridge.stopHeartRateSensor()
        sensorBridge.stopNoiseSensor()
    }
}
