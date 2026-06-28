package com.sleepytime.shared.ui.tracking

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.repository.WeatherRepository
import com.sleepytime.shared.platform.HeartRateMonitor
import com.sleepytime.shared.platform.NoiseDetector
import com.sleepytime.shared.domain.repository.AuthRepository
import com.sleepytime.shared.platform.TrackingManager
import com.sleepytime.shared.util.DateTimeUtil.tickerFlow
import com.sleepytime.shared.util.IdGenerator
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
    private val weatherRepository: WeatherRepository,
    private val heartRateMonitor: HeartRateMonitor,
    private val noiseDetector: NoiseDetector,
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
                .map { it.isFinished }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    val sessionId = trackingManager.trackingState.value.sessionId
                    _effect.emit(TrackingContract.Effect.NavigateToReport(sessionId))
                }
        }
        screenModelScope.launch {
            tickerFlow(1000.milliseconds).collect { _ ->
                _currentTime.value = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                if (state.value.isTracking) {
                    val snapshot = EnvironmentFeature.Snapshot(
                        heartRate = heartRateMonitor.getCurrentHeartRate(),
                        noise = noiseDetector.getCurrentNoise(),
                        temperature = weatherRepository.getCurrentTemperature(),
                        humidity = weatherRepository.getCurrentHumidity(),
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
                    Napier.d("sessionId=$sessionId")
                    trackingManager.start(
                        sessionId = sessionId,
                        duration = intent.duration,
                        musicTitle = intent.musicTitle,
                    )
                    _effect.emit(TrackingContract.Effect.NavigateToTracking(intent.duration, sessionId))
                }
            }
            is TrackingContract.Intent.FinishTracking -> {
                Napier.d("FinishTracking - 5분 이상 정상 리포트 종료")
                trackingManager.finish()
            }
            is TrackingContract.Intent.DiscardTracking -> {
                Napier.d("DiscardTracking - 5분 미만 수면 폐기")
                screenModelScope.launch {
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
}
