@file:OptIn(InternalVoyagerApi::class)

package com.sleepytime.shared.platform

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import com.russhwolf.settings.ExperimentalSettingsApi
import com.sleepytime.shared.data.tracking.SleepTrackingService
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepSession
import com.sleepytime.shared.domain.model.Stats
import com.sleepytime.shared.domain.repository.SleepSessionRepository
import com.sleepytime.shared.domain.repository.SleepSettingsRepository
import com.sleepytime.shared.ui.tracking.TrackingContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
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
    private val sleepSettingsRepository: SleepSettingsRepository,
    private val sensorBridge: SensorBridge,
    private val musicPlayer: MusicPlayer,
    private val csvExporter: CsvExporter,
    private val audioSystem: AudioSystem,
    private val activeSessionStore: ActiveSessionStore
) : TrackingManager {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _trackingState = MutableStateFlow(TrackingContract.State())
    override val trackingState: StateFlow<TrackingContract.State> = _trackingState.asStateFlow()

    private val _wakeAlarmEvent = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1
    )
    override val wakeAlarmEvent: SharedFlow<String> = _wakeAlarmEvent.asSharedFlow()


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

    override fun start(sessionId: String, durationMillis: Long, musicTitle: String?) {
        Log.d("AndroidTrackingManager","start()")
        val serviceIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_START
            putExtra(SleepTrackingService.EXTRA_SESSION_ID, sessionId)
            putExtra(SleepTrackingService.EXTRA_DURATION_MILLIS, durationMillis)
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
                if (musicPlayer.isPlaying) {
                    musicPlayer.setVolume(0.4f)
                    return@withContext
                }
                musicPlayer.setVolume(0.4f)
                musicPlayer.play(musicName = it, type = SoundType.SLEEP, startSeconds = 0)
            }
        }
    }
    private suspend fun stopMusicIfMatches(musicTitle: String?) {
        withContext(Dispatchers.Main) {
            musicPlayer.stop()
        }
        Log.d("AndroidTrackingManager", "음악 정지: $musicTitle")
    }
    private fun setupSensorCallbacks() {
        measureManager.onMinuteAggregateReady = { aggregate ->
            scope.launch {
                Log.d("SleepTracker", "1분 압축 데이터 수집됨: ${aggregate.timestampBucket}")
                _trackingState.update { current ->
                    current.copy(
                        avgNoise = aggregate.avgNoiseDb
                    )
                }
            }
        }
        measureManager.onWindowReady = { windowData ->
            scope.launch {
                Log.d("AndroidTrackingManager","onWindowReady called, isReady=${sleepSessionRepository.isReady()}")
                if (!sleepSessionRepository.isReady()) return@launch

                // 💡 해결: 히스토리가 비어있을 때를 대비해 SensorBridge에서 직접 최신 수치를 가져옴
                val latestNoise = sensorBridge.latestNoiseStats.last

                val currentEnv = EnvironmentFeature(
                    timestamp = System.currentTimeMillis(),
                    snapshot = EnvironmentFeature.Snapshot(noise = latestNoise),
                    stats = EnvironmentFeature.Statistics(
                        noise = Stats(
                            avg = latestNoise,
                            max = sensorBridge.latestNoiseStats.max
                        )
                    ),
                    flag = EnvironmentFeature.Flag(
                        isNoiseDanger = false
                    )
                )

                sleepSessionRepository.analyzeSleepData(sensorData = windowData, environmentFeature = currentEnv)
                    .onSuccess { analysis ->
                        Log.d("SleepTracker", "analyzeSleepData success, history size 증가")
                        Log.d("SleepTracker", "분석 결과: ${analysis.predictionStageType}")
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
                        isNoiseDanger = envFeature.flag.isNoiseDanger,
                    )
                }
            }
        }
    }
    private fun startAllSensors() {
        measureManager.start()
        sensorBridge.startNoiseSensor(scope)
        sensorBridge.startGyroscopeSensor(scope) // 👈 자이로스코프 데이터 수집 시작
    }
    private fun startSensorBridgeSync() {
        scope.launch {
            while (isActive && _trackingState.value.isTracking) {
                val noise = sensorBridge.latestNoiseStats.last
                val now = System.currentTimeMillis()
                if (noise > 0f) measureManager.submitNoise(noise, now)
                delay(1000L.milliseconds)
            }
        }
    }
    private fun startElapsedTimeUpdater() {
        scope.launch {
            while (isActive && _trackingState.value.isTracking) {
                val startTime = _trackingState.value.trackingStartTime
                val now = Clock.System.now()
                val nowMillis = now.toEpochMilliseconds()

                val startInstant = startTime.toInstant(TimeZone.currentSystemDefault())
                val elapsedMillis = (now - startInstant).inWholeMilliseconds.coerceAtLeast(0)

                _trackingState.update { it.copy(elapsedMillis = elapsedMillis) }

                val totalSeconds = elapsedMillis / 1000
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                onNotificationUpdate?.invoke("측정 시간: ${hours}시간 ${minutes}분")

                val endInstant = _trackingState.value.trackingEndTime
                    .toInstant(TimeZone.currentSystemDefault())
                val endMillis = endInstant.toEpochMilliseconds()

                if (!_trackingState.value.isAlarmTriggered && nowMillis >= endMillis) {
                    Log.d("AndroidTrackingManager", "기상 시간 도달! 알람 트리거 시작")
                    triggerWakeAlarm()
                }

                val delayMillis = 1000L - (elapsedMillis % 1000)
                delay(delayMillis.milliseconds)
            }
        }
    }
    private suspend fun triggerWakeAlarm() {
        Log.d("AndroidTrackingManager", "triggerWakeAlarm() - 기상 알람 트리거")

        _trackingState.update { it.copy(isAlarmTriggered = true) }

        val savedAlarm = sleepSettingsRepository.observeSettings().first()
        val alarmSound = savedAlarm.sound

        val appVolume = alarmSound.volume
        val systemVolume = audioSystem.getSystemAlarmVolume()
        val finalVolume = systemVolume * appVolume

        withContext(Dispatchers.Main) {
            musicPlayer.stop()
            musicPlayer.setVolume(finalVolume)
            musicPlayer.play(musicName = alarmSound.id, type = SoundType.ALARM)
        }

        val isVibrationRequired = savedAlarm.isVibrationEnabled || appVolume == 0f

        Log.d("AndroidTrackingManager", "진동 여부: isVibrationRequired=$isVibrationRequired (savedIsVibration=${savedAlarm.isVibrationEnabled}, appVolume=$appVolume)")

        if (isVibrationRequired) {
            triggerVibration()
        } else {
            stopVibration() // 확실한 진동 차단을 위해 수동 중지 호출
        }

        onNotificationUpdate?.invoke("기상 알람 울림")

        val sessionId = _trackingState.value.sessionId ?: ""
        _wakeAlarmEvent.emit(sessionId)
    }

    private fun triggerVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            val timings = longArrayOf(0, 500, 500, 500) // 대기, 진동, 대기, 진동 (ms)
            val amplitudes = intArrayOf(0, 255, 0, 255) // 강도 (0~255)
            
            val effect = VibrationEffect.createWaveform(timings, amplitudes, 0) // 0: 무한 반복
            vibrator.vibrate(effect)
        }
    }

    private fun stopVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.cancel()
    }
    override fun finish(sessionId: String, musicTitle: String?) {
        Log.d("AndroidTrackingManager", "finish()")
        val serviceIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_FINISH
            putExtra(SleepTrackingService.EXTRA_SESSION_ID, sessionId)
            putExtra(SleepTrackingService.EXTRA_MUSIC_TITLE, musicTitle)
        }
        context.startService(serviceIntent)
    }

    override fun discard(sessionId: String, musicTitle: String?) {
        val serviceIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_DISCARD
            putExtra(SleepTrackingService.EXTRA_SESSION_ID, sessionId)
            putExtra(SleepTrackingService.EXTRA_MUSIC_TITLE, musicTitle)
        }
        context.startService(serviceIntent)
    }
    fun performStart(sessionId: String, durationMillis: Long, musicTitle: String?) {
        Log.d("AndroidTrackingManager","performStart()")
        if (scope.coroutineContext[Job]?.isActive != true) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
        scope.launch {
            isCleanedUp = false
            _trackingState.update { it.copy(isFinished = false) }

            val tz = TimeZone.currentSystemDefault()
            val startTime = Clock.System.now()
            val endTime = startTime.plus(durationMillis.milliseconds).toLocalDateTime(tz)
            val sessionDate = startTime.toLocalDateTime(tz).date

            val session = createInitialSession(sessionId, sessionDate)
            sleepSessionRepository.insertSession(session)

            classifier.initialize(context)
            if (!initializeModel()) return@launch

            playMusic(musicTitle)
            setupSensorCallbacks()
            startAllSensors()
            startSensorBridgeSync()

            _trackingState.update {
                it.copy(
                    isTracking = true,
                    isAlarmTriggered = false,
                    trackingStartTime = startTime.toLocalDateTime(TimeZone.currentSystemDefault()),
                    trackingEndTime = endTime,
                    durationMillis = durationMillis,
                    sessionId = session.sessionId,
                    musicTitle = musicTitle
                )
            }
            activeSessionStore.save(
                sessionId = session.sessionId,
                startTimeMillis = startTime.toEpochMilliseconds(),
                duration = (durationMillis / 60000).toInt(), // Keeping Int minutes for store if needed, or update store
                musicTitle = musicTitle
            )
            onNotificationUpdate?.invoke("수면 측정 중..")
            startElapsedTimeUpdater()
        }
    }
    fun performDiscard(sessionId: String, musicTitle: String?) {
        scope.launch {
            stopMusicIfMatches(musicTitle)

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

            clear()
        }
    }
    fun performFinish(sessionId: String, musicTitle: String?) {
        scope.launch {
            _trackingState.update {
                it.copy(
                    isTracking = false,
                    isAlarmTriggered = false,
                    trackingEndTime = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                )
            }
            stopMusicIfMatches(musicTitle)
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
        val tz = TimeZone.currentSystemDefault()
        val nowInstant = Clock.System.now()
        val now = nowInstant.toLocalDateTime(tz)

        var endTime = LocalDateTime(
            year = now.year,
            month = now.month,
            dayOfMonth = now.dayOfMonth,
            hour = hour,
            minute = minute,
            second = 0,
            nanosecond = 0
        )

        // 이미 지난 시간이면 내일로 설정
        if (endTime.toInstant(tz) <= nowInstant) {
            endTime = endTime.toInstant(tz).plus(1L, DateTimeUnit.DAY, tz).toLocalDateTime(tz)
        }

        val durationMillis = (endTime.toInstant(tz) - nowInstant).inWholeMilliseconds

        _trackingState.update {
            it.copy(
                trackingEndTime = endTime,
                isAlarmTriggered = false,
                durationMillis = durationMillis
            )
        }
    }
    private fun stopSensors() {
        if (isCleanedUp) return
        isCleanedUp = true
        runCatching { measureManager.stop() }
        runCatching { sensorBridge.stopNoiseSensor() }
        runCatching { sensorBridge.stopGyroscopeSensor() } // 👈 자이로스코프 중단
        runCatching { musicPlayer.stop() }
        runCatching { stopVibration() }
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

    private fun createInitialSession(sessionId: String, date: LocalDate): SleepSession {
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
                    noise = Stats(),
                ),
                flags = EnvironmentFeature.Flag(
                    isNoiseDanger = false,
                ),
            ),
            duration = SleepSession.Duration(
                awakeMinutes = 0.0,
                lightMinutes = 0.0,
                deepMinutes = 0.0,
                remMinutes = 0.0,
                targetMinutes = 0.0,
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

            date = date,
            wakeCount = 0,
        )
    }
}