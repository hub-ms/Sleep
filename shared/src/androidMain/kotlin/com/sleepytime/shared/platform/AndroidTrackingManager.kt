package com.sleepytime.shared.platform

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.russhwolf.settings.ExperimentalSettingsApi
import com.sleepytime.shared.data.tracking.SleepTrackingService
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepSession
import com.sleepytime.shared.domain.model.Stats
import com.sleepytime.shared.domain.repository.SleepSessionRepository
import com.sleepytime.shared.ui.tracking.TrackingContract
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.time.ExperimentalTime


@UnstableApi
@ExperimentalMaterial3Api
@ExperimentalTime
@ExperimentalSettingsApi
@ExperimentalCoroutinesApi
class AndroidTrackingManager @Inject constructor(
    private val context: Context,
    private val classifier: SleepStageClassifier,
    private val measureManager: SleepMeasureManager,
    private val sleepSessionRepository: SleepSessionRepository,
    private val sensorBridge: SensorBridge,
    private val musicPlayer: MusicPlayer,
    private val csvExporter: CsvExporter,
    private val activeSessionStore: ActiveSessionStore
) : TrackingManager {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _trackingState = MutableStateFlow(TrackingContract.State())
    override val trackingState: StateFlow<TrackingContract.State> = _trackingState.asStateFlow()

    private var onNotificationUpdate: ((String) -> Unit)? = null
    private var onRequestStopForeground: (() -> Unit)? = null
   @Volatile private var isCleanedUp = false

    companion object {
        private const val MIN_TRACKING_MINUTES = 5
        private const val WINDOW_DURATION_SECONDS = 30

        private const val MIN_WINDOWS = (MIN_TRACKING_MINUTES * 60) / WINDOW_DURATION_SECONDS

        private const val ENV_SAMPLE_INTERVAL_SECONDS = 60
        private const val MIN_ENV_WINDOWS = (MIN_TRACKING_MINUTES * 60) / ENV_SAMPLE_INTERVAL_SECONDS

        private data class CaptureResult(
            val sensorData: List<List<FloatArray>>,
            val environmentFeatures: List<EnvironmentFeature>,
            val timestamps: List<Long>
        ) {
            fun isSufficient() = sensorData.size >= MIN_WINDOWS && environmentFeatures.size >= MIN_ENV_WINDOWS
        }
    }
    init {
        Log.d("AndroidTrackingManager", "인스턴스 생성됨, pid=${android.os.Process.myPid()}, hashCode=${this.hashCode()}")
    }

    override fun attachCallbacks(
        onNotificationUpdate: (String) -> Unit,
        onRequestStopForeground: () -> Unit
    ) {
        this.onNotificationUpdate = onNotificationUpdate
        this.onRequestStopForeground = onRequestStopForeground
    }

    override fun start(sessionId: String, duration: Int, musicTitle: String?) {
        Log.d("AndroidTrackingManager","start()")
        val serviceIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_START
            putExtra(SleepTrackingService.EXTRA_SESSION_ID, sessionId)
            putExtra(SleepTrackingService.EXTRA_DURATION, duration)
            putExtra(SleepTrackingService.EXTRA_MUSIC_TITLE, musicTitle)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
    private suspend fun initializeModel(): Boolean {
        val initResult = sleepSessionRepository.initializeModel()
        if (initResult.isFailure) {
            Log.e("TrackingService", "모델 초기화 실패", initResult.exceptionOrNull())
            clear()
            return false
        }
        return true
    }
    private suspend fun playMusic(musicTitle: String?) {
        musicTitle?.let {
            withContext(Dispatchers.Main) {
                musicPlayer.setVolume(0.4f)
                musicPlayer.play(it, startSeconds = 0)
            }
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
                Log.d("AndroidTrackingManager","onWindowReady called, isReady=${sleepSessionRepository.isReady()}")
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
            Log.d("AndroidTrackingManager","envFeature 수신 = $envFeature")
            scope.launch {
                sleepSessionRepository.updateEnvironmentContext(envFeature)
                _trackingState.update { current ->
                    val newHistory = (current.environmentHistory + envFeature.snapshot).takeLast(60)
                    current.copy(
                        environmentHistory = newHistory,
                        isHeartRateAnomaly = envFeature.flag.isHeartRateAnomaly,
                        isNoiseDanger = envFeature.flag.isNoiseDanger,
                    )
                }
            }
        }
    }
    private fun startAllSensors() {
        measureManager.start()
        sensorBridge.startHeartRateSensor(scope)
        sensorBridge.startNoiseSensor(scope)
    }
    private fun startSensorBridgeSync() {
        scope.launch {
            while (isActive && _trackingState.value.isTracking) {
                val hr = sensorBridge.latestHeartRateStats.last
                val noise = sensorBridge.latestNoiseStats.last
                val now = System.currentTimeMillis()
                if (hr > 0f) measureManager.submitHeartRate(hr, now)
                if (noise > 0f) measureManager.submitNoise(noise, now)
                delay(1000L)
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
            }
        }
    }

    override fun discard() {
        val serviceIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_DISCARD
        }
        context.startService(serviceIntent)
    }

    override fun finish() {
        Log.d("AndroidTrackingManager","finish()")
        val serviceIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_FINISH
        }
        context.startService(serviceIntent)
    }
    fun performStart(sessionId: String, duration: Int, musicTitle: String?) {
        Log.d("AndroidTrackingManager","performStart()")
        if (scope.coroutineContext[Job]?.isActive != true) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
        scope.launch {
            isCleanedUp = false
            _trackingState.update { it.copy(isFinished = false) }

            val session = createInitialSession(sessionId)
            sleepSessionRepository.insertSession(session)

            classifier.initialize(context)
            if (!initializeModel()) return@launch

            playMusic(musicTitle)
            setupSensorCallbacks()
            startAllSensors()
            startSensorBridgeSync()

            val startTime = Clock.System.now()
            _trackingState.update {
                it.copy(
                    isTracking = true,
                    trackingStartTime = startTime.toLocalDateTime(TimeZone.currentSystemDefault()),
                    sessionId = session.sessionId
                )
            }
            activeSessionStore.save(
                sessionId = session.sessionId,
                startTimeMillis = startTime.toEpochMilliseconds(),
                duration = duration,
                musicTitle = musicTitle
            )
            onNotificationUpdate?.invoke("수면 측정 중..")
            startElapsedTimeUpdater()
        }
    }
    fun performDiscard() {
        scope.launch {
            val sessionId = _trackingState.value.sessionId ?: return@launch

            clear()
            if (sessionId.isNotEmpty()) {
                runCatching {
                    sleepSessionRepository.deleteSession(sessionId)
                    Log.d("AndroidTrackingManager","discard: 세션 삭제 완료 sessionId=$sessionId")
                }.onFailure {
                    Log.e("AndroidTrackingManager","discard: 세션 삭제 실패", it)
                }
            }
            activeSessionStore.clear()
            _trackingState.value = TrackingContract.State()
        }
    }
    fun performFinish() {
        Log.d("AndroidTrackingManager","performFinish()")
        scope.launch {
            _trackingState.update {
                it.copy(
                    isTracking = false,
                    trackingEndTime = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                )
            }
            stopSensors()

            val capture = collectCapturedData()
            if(!capture.isSufficient()) {
                Log.w("TrackingService", "데이터 부족")
                onRequestStopForeground?.invoke()
                activeSessionStore.clear()
                return@launch
            }

            exportCsv(capture)
            analyzeAndSave(capture)
        }
    }


    private fun collectCapturedData() = CaptureResult(
        sensorData = measureManager.getCapturedSensorData(),
        environmentFeatures = measureManager.getCapturedEnvironmentFeatures(),
        timestamps = measureManager.getCapturedTimestamps()
    )
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
        runCatching { sensorBridge.stopHeartRateSensor() }
        runCatching { sensorBridge.stopNoiseSensor() }
        runCatching { musicPlayer.stop() }
    }
    private fun exportCsv(capture: CaptureResult) {
        val sessionId = _trackingState.value.sessionId
        val startTime = capture.timestamps.firstOrNull() ?: System.currentTimeMillis()

        csvExporter.exportSensorData(
            capture.sensorData,
            "sleep_$sessionId.csv",
            startTime
        )
        csvExporter.exportEnvironmentData(
            capture.environmentFeatures,
            "env_$sessionId.csv"
        )
    }
    private suspend fun analyzeAndSave(capture: CaptureResult) {
        Log.d("AndroidTrackingManager","analyzeAndSave()")
        val sessionId = _trackingState.value.sessionId ?: return

        withContext(Dispatchers.Default) {
            sleepSessionRepository.analyzeSleepSession(
                capture.timestamps,
                capture.environmentFeatures,
                sessionId
            )
        }.onSuccess { report ->
            sleepSessionRepository.insertSession(report)
            activeSessionStore.clear()
            _trackingState.update {
                it.copy(
                    isFinished = true,
                    finishedSessionId = report.sessionId,
                    sessionId = report.sessionId
                )
            }
            onNotificationUpdate?.invoke("측정 완료!")
            onRequestStopForeground?.invoke()
        }.onFailure { e ->
            Log.e("TrackingService", "분석 실패", e)
            onRequestStopForeground?.invoke()
        }
    }
    fun clear() {
        stopSensors()
        scope.cancel()
    }

    private fun createInitialSession(sessionId: String): SleepSession {
        val now = System.currentTimeMillis()
        return SleepSession(
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
                ),
                flags = EnvironmentFeature.Flag(
                    isHeartRateAnomaly = false,
                    isNoiseDanger = false,
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
    }
}