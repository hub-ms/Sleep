package com.soundsleeper.app.ui.tracking

import com.soundsleeper.app.ui.common.AppScopedScreenModel
import com.soundsleeper.app.domain.model.EnvironmentFeature
import com.soundsleeper.app.domain.repository.AuthRepository
import com.soundsleeper.app.platform.SensorBridge
import com.soundsleeper.app.platform.TrackingManager
import com.soundsleeper.app.domain.repository.SleepSettingRepository
import com.soundsleeper.app.util.DateTimeUtil.tickerFlow
import com.soundsleeper.app.util.IdGenerator.generateSessionId
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * 측정 화면(TrackingScreen)의 ViewModel. 측정 시작/종료/취소 Intent를 받아
 * [TrackingManager](실제 센서/서비스/분석을 담당하는 플랫폼 구현체)에 위임하고,
 * 그 결과를 관찰해 화면 전환 Effect(측정 화면 진입, 기상 화면, 리포트 화면, 홈 복귀)로 내보낸다.
 */
@ExperimentalTime
@ExperimentalCoroutinesApi
class TrackingViewModel(
    private val authRepository: AuthRepository,
    private val sensorBridge: SensorBridge,
    private val sleepSettingRepository: SleepSettingRepository,
    internal val trackingManager: TrackingManager,
)  : AppScopedScreenModel() {


    private val _state = MutableStateFlow(TrackingContract.State())
    val state = _state.asStateFlow()

    // 기상 화면이 올라오면 이 화면의 수집자가 사라진다. 버퍼가 없으면 그 순간의 emit 이
    // 영구히 멈춰 해당 코루틴이 다시는 돌지 않는다.
    private val _effect = MutableSharedFlow<TrackingContract.Effect>(extraBufferCapacity = 1)
    val effect = _effect.asSharedFlow()

    private val _environmentHistory = MutableStateFlow<List<EnvironmentFeature.Snapshot>>(emptyList())
    val environmentHistory = _environmentHistory.asStateFlow()

    private val _currentTime = MutableStateFlow(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))

    val elapsedSleepTimeMillis: StateFlow<Long> = trackingManager.trackingState
        .map { it.elapsedMillis }
        .stateIn(
            scope = modelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    /**
     * ViewModel 생성 시 네 가지 백그라운드 구독을 건다:
     * (1) trackingManager의 상태를 그대로 이 ViewModel의 state로 반영,
     * (2) 측정이 끝났는지 감시해 자동으로 리포트 화면으로 보내기,
     * (3) 1초마다 소음 스냅샷을 모아 환경 히스토리를 쌓기,
     * (4) 알람이 울렸는지/권한이 거부됐는지 감시해 기상 화면·권한 안내로 보내기.
     */
    init {
        Napier.d("elapsedSleepTimeMillis: $elapsedSleepTimeMillis")
        // trackingManager(서비스/센서 레이어)가 가진 상태를 그대로 이 화면의 state로 미러링한다.
        modelScope.launch {
            trackingManager.trackingState.collect { _state.value = it }
        }
        modelScope.launch {
            // 🐛 버그 수정: state.sessionId 는 측정 시작부터 끝까지 바뀌지 않는 값이라, 분석이
            // 실패해 DB에 저장되지 않은 경우에도 그대로 남아 있었다. "저장된 세션이 있는지"는
            // isFinished 가 true 로 바뀌는 순간의 finishedSessionId(저장 실패 시 null)를 봐야
            // 한다. null 도 그대로 흘려보내 리포트 화면이 최신 세션으로 대체하도록 한다.
            // isFinished가 false→true로 바뀌는 순간이 "측정 종료 + 분석 완료" 시점이다.
            // 이 흐름은 수동 종료(FinishTracking)와는 별개로, 서비스 쪽에서 비동기로 분석이
            // 끝났을 때(예: 알람으로 자동 종료된 경우)도 똑같이 리포트로 보내기 위한 안전망이다.
            trackingManager.trackingState
                .map { state -> state.isFinished to state.finishedSessionId }
                .distinctUntilChanged()
                .collect { (isFinished, finishedSessionId) ->
                    if (!isFinished) return@collect
                    stopAllSensors()
                    _effect.emit(TrackingContract.Effect.NavigateToReport(finishedSessionId))
                }
        }
        modelScope.launch {
            // 측정 중일 때만 1초마다 현재 소음 스냅샷을 모아 최근 300개(=5분)까지 보관한다.
            // 측정 화면의 환경(소음) 그래프/통계가 이 히스토리를 사용한다.
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
        modelScope.launch {
            // 알람 시각이 되면 trackingManager가 wakeAlarmEvent를 흘려보낸다 — 기상 화면으로 이동.
            trackingManager.wakeAlarmEvent.collect { sessionId ->
                if (state.value.isTracking) {
                    _effect.emit(TrackingContract.Effect.NavigateToWakeUp(sessionId))
                }
            }
        }
        modelScope.launch {
            // 측정 중 마이크 권한이 거부된 것이 감지되면 권한 안내 화면으로 보낸다.
            trackingManager.trackingState
                .map { it.permissionDenied }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    _effect.emit(TrackingContract.Effect.NavigateToPermissionGuide)
                }
        }
    }

    /**
     * 화면에서 올라오는 Intent(측정 시작/종료/취소)를 처리하는 단일 입구.
     * 세 Intent 모두 실제 센서/서비스 제어는 [trackingManager]에 위임하고,
     * 그 결과에 따라 화면 전환 Effect를 내보내는 역할을 한다.
     */
     fun sendIntent(intent: TrackingContract.Intent) {
        when (intent) {
            is TrackingContract.Intent.StartTracking -> {
                // 홈 화면의 "수면 시작" 버튼(beginTracking)이 보낸 Intent를 처리한다.
                modelScope.launch {
                    val currentUserType = authRepository.getUserContext()
                    val sessionId = generateSessionId(currentUserType)
                    Napier.d("sessionId=$sessionId")

                    // 1. 설정된 알람 시각 가져오기
                    val savedSettings = sleepSettingRepository.observeSettings().first()
                    val tz = TimeZone.currentSystemDefault()
                    val now = Clock.System.now()
                    val today = now.toLocalDateTime(tz)

                    var wakeUpTime = LocalDateTime(
                        year = today.year,
                        month = today.month,
                        day = today.day,
                        hour = savedSettings.alarm.hour,
                        minute = savedSettings.alarm.minute,
                        second = 0,
                        nanosecond = 0
                    ).toInstant(tz)

                    // 설정된 알람 시각이 이미 지났다면(예: 밤 11시인데 알람이 오전 7시) 다음날로 간주한다.
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

                    // 노이즈 센서를 먼저 켜고(startAllSensors), 가속도/자이로 등 나머지 센서와
                    // 분석/저장 파이프라인은 trackingManager.start가 서비스 쪽에서 담당한다.
                    startAllSensors()
                    trackingManager.start(sessionId, calculatedDurationMillis, intent.musicName)
                    _effect.emit(TrackingContract.Effect.NavigateToTracking(calculatedDurationMillis, sessionId))
                }
            }
            is TrackingContract.Intent.FinishTracking -> {
                // 측정 화면에서 사용자가 직접 "측정 종료"를 눌렀을 때 처리한다.
                modelScope.launch {
                    stopAllSensors()
                    val currentSessionId = state.value.sessionId ?: ""
                    val currentMusicName = state.value.musicName
                    trackingManager.finish(currentSessionId, currentMusicName)
                    // 분석이 끝나 isFinished가 true가 될 때까지 최대 10초 기다린다.
                    val finishedState = withTimeoutOrNull(10_000L.milliseconds) {
                        trackingManager.trackingState.first { it.isFinished }
                    }
                    // 🐛 버그 수정: 분석 결과와 상관없이 finish() 호출 전에 들고 있던 sessionId를
                    // 그대로 리포트 화면에 넘겨서, 분석이 실패해 DB에 저장되지 않은 세션으로
                    // 이동한 뒤 10초간 재시도만 하다 최신 세션으로 대체되는 낭비가 있었다.
                    // 분석이 끝난 뒤의 실제 결과(finishedSessionId, 실패 시 null)를 쓴다.
                    // 타임아웃으로 끝내 결과를 못 받았을 때만 원래 sessionId로 최선 추정한다.
                    val resolvedSessionId = if (finishedState != null) finishedState.finishedSessionId
                        else currentSessionId.ifBlank { null }
                    _effect.emit(TrackingContract.Effect.NavigateToReport(sessionId = resolvedSessionId))
                }
            }
            is TrackingContract.Intent.DiscardTracking -> {
                // 사용자가 측정을 취소(저장하지 않고 버림)했을 때 처리한다.
                modelScope.launch {
                    stopAllSensors()
                    val currentSessionId = state.value.sessionId ?: ""
                    val currentMusicName = state.value.musicName
                    trackingManager.discard(currentSessionId, currentMusicName)
                    _effect.emit(TrackingContract.Effect.NavigateToHome)
                }
            }
        }
    }

    /** 측정 시작 시 이 ViewModel이 직접 켜는 센서(소음 측정)를 시작한다. 가속도/자이로는 trackingManager가 켠다. */
    private fun startAllSensors() {
        sensorBridge.startNoiseSensor(modelScope)
    }

    /** 측정 종료/취소 시 위에서 켠 소음 센서를 멈춘다. */
    private fun stopAllSensors() {
        sensorBridge.stopNoiseSensor()
    }
}
