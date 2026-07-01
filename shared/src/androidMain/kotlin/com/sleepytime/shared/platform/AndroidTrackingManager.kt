package com.sleepytime.shared.platform

import android.content.Context
import android.util.Log
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepSession
import com.sleepytime.shared.domain.model.Stats
import com.sleepytime.shared.domain.repository.SleepSessionRepository
import com.sleepytime.shared.ui.tracking.TrackingContract
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.onSuccess
import kotlin.time.Duration.Companion.days

class AndroidTrackingManager @Inject constructor(
    private val context: Context,
    private val classifier: SleepStageClassifier,
    private val measureManager: AndroidSleepMeasureManager,
    private val sleepSessionRepository: SleepSessionRepository,
    private val heartRateMonitor: HeartRateMonitor,
    private val noiseDetector: NoiseDetector,
    private val sensorBridge: SensorBridge,
    private val musicPlayer: MusicPlayer,
    private val csvExporter: CsvExporter
) : TrackingManager {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _trackingState = MutableStateFlow(TrackingContract.State())
    override val trackingState: StateFlow<TrackingContract.State> = _trackingState.asStateFlow()

    private var onNotificationUpdate: ((String) -> Unit)? = null
    private var onRequestStopForeground: (() -> Unit)? = null
    private var isCleanedUp = false

    companion object {
        private const val MIN_MINUTES_REQUIRED = 30 // 최소 수면 측정 시간 조건 (예: 30분)
        private const val ELAPSED_UPDATE_INTERVAL = 1000L
    }

    fun attachCallbacks(
        onNotificationUpdate: (String) -> Unit,
        onRequestStopForeground: () -> Unit
    ) {
        this.onNotificationUpdate = onNotificationUpdate
        this.onRequestStopForeground = onRequestStopForeground
    }

    override fun start(sessionId: String, duration: Int, musicTitle: String?) {
        if (scope.coroutineContext[Job]?.isActive != true) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
        scope.launch {
            isCleanedUp = false
            _trackingState.update { it.copy(isFinished = false) }

            val now = System.currentTimeMillis()
            val session = SleepSession(
                sleepMetrics = SleepMetrics(
                    wakeCountScore = 0.0,
                    continuityScore = 0.0,
                    deepScore = 0.0,
                    remScore = 0.0,
                    latencyScore = 0.0,
                    awakeMinutes = 0.0,
                    lightMinutes = 0.0,
                    deepMinutes = 0.0,
                    remMinutes = 0.0,
                    sleepLatencyMinutes = 0.0,
                    wakeCount = 0
                ),
                environment = SleepSession.Environment(
                    history = emptyList(),
                    stats = EnvironmentFeature.Statistics(
                        heartRate = Stats(),
                        noise = Stats(),
                        temperature = Stats(),
                        humidity = Stats()
                    ),
                    flags = EnvironmentFeature.Flag(
                        isHeartRateAnomaly = false,
                        isNoiseDanger = false,
                        isTempExtreme = false,
                        isHumidityExtreme = false
                    ),
                ),
                duration = SleepSession.Duration(
                    awakeMinutes = 0.0,
                    lightMinutes = 0.0,
                    deepMinutes = 0.0,
                    remMinutes = 0.0,
                    sleepLatencyMinutes = 0.0,
                ),
                csvData = SleepSession.CsvData(
                    sensorCsv = "",
                    environmentCsv = ""
                ),
                timestamp = SleepSession.Timestamp(
                    createdAt = now,
                    updatedAt = now
                ),
                stageTimeline = emptyList(),
                stagesDistribution = emptyMap(),
                sleepEfficiency = 0,

                sessionId = sessionId,

                date = now,
                wakeCount = 0,
            )


            Log.d("SleepTracker", "startTracking() - 음악: $musicTitle")

            sleepSessionRepository.insertSession(session)

            classifier.initialize(context)

            val initResult = sleepSessionRepository.initializeModel()
            if (initResult.isFailure) {
                Log.e("TrackingService", "모델 초기화 실패", initResult.exceptionOrNull())
                clear()
                return@launch
            }
            Napier.d("musicTitle:$musicTitle")

            musicTitle?.let {
                withContext(Dispatchers.Main) {
                    musicPlayer.setVolume(0.4f)

                    musicPlayer.play(it, startSeconds = 0)
                    Log.d("TrackingService", "음악 재생: $it")
                }
            }

            setupSensorCallbacks()
            measureManager.start()
            // Note: Sensors are started through monitor triggers within measureManager or handled by OS background sensors.
            // Bridge simply reads the current state.

            sensorBridge.startHeartRateSensor(scope)
            sensorBridge.startNoiseSensor(scope)
            heartRateMonitor.startMonitoring(scope)
            noiseDetector.startMonitoring(scope)

            _trackingState.update {
                it.copy(
                    isTracking = true,
                    trackingStartTime = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault()),
                    sessionId = session.sessionId
                )
            }

            onNotificationUpdate?.invoke("수면 측정 중..")
            startElapsedTimeUpdater()
        }
    }
    private fun setupSensorCallbacks() {
        measureManager.onMinuteAggregateReady = { aggregate ->
            scope.launch {
                Log.d("SleepTracker", "1분 압축 데이터 수집됨: ${aggregate.timestampBucket}")
                _trackingState.update { current ->
                    current.copy(
                        avgHeartRate = aggregate.avgHeartRate,
                        avgNoise = aggregate.avgNoiseDb
                    )
                }
            }
        }
        measureManager.onWindowReady = { windowData ->
            scope.launch {
                Napier.d("onWindowReady called, isReady=${sleepSessionRepository.isReady()}")
                if (!sleepSessionRepository.isReady()) return@launch

                sleepSessionRepository.analyzeSleepData(sensorData = windowData, environmentFeature = null)
                    .onSuccess { analysis ->
                        Log.d("SleepTracker", "analyzeSleepData success, history size 증가")
                        _trackingState.update {
                            it.copy(currentSleepStageType = analysis.predictionStageType)
                        }
                        onNotificationUpdate?.invoke("수면 단계: ${analysis.predictionStageType.name}")
                    }
                    .onFailure { Log.e("SleepTracker", "analyzeSleepData 실패", it) }
            }
        }
        measureManager.onEnvironmentReady = { envFeature ->
            Napier.d("envFeature 수신 = $envFeature")
            scope.launch {
                sleepSessionRepository.updateEnvironmentContext(envFeature)
                _trackingState.update { current ->
                    val newHistory = (current.environmentHistory + envFeature.snapshot).takeLast(60)
                    current.copy(
                        environmentHistory = newHistory,
                        avgTemperature = envFeature.snapshot.temperature,
                        avgHumidity = envFeature.snapshot.humidity,
                        isHeartRateAnomaly = envFeature.flag.isHeartRateAnomaly,
                        isNoiseDanger = envFeature.flag.isNoiseDanger,
                        isTempExtreme = envFeature.flag.isTempExtreme,
                        isHumidityExtreme = envFeature.flag.isHumidityExtreme
                    )
                }
            }
        }
    }
    private fun startElapsedTimeUpdater() {
        scope.launch {
            while (isActive && _trackingState.value.isTracking) {
                val startTime = _trackingState.value.trackingStartTime
                val now = Clock.System.now()
                val startInstant = startTime.toInstant(TimeZone.currentSystemDefault())
                val elapsed = (now - startInstant).inWholeSeconds.toInt()

                _trackingState.update { it.copy(elapsedSeconds = elapsed) }

                val hours = elapsed / 3600
                val minutes = (elapsed % 3600) / 60
                onNotificationUpdate?.invoke("측정 시간: ${hours}시간 ${minutes}분")
                delay(ELAPSED_UPDATE_INTERVAL)
            }
        }
    }

    override fun discard() {
        scope.launch {
            val sessionId = _trackingState.value.sessionId

            clear()
            if (sessionId.isNotEmpty()) {
                runCatching {
                    sleepSessionRepository.deleteSession(sessionId)
                    Napier.d("discard: 세션 삭제 완료 sessionId=$sessionId")
                }.onFailure {
                    Napier.e("discard: 세션 삭제 실패", it)
                }
            }
            _trackingState.value = TrackingContract.State()
        }
    }

    override fun finish() {
        scope.launch {
            _trackingState.update {
                it.copy(
                    isTracking = false,
                    trackingEndTime = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                )
            }
            stopSensors()

            val aggregates = measureManager.getCapturedAggregates()
            val environmentFeatures = measureManager.getCapturedEnvironmentFeatures()

            if (aggregates.size < MIN_MINUTES_REQUIRED) {
                Log.w("TrackingService", "수면 데이터 부족으로 분석 생략: ${aggregates.size}분 측정됨.")
                clear()
                return@launch
            }

            val sessionId = _trackingState.value.sessionId

            sensorBridge.stopHeartRateSensor()
            sensorBridge.stopNoiseSensor()
            heartRateMonitor.stopMonitoring()
            noiseDetector.stopMonitoring()

            csvExporter.exportEnvironmentData(features = environmentFeatures, fileName = "env_$sessionId.csv")

            withContext(Dispatchers.Default) {
                // 수면 세션 분석을 하이드레이션된 1분 데이터 셋 기반으로 변경하는 아키텍처 연계
                sleepSessionRepository.analyzeSleepSession(
                    sensorData = emptyList(), // 더 이상 무거운 원시 리스트 목록을 힙에 들고 전달하지 않음
                    timestamps = aggregates.map { it.timestampBucket },
                    environmentFeatures = environmentFeatures,
                    sessionId = sessionId
                )
            }.onSuccess { report ->
                sleepSessionRepository.insertSession(report)
                _trackingState.update {
                    it.copy(
                        isFinished = true,
                        finishedSessionId = report.sessionId,
                        sessionId = report.sessionId
                    )
                }
                onNotificationUpdate?.invoke("측정 종료! 분석 완료.")
                onRequestStopForeground?.invoke()
            }.onFailure { e ->
                Log.e("TrackingService", "분석 실패", e)
            }
        }
    }
    override fun updateEndTime(hour: Int, minute: Int) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val tz  = TimeZone.currentSystemDefault()

        var endTime = LocalDateTime(
            year       = now.year,
            month      = now.month,
            dayOfMonth = now.dayOfMonth,
            hour       = hour,
            minute     = minute,
            second     = 0,
            nanosecond = 0
        )

        if (endTime <= now) {
            endTime = endTime.toInstant(tz).plus(1.days).toLocalDateTime(tz)
        }

        val duration = (endTime.toInstant(tz) - now.toInstant(tz)).inWholeMinutes.toInt()

        _trackingState.update {
            it.copy(
                trackingEndTime   = endTime,
                duration          = duration
            )
        }
    }
    private fun stopSensors() {
        if (isCleanedUp) return
        isCleanedUp = true
        runCatching { measureManager.stop() }
        runCatching { heartRateMonitor.stopMonitoring() }
        runCatching { noiseDetector.stopMonitoring() }
        runCatching { musicPlayer.stop() }
    }
    fun clear() {
        stopSensors()
        scope.cancel()
    }
}