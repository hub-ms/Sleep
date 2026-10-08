@file:OptIn(InternalVoyagerApi::class)

package com.soundsleeper.app.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import com.soundsleeper.app.data.tracking.SleepTrackingService
import com.soundsleeper.app.domain.model.EnvironmentFeature
import com.soundsleeper.app.domain.model.SleepMinuteAggregate
import com.soundsleeper.app.domain.model.Stats
import com.soundsleeper.app.domain.repository.SleepSessionRepository
import com.soundsleeper.app.domain.repository.SleepSettingRepository
import com.soundsleeper.app.ui.tracking.TrackingContract
import com.soundsleeper.app.util.StatsUtil
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime


data class RawAccel(val values: FloatArray, val timestamp: Long) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawAccel

        if (timestamp != other.timestamp) return false
        if (!values.contentEquals(other.values)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }
}

data class RawNoise(val db: Float, val timestamp: Long)
data class RawGyro(val values: FloatArray, val timestamp: Long) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawGyro

        if (timestamp != other.timestamp) return false
        if (!values.contentEquals(other.values)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }
}

/**
 * 수면 측정의 모든 것 — 포그라운드 서비스 제어, 센서 수집·버킷 집계, 분석 저장, 기상 알람.
 *
 * 예전에는 센서 수집·집계 부분만 SleepMeasureManager 로 떼어 두고 이 클래스가 생성자로 주입받아
 * `onWindowReady` / `onEnvironmentReady` / `onMinuteAggregateReady` 세 개의 var 콜백으로 결과를
 * 돌려받았다. 그런데 그 인터페이스의 사용처가 처음부터 이 클래스 하나뿐이었고, 콜백을 해제하는
 * 코드가 없어서 측정을 멈춘 뒤에도 취소된 scope 를 붙든 람다가 남아 있었다. 한 클래스로 합쳐
 * 콜백 대신 직접 호출로 바꿨다.
 */
@UnstableApi
@ExperimentalMaterial3Api
@ExperimentalTime
@ExperimentalSettingsApi
@ExperimentalCoroutinesApi
class AndroidTrackingManager(
    private val context: Context,
    private val classifier: SleepStageClassifier,
    private val sleepSessionRepository: SleepSessionRepository,
    private val sleepSettingRepository: SleepSettingRepository,
    private val sensorBridge: SensorBridge,
    private val musicPlayer: MusicPlayer,
    private val audioSystem: AudioSystem,
    private val activeSessionStore: ActiveSessionStore,
    /**
     * 모델 파일이 없을 때 [SleepStageClassifier]의 mock 수면단계로 넘어가도 되는지.
     * AndroidModule이 AppConfig.isDebug를 넘기므로 **릴리즈 빌드에서는 false**이고,
     * 그 경우 모델이 없으면 지금까지처럼 측정을 거부한다(가짜 리포트가 사용자에게 나가지 않도록).
     */
    private val allowMockModel: Boolean
) : TrackingManager, SensorEventListener {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── 센서 수집 상태 (구 SleepMeasureManager) ────────────────────────────
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val accelQueue = ConcurrentLinkedDeque<RawAccel>()
    private val gyroQueue = ConcurrentLinkedDeque<RawGyro>()
    private val noiseQueue = ConcurrentLinkedDeque<RawNoise>()

    private val minuteAccel = mutableListOf<RawAccel>()
    private val minuteGyro = mutableListOf<RawGyro>()
    private val minuteNoise = mutableListOf<RawNoise>()
    private var lastBucketTimestamp: Long = 0

    private val capturedSensorWindows = CopyOnWriteArrayList<List<FloatArray>>()
    private val capturedAggregates = CopyOnWriteArrayList<SleepMinuteAggregate>()
    private val capturedEnvironmentFeatures = CopyOnWriteArrayList<EnvironmentFeature>()

    private val bufferMutex = Mutex()
    @Volatile private var isMeasuring = false

    // 💡 소음측정 정확도 개선: 세션마다 침실의 배경소음 수준이 다르므로, 고정 임계값 하나로
    // 모든 사용자를 판단하지 않고 세션 초반 몇 분을 "이 밤의 개인 배경소음 기준선"으로 삼아
    // 위험 판정 임계값을 개인화합니다. 기준선이 원래 임계값보다 낮을 때는 절대 임계값보다
    // 민감해지지 않도록 max()로 하한을 둡니다(회귀 방지).
    private var sessionAmbientBaselineDb: Float? = null
    private val calibrationBucketNoiseAvgs = mutableListOf<Float>()

    // 💡 소음측정 정확도 개선: 단발성 스파이크(알림음, 한 번의 기침 등)에 과민 반응하지 않도록
    // 연속된 버킷에서 임계값을 넘을 때만 "소음 위험"으로 판정합니다.
    private var consecutiveDangerBuckets = 0

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
   @Volatile private var isFinishing = false

    companion object {
        private const val MIN_TRACKING_MINUTES = 5
        private const val WINDOW_DURATION_SECONDS = 30

        private const val MIN_WINDOWS = (MIN_TRACKING_MINUTES * 60) / WINDOW_DURATION_SECONDS

        private const val ENV_SAMPLE_INTERVAL_SECONDS = 60

        private const val MOVEMENT_THRESHOLD_MS2 = 12.0f
        private const val DEFAULT_NOISE_DB = 30f
        private const val NOISE_DANGER_THRESHOLD = 60f
        private const val WINDOW_BUCKET_MS = 30_000L
        private const val CALIBRATION_BUCKET_COUNT = 4 // 첫 2분(4×30초)을 세션 배경소음 기준선으로 사용
        private const val DANGER_BASELINE_DELTA_DB = 30f // 개인 기준선 대비 이만큼 이상 올라야 위험으로 판단
        private const val MIN_CONSECUTIVE_DANGER_BUCKETS = 2 // 연속 2버킷(60초) 이상 지속되어야 위험으로 판단
        private const val MIN_ENV_WINDOWS = (MIN_TRACKING_MINUTES * 60) / ENV_SAMPLE_INTERVAL_SECONDS

        private data class CaptureResult(
            val sensorData: List<List<FloatArray>>,
            val environmentFeatures: List<EnvironmentFeature>,
            val timestamps: List<Long>
        ) {
            /**
             * 임계값을 상수에서 직접 읽지 않고 인자로 받는다 — mock 수면단계로 돌릴 때는
             * 테스트마다 5분을 기다리지 않도록 호출부가 낮춘 값을 넘긴다([performFinish] 참고).
             */
            fun isSufficient(minWindows: Int, minEnvWindows: Int) =
                sensorData.size >= minWindows && environmentFeatures.size >= minEnvWindows
        }

        // mock 수면단계로 돌릴 때의 완화된 임계값 — 1분(센서 2윈도우 + 환경 1윈도우)이면
        // "측정 종료 → 리포트 화면 이동"이 되는지 빠르게 확인할 수 있다. 단, 네 수면단계가
        // 모두 나오는 건 실제 5분 이상일 때다(SleepStageClassifier.MOCK_TIME_SCALE 참고).
        private const val MOCK_MIN_WINDOWS = 2
        private const val MOCK_MIN_ENV_WINDOWS = 1
    }
    init {
        Log.d("AndroidTrackingManager", "인스턴스 생성됨, pid=${android.os.Process.myPid()}, hashCode=${this.hashCode()}")
    }

    /** 서비스(SleepTrackingService.onCreate)가 알림 갱신/포그라운드 종료 요청 콜백을 등록한다. */
    override fun attachCallbacks(
        onNotificationUpdate: (String) -> Unit,
        onRequestStopForeground: () -> Unit
    ) {
        this.onNotificationUpdate = onNotificationUpdate
        this.onRequestStopForeground = onRequestStopForeground
    }

    /**
     * 측정 시작 진입점(TrackingViewModel이 호출). 실제 센서/분석은 여기서 하지 않고,
     * 마이크 권한만 미리 확인한 뒤 [SleepTrackingService]를 ACTION_START로 기동한다
     * (실제 시작 로직은 서비스가 받아 [performStart]를 호출하는 쪽에서 수행된다).
     */
    override fun start(sessionId: String, durationMillis: Long, musicName: String?) {
        Log.d("AndroidTrackingManager","start()")
        _trackingState.update { it.copy(permissionDenied = false) }

        // 서비스가 microphone 타입 포그라운드로 선언되어 있어(AndroidManifest.xml), RECORD_AUDIO 권한 없이는
        // startForeground()를 호출할 수 없다. 권한이 없는 상태에서 startForegroundService()만 호출하고
        // 서비스가 startForeground()를 호출하지 못하면 시스템이 ForegroundServiceDidNotStartInTimeException으로
        // 앱을 강제 종료하므로, 서비스를 아예 시작하지 않고 여기서 미리 걸러낸다.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("AndroidTrackingManager", "RECORD_AUDIO 권한이 없어 수면 측정을 시작할 수 없습니다")
            _trackingState.update { it.copy(permissionDenied = true) }
            return
        }

        val serviceIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_START
            putExtra(SleepTrackingService.EXTRA_SESSION_ID, sessionId)
            putExtra(SleepTrackingService.EXTRA_DURATION_MILLIS, durationMillis)
            putExtra(SleepTrackingService.EXTRA_MUSIC_NAME, musicName)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    // RECORD_AUDIO 권한이 없어 SleepTrackingService가 포그라운드 서비스를 시작하지 못했을 때 호출된다.
    // startForeground()를 마이크 타입으로 호출하려면 권한이 필수이므로, 권한이 없으면 크래시 대신
    // 이 경로로 안전하게 종료하고 State를 통해 UI에 알린다.
    fun notifyPermissionDenied() {
        Log.e("AndroidTrackingManager", "RECORD_AUDIO 권한이 없어 수면 측정을 시작할 수 없습니다")
        _trackingState.update { it.copy(permissionDenied = true) }
        onRequestStopForeground?.invoke()
    }

    /** 수면 단계 분류 모델(TFLite)을 초기화한다. 실패하면 측정을 정리하고 서비스 종료를 요청한다. */
    private suspend fun initializeModel(): Boolean {
        val initResult = sleepSessionRepository.initializeModel()
        if (initResult.isFailure) {
            Log.e("TrackingService", "모델 초기화 실패", initResult.exceptionOrNull())
            clear()
            // 포그라운드 알림이 "수면 측정 중.."에 영구히 머무르지 않도록 정리 콜백을 호출한다.
            onRequestStopForeground?.invoke()
            return false
        }
        return true
    }
    /** 선택된 수면음악이 있으면 재생한다(이미 재생 중이면 볼륨만 맞추고 재시작하지 않는다). */
    private suspend fun playMusic(musicName: String?) {
        musicName?.let {
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
    /** 재생 중인 수면음악을 정지한다(측정 종료/취소 시 호출). */
    private suspend fun stopMusicIfMatches(musicName: String?) {
        withContext(Dispatchers.Main) {
            musicPlayer.stop()
        }
        Log.d("AndroidTrackingManager", "음악 정지: $musicName")
    }

    /** 1분(버킷) 단위로 집계된 평균 소음을 state에 반영해 화면에 노출한다. */
    private fun handleMinuteAggregate(aggregate: SleepMinuteAggregate) {
        scope.launch {
            Log.d("SleepTracker", "1분 압축 데이터 수집됨: ${aggregate.timestampBucket}")
            _trackingState.update { current ->
                current.copy(
                    avgNoise = aggregate.avgNoiseDb
                )
            }
        }
    }

    /**
     * 30초 윈도우 하나가 다 채워질 때마다 호출되는 실시간 분석 핸드오프 지점.
     * 7채널 센서 데이터를 [sleepSessionRepository.analyzeSleepData]에 넘겨 수면 단계를
     * 추론하고, 성공하면 현재 수면 단계를 state/알림에 반영한다(측정 화면의 실시간 표시 반영 지점).
     */
    private fun handleWindow(windowData: List<FloatArray>) {
        scope.launch {
            Log.d("AndroidTrackingManager","handleWindow called, isReady=${sleepSessionRepository.isReady()}")
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

            // 신규(Phase 8): 같은 30초 구간에 완성된 오디오 에포크가 있으면 함께 넘긴다.
            // consumeLatestAudioEpoch()는 한 번 꺼내면 비워지므로, 여기서 정확히 한 번만 호출한다.
            val audioEpoch = sensorBridge.consumeLatestAudioEpoch()

            sleepSessionRepository.analyzeSleepData(
                sensorData = windowData,
                environmentFeature = currentEnv,
                rawAudioEpochSamples = audioEpoch,
            )
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

    /** 버킷마다 만들어진 환경(소음) 정보를 저장소에 반영하고, 최근 60개까지 history로 유지한다. */
    private fun handleEnvironment(envFeature: EnvironmentFeature) {
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

    /** 가속도/자이로(startMeasuring) + 소음(sensorBridge) 센서를 모두 시작한다. [performStart]에서 호출. */
    private fun startAllSensors() {
        startMeasuring()
        sensorBridge.startNoiseSensor(scope)
        sensorBridge.startGyroscopeSensor(scope) // 👈 자이로스코프 데이터 수집 시작
    }

    /**
     * SensorBridge가 들고 있는 최신 소음(dB) 값을 1초마다 읽어와 [submitNoise]로
     * 이 클래스의 accel/gyro 윈도우 집계 파이프라인에 합류시킨다.
     * isTracking이 true로 바뀐 뒤에 시작해야 한다(아래 performStart의 버그 수정 주석 참고).
     */
    private fun startSensorBridgeSync() {
        scope.launch {
            while (isActive && _trackingState.value.isTracking) {
                val noise = sensorBridge.latestNoiseStats.last
                val now = System.currentTimeMillis()
                if (noise > 0f) submitNoise(noise, now)
                delay(1000L.milliseconds)
            }
        }
    }

    /** 1초마다 경과 시간을 갱신하고 알림 문구를 바꾸며, 기상 시각에 도달하면 [triggerWakeAlarm]을 호출한다. */
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
    /**
     * 설정된 기상 시각이 되었을 때 호출된다: 알람 사운드를 재생하고(필요 시 진동),
     * state의 isAlarmTriggered를 세우고 [wakeAlarmEvent]를 흘려보내 기상 화면으로 이동시킨다.
     * 측정 자체는 멈추지 않는다 — 사용자가 "알람 끄기"를 눌러야 [performFinish]로 이어진다.
     */
    private suspend fun triggerWakeAlarm() {
        Log.d("AndroidTrackingManager", "triggerWakeAlarm() - 기상 알람 트리거")

        _trackingState.update { it.copy(isAlarmTriggered = true) }

        val savedSleepSetting = sleepSettingRepository.observeSettings().first()
        val alarmSound = savedSleepSetting.alarm.sound

        val appVolume = alarmSound.volume
        val systemVolume = audioSystem.getSystemAlarmVolume()
        val finalVolume = systemVolume * appVolume

        withContext(Dispatchers.Main) {
            musicPlayer.stop()
            musicPlayer.setVolume(finalVolume)
            musicPlayer.play(musicName = alarmSound.id, type = SoundType.ALARM)
        }

        val isVibrationRequired = savedSleepSetting.alarm.isVibrationEnabled || appVolume == 0f

        Log.d("AndroidTrackingManager", "진동 여부: isVibrationRequired=$isVibrationRequired (savedIsVibration=${savedSleepSetting.alarm.isVibrationEnabled}, appVolume=$appVolume)")

        if (isVibrationRequired) {
            triggerVibration()
        } else {
            stopVibration() // 확실한 진동 차단을 위해 수동 중지 호출
        }

        onNotificationUpdate?.invoke("기상 알람 울림")

        val sessionId = _trackingState.value.sessionId ?: ""
        _wakeAlarmEvent.emit(sessionId)
    }

    /** 기상 알람용 무한 반복 진동 패턴(대기-진동-대기-진동)을 시작한다. */
    private fun triggerVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            val timings = longArrayOf(0, 500, 500, 500) // 대기, 진동, 대기, 진동 (ms)
            val amplitudes = intArrayOf(0, 255, 0, 255) // 강도 (0~255)
            
            val effect = VibrationEffect.createWaveform(timings, amplitudes, 0) // 0: 무한 반복
            vibrator.vibrate(effect)
        }
    }

    /** 진행 중인 진동을 멈춘다(진동이 필요 없는 알람 설정일 때 확실히 끄기 위해 명시적으로 호출). */
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

    /** 측정 종료 요청(TrackingViewModel.FinishTracking) — 서비스에 ACTION_FINISH를 보내 [performFinish]를 트리거한다. */
    override fun finish(sessionId: String, musicName: String?) {
        Log.d("AndroidTrackingManager", "finish()")
        val serviceIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_FINISH
            putExtra(SleepTrackingService.EXTRA_SESSION_ID, sessionId)
            putExtra(SleepTrackingService.EXTRA_MUSIC_NAME, musicName)
        }
        context.startService(serviceIntent)
    }

    /** 측정 취소 요청(TrackingViewModel.DiscardTracking) — 서비스에 ACTION_DISCARD를 보내 [performDiscard]를 트리거한다. */
    override fun discard(sessionId: String, musicName: String?) {
        val serviceIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_DISCARD
            putExtra(SleepTrackingService.EXTRA_SESSION_ID, sessionId)
            putExtra(SleepTrackingService.EXTRA_MUSIC_NAME, musicName)
        }
        context.startService(serviceIntent)
    }

    /**
     * 서비스(SleepTrackingService.onStartCommand, ACTION_START) 쪽에서 실제로 측정을 시작시키는 함수.
     * 모델 초기화 → 음악 재생 → 센서 시작 → state 갱신 → 경과시간 타이머 시작까지,
     * 측정 사이클의 "시작" 단계에서 일어나는 모든 일이 여기 모여 있다.
     */
    fun performStart(sessionId: String, durationMillis: Long, musicName: String?) {
        Log.d("AndroidTrackingManager","performStart()")
        isFinishing = false
        if (scope.coroutineContext[Job]?.isActive != true) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
        scope.launch {
            isCleanedUp = false
            _trackingState.update { it.copy(isFinished = false) }

            val tz = TimeZone.currentSystemDefault()
            val startTime = Clock.System.now()
            val endTime = startTime.plus(durationMillis.milliseconds).toLocalDateTime(tz)

            // 🐛 버그 수정 (리포트 화면 값이 모두 0으로 표시되던 문제):
            // 예전에는 여기서 모든 필드가 0인 플레이스홀더 SleepSession을 곧바로 DB(SleepSessionEntity)에
            // 저장했습니다. 정상적으로 측정을 마치면 analyzeAndSave()가 같은 sessionId로 실제 데이터를
            // INSERT OR REPLACE해 덮어쓰지만, 앱 강제종료/크래시/OS의 백그라운드 서비스 종료 등으로
            // finish()나 discard() 없이 측정이 중단되면 이 0값 플레이스홀더가 DB에 영구히 남았습니다.
            // 리포트/홈 화면은 getLatestSession()(createdAt 내림차순 LIMIT 1)으로 최신 세션을 가져오는데,
            // 이 플레이스홀더가 실제 완료된 세션보다 최근이면 그대로 "모든 값이 0인 리포트"로 노출됐습니다.
            // 활성 세션 추적은 이미 아래 activeSessionStore가 전담하므로, 완료된 세션만 저장되는
            // SleepSessionEntity 테이블에는 실제 분석 결과(analyzeAndSave)가 나올 때만 기록합니다.

            classifier.initialize(context, allowMock = allowMockModel)
            if (!initializeModel()) return@launch

            playMusic(musicName)
            startAllSensors()

            _trackingState.update {
                it.copy(
                    isTracking = true,
                    isAlarmTriggered = false,
                    trackingStartTime = startTime.toLocalDateTime(TimeZone.currentSystemDefault()),
                    trackingEndTime = endTime,
                    durationMillis = durationMillis,
                    sessionId = sessionId,
                    musicName = musicName
                )
            }
            // 🐛 버그 수정: startSensorBridgeSync()의 루프는 `while (isActive && isTracking)` 인데
            // 예전에는 바로 위 상태 갱신보다 먼저 시작돼서 첫 조건 검사에서 isTracking=false 를 보고
            // 즉시 빠져나갔다. 그 결과 마이크 소음이 측정 버퍼로 한 번도 들어가지 않았다.
            // isTracking 이 true 가 된 뒤에 시작한다.
            startSensorBridgeSync()

            activeSessionStore.save(
                sessionId = sessionId,
                startTimeMillis = startTime.toEpochMilliseconds(),
                duration = (durationMillis / 60000).toInt(), // Keeping Int minutes for store if needed, or update store
                musicName = musicName
            )
            onNotificationUpdate?.invoke("수면 측정 중..")
            startElapsedTimeUpdater()
        }
    }
    /** 서비스(ACTION_DISCARD) 쪽에서 실제로 측정을 취소하는 함수 — 음악을 멈추고 DB에서 세션을 지우고 전체를 정리한다. */
    fun performDiscard(sessionId: String, musicName: String?) {
        scope.launch {
            stopMusicIfMatches(musicName)

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
    /**
     * 서비스(ACTION_FINISH) 쪽에서 실제로 측정을 끝내는 함수 — 센서를 멈추고, 수집된 데이터가
     * 충분한지 확인한 뒤([CaptureResult.isSufficient]) 충분하면 [analyzeAndSave]로 분석·저장까지
     * 진행한다. 데이터가 부족하면 분석 없이 바로 isFinished를 세워 화면이 즉시 넘어가게 한다.
     */
    fun performFinish(sessionId: String, musicName: String?) {
        // 🐛 버그 수정: TrackingViewModel.FinishTracking, AlarmViewModel.StopAlarm, 알림의
        // ACTION_FINISH 세 경로 모두 이 함수를 부를 수 있는데 재진입 가드가 없었다. 거의 동시에
        // 두 번 불리면 두 번째 analyzeSleepSession() 이 첫 번째가 이미 비운
        // predictionHistoryManager 를 읽어 "분석 데이터 없음"으로 실패하고, 그 결과 세션이
        // DB에 저장되지 않는데도 리포트 화면은 그 sessionId 로 넘어가 10초를 허비했다.
        if (isFinishing) {
            Log.w("AndroidTrackingManager", "performFinish() 중복 호출 무시")
            return
        }
        isFinishing = true
        // performStart 와 같은 되살리기. scope 가 취소된 상태(clear() 이후)면 launch 가
        // 아무 일도 하지 않고 끝나서, 저장도 isFinished 설정도 전부 조용히 사라졌다.
        if (scope.coroutineContext[Job]?.isActive != true) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
        scope.launch {
            _trackingState.update {
                it.copy(
                    isTracking = false,
                    isAlarmTriggered = false,
                    trackingEndTime = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                )
            }
            stopMusicIfMatches(musicName)
            stopSensors()

            val capture = collectCapturedData()
            // mock 여부는 allowMockModel(= isDebug)이 아니라 분류기의 실제 상태로 판단한다 —
            // mock은 "디버그 빌드 **그리고** 모델 없음"일 때만 켜지므로, 디버그 빌드에 실제
            // 모델을 넣어 둔 경우 임계값이 실수로 낮아져선 안 된다.
            val isMock = classifier.isMockMode()
            val minWindows = if (isMock) MOCK_MIN_WINDOWS else MIN_WINDOWS
            val minEnvWindows = if (isMock) MOCK_MIN_ENV_WINDOWS else MIN_ENV_WINDOWS
            if(!capture.isSufficient(minWindows, minEnvWindows)) {
                Log.w(
                    "TrackingService",
                    "데이터 부족: 센서 ${capture.sensorData.size}/$minWindows, " +
                        "환경 ${capture.environmentFeatures.size}/$minEnvWindows (mock=$isMock)"
                )
                onRequestStopForeground?.invoke()
                activeSessionStore.clear()
                // isFinished 를 세우지 않으면 TrackingViewModel 이 10초 타임아웃을 전부 기다린
                // 뒤에야 화면을 넘긴다. 저장될 세션이 없다는 건 이미 확정이므로 바로 알린다.
                _trackingState.update { it.copy(isFinished = true, finishedSessionId = null) }
                return@launch
            }

            analyzeAndSave(capture)
        }
    }


    /** 측정 동안 쌓아 둔 30초 윈도우/버킷 집계/환경 데이터를 하나의 스냅샷([CaptureResult])으로 모은다. */
    private fun collectCapturedData() = CaptureResult(
        sensorData = capturedSensorWindows.toList(),
        environmentFeatures = capturedEnvironmentFeatures.toList(),
        timestamps = capturedAggregates.map { it.timestampBucket }
    )

    /** 측정 중 사용자가 알람(기상) 시각을 바꿨을 때 trackingEndTime/durationMillis를 재계산해 반영한다. */
    override fun updateEndTime(hour: Int, minute: Int) {
        val tz = TimeZone.currentSystemDefault()
        val nowInstant = Clock.System.now()
        val now = nowInstant.toLocalDateTime(tz)

        var endTime = LocalDateTime(
            year = now.year,
            month = now.month,
            day = now.day,
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
    /** 가속도/자이로/소음 센서와 음악/진동을 모두 멈춘다. 멱등(idempotent) — 두 번 불려도 안전하다. */
    private fun stopSensors() {
        if (isCleanedUp) return
        isCleanedUp = true
        runCatching { stopMeasuring() }
        runCatching { sensorBridge.stopNoiseSensor() }
        runCatching { sensorBridge.stopGyroscopeSensor() } // 👈 자이로스코프 중단
        runCatching { musicPlayer.stop() }
        runCatching { stopVibration() }
    }

    /**
     * 측정이 끝난 뒤 수집된 전체 데이터를 [SleepSessionRepository.analyzeSleepSession]에 넘겨
     * 최종 수면 리포트를 만들고 DB에 저장한다. 이 함수가 성공적으로 끝나야 리포트 화면에 보여줄
     * 세션이 실제로 존재하게 된다 — 측정 사이클에서 "저장"이 일어나는 지점.
     */
    private suspend fun analyzeAndSave(capture: CaptureResult) {
        Log.d("AndroidTrackingManager","analyzeAndSave()")
        val sessionId = _trackingState.value.sessionId ?: run {
            // 여기서 그냥 빠져나가면 isFinished 가 끝내 서지 않아, 화면은 10초 타임아웃을
            // 모두 기다린 뒤 존재하지 않는 세션의 리포트로 넘어간다.
            Log.e("TrackingService", "sessionId 가 없어 분석을 건너뜁니다")
            _trackingState.update { it.copy(isFinished = true, finishedSessionId = null) }
            return
        }

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
            // 🐛 버그 수정: 분석이 실패해도 isFinished를 설정하지 않아, TrackingScreen의 타이머는
            // 이미 멈췄는데(isTracking=false는 위에서 이미 설정됨) 리포트 화면으로는 영원히
            // 넘어가지 못하고 멈춰 있는 상태가 됐습니다. 실패해도 isFinished는 true로 설정해
            // TrackingViewModel의 리포트 화면 이동 흐름이 계속 진행되도록 합니다.
            //
            // 🐛 버그 수정: finishedSessionId 에 실패한 sessionId를 그대로 넣어 넘겼더니, DB에는
            // 끝내 저장되지 않은 세션을 리포트 화면이 10초 동안 재시도하다 최신 세션으로
            // 대체하는 낭비가 생겼다. 바로 위 "데이터 부족" 분기와 같이 null로 둬서, 저장된
            // 세션이 없다는 사실을 화면이 곧바로 알 수 있게 한다.
            activeSessionStore.clear()
            _trackingState.update {
                it.copy(isFinished = true, finishedSessionId = null)
            }
            onRequestStopForeground?.invoke()
        }
    }
    /** 전체 정리: 센서를 멈추고 이 매니저의 코루틴 스코프를 취소한다(취소/모델초기화실패/정상종료 공통 경로). */
    fun clear() {
        stopSensors()
        scope.cancel()
    }

    // ────────────────────────────────────────────────────────────────────────
    // 센서 수집·버킷 집계 (구 AndroidSleepMeasureManager)
    // ────────────────────────────────────────────────────────────────────────

    /** 가속도/자이로 센서 원본 이벤트를 받는 콜백. 측정 중일 때만 각자의 큐에 타임스탬프와 함께 쌓아 둔다. */
    override fun onSensorChanged(event: SensorEvent?) {
        if (!isMeasuring || event == null) return
        val now = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelQueue.addLast(RawAccel(event.values.clone(), now))
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroQueue.addLast(RawGyro(event.values.clone(), now))
            }
        }
    }

    /** 센서 정확도 변화는 이 앱에서 쓰지 않으므로 아무 것도 하지 않는다(SensorEventListener 인터페이스 요구사항). */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * 이름이 startMeasuring/stopMeasuring 인 이유: TrackingManager 인터페이스의 start(sessionId, …)와
     * 이름이 겹치면 "센서 등록"과 "포그라운드 서비스 시작"이라는 전혀 다른 두 동작이 오버로드로
     * 나란히 서게 된다.
     */
    private fun startMeasuring() {
        if (isMeasuring) return
        isMeasuring = true

        scope.launch {
            clearAllBuffers()
            lastBucketTimestamp = (System.currentTimeMillis() / WINDOW_BUCKET_MS) * WINDOW_BUCKET_MS
            registerSensorListeners()
            scheduleWindowUpdate()
        }
    }

    /** 메인 스레드에서 가속도계/자이로스코프 리스너(this)를 SENSOR_DELAY_GAME 주기로 등록한다. */
    private suspend fun registerSensorListeners() {
        withContext(Dispatchers.Main) {
            accelerometer?.let {
                sensorManager.registerListener(this@AndroidTrackingManager, it, SensorManager.SENSOR_DELAY_GAME)
            }
            gyroscope?.let {
                sensorManager.registerListener(this@AndroidTrackingManager, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    /**
     * 예전에는 전용 measureScope 를 cancel 해서 집계 루프를 멈췄지만, 이제 측정 코루틴도 이 클래스의
     * scope 위에서 돈다. scope 는 stopSensors() 이후에도 분석·저장에 계속 쓰이므로 여기서 취소하지
     * 않고, 루프의 `while (isMeasuring)` 조건으로 빠져나가게 한다.
     */
    private fun stopMeasuring() {
        isMeasuring = false
        sensorManager.unregisterListener(this)
    }

    /** startSensorBridgeSync가 읽어온 소음 값을 가속도/자이로와 같은 윈도우 집계 큐에 합류시킨다. */
    private fun submitNoise(db: Float, timestamp: Long) {
        if (!isMeasuring) return
        noiseQueue.addLast(RawNoise(db, timestamp))
    }

    /** 새 측정을 시작하기 전, 이전 세션에서 남았을 수 있는 모든 버퍼/캘리브레이션 상태를 초기화한다. */
    private suspend fun clearAllBuffers() {
        bufferMutex.withLock {
            accelQueue.clear()
            gyroQueue.clear()
            noiseQueue.clear()

            minuteAccel.clear()
            minuteGyro.clear()
            minuteNoise.clear()

            capturedSensorWindows.clear()
            capturedAggregates.clear()
            capturedEnvironmentFeatures.clear()

            sessionAmbientBaselineDb = null
            calibrationBucketNoiseAvgs.clear()
            consecutiveDangerBuckets = 0
        }
    }

    /**
     * 500ms마다 큐에 쌓인 원시 센서 샘플을 분(버킷) 단위 버퍼로 옮기고, 30초 버킷이
     * 넘어갈 때마다 [processMinuteAggregate]를 호출해 그 구간을 하나의 분석 단위로 확정한다.
     */
    private fun scheduleWindowUpdate() {
        scope.launch {
            while (isMeasuring) {
                delay(500.milliseconds) // 배터리 절약을 위한 500ms 폴링 주기

                val now = System.currentTimeMillis()
                val currentBucket = (now / WINDOW_BUCKET_MS) * WINDOW_BUCKET_MS

                // 1. Thread-safe 큐에서 임시로 데이터를 안전하게 먼저 꺼냄 (락 범위 최소화)
                val newAccels = accelQueue.pollAll()
                val newGyros = gyroQueue.pollAll()
                val newNoises = noiseQueue.pollAll()

                bufferMutex.withLock {
                    minuteAccel.addAll(newAccels)
                    minuteGyro.addAll(newGyros)
                    minuteNoise.addAll(newNoises)

                    if (currentBucket > lastBucketTimestamp) {
                        processMinuteAggregate(lastBucketTimestamp)
                        lastBucketTimestamp = currentBucket
                    }
                }
            }
        }
    }

    /**
     * 한 버킷(30초) 분량의 원시 샘플을 모델 입력용 7채널 윈도우로 합치고, 움직임/소음 통계를
     * 계산해 실시간 분석([handleWindow]), 분 집계([handleMinuteAggregate]), 환경 정보
     * ([handleEnvironment])로 각각 전달한다. 이 함수가 끝나면 다음 버킷을 위해 버퍼를 비운다.
     */
    private fun processMinuteAggregate(bucketTimestamp: Long) {
        if (minuteAccel.isEmpty() && minuteNoise.isEmpty()) return

        // 💡 1주차 로드맵: 가속도계 + 자이로스코프 RAW 데이터 로깅 (7채널, 심박수 제거)
        // 버킷의 마지막 소음값을 일괄 태깅하지 않고, 각 accel 샘플과 시간상 가장 가까운
        // 소음 샘플을 매칭합니다(accel/noise 모두 타임스탬프 오름차순이므로 two-pointer로 O(n+m)).
        var noiseIdx = 0
        val windowData = minuteAccel.mapIndexed { index, accel ->
            val gyro = minuteGyro.getOrNull(index) ?: RawGyro(floatArrayOf(0f, 0f, 0f), accel.timestamp)

            while (noiseIdx < minuteNoise.size - 1 &&
                abs(minuteNoise[noiseIdx + 1].timestamp - accel.timestamp) <=
                abs(minuteNoise[noiseIdx].timestamp - accel.timestamp)
            ) {
                noiseIdx++
            }
            val noise = minuteNoise.getOrNull(noiseIdx)?.db ?: DEFAULT_NOISE_DB

            floatArrayOf(
                accel.values[0], accel.values[1], accel.values[2], // 0, 1, 2: Accel XYZ
                gyro.values[0], gyro.values[1], gyro.values[2],    // 3, 4, 5: Gyro XYZ
                noise                                              // 6: Noise
            )
        }

        if (windowData.isNotEmpty()) {
            capturedSensorWindows.add(windowData)
            handleWindow(windowData)
        }

        val noiseStats = StatsUtil.computeStats(minuteNoise.map { it.db })
        val movementCount = countMovements(minuteAccel)

        val aggregate = SleepMinuteAggregate(
            timestampBucket = bucketTimestamp,
            avgNoiseDb = noiseStats.avg.ifEmpty(noiseStats.count, DEFAULT_NOISE_DB),
            maxNoiseDb = noiseStats.max.ifEmpty(noiseStats.count, DEFAULT_NOISE_DB),
            minNoiseDb = noiseStats.min.ifEmpty(noiseStats.count, DEFAULT_NOISE_DB),
            movementCount = movementCount,
        )

        capturedAggregates.add(aggregate)
        handleMinuteAggregate(aggregate)

        val envFeature = createEnvironmentFeature(bucketTimestamp, noiseStats)
        capturedEnvironmentFeatures.add(envFeature)
        handleEnvironment(envFeature)

        minuteAccel.clear()
        minuteGyro.clear()
        minuteNoise.clear()
    }

    /** 이 버킷에서 가속도 벡터 크기가 임계값([MOVEMENT_THRESHOLD_MS2])을 넘은 샘플 수를 센다(움직임 횟수). */
    private fun countMovements(accel: List<RawAccel>): Int = accel.count {
        val force = sqrt(it.values[0] * it.values[0] + it.values[1] * it.values[1] + it.values[2] * it.values[2])
        force > MOVEMENT_THRESHOLD_MS2
    }

    /** 이 버킷의 소음 통계로 [EnvironmentFeature]를 만든다(위험 여부는 [resolveNoiseDanger]가 판정). */
    private fun createEnvironmentFeature(
        timestamp: Long,
        noiseStats: StatsUtil.RollingStats,
    ): EnvironmentFeature {
        return EnvironmentFeature(
            timestamp = timestamp,
            snapshot = EnvironmentFeature.Snapshot(
                noise = noiseStats.last,
            ),
            stats = EnvironmentFeature.Statistics(
                noise = Stats(noiseStats.avg, noiseStats.std, noiseStats.min, noiseStats.max),
            ),
            flag = EnvironmentFeature.Flag(
                isNoiseDanger = resolveNoiseDanger(noiseStats),
            )
        )
    }

    /**
     * 이번 버킷이 "소음 위험"인지 판정합니다.
     * 1) 세션 초반 [CALIBRATION_BUCKET_COUNT]개 버킷 평균으로 이 밤의 개인 배경소음 기준선을
     *    추정하고, 절대 임계값([NOISE_DANGER_THRESHOLD])보다 낮아지지는 않게 하한을 둔 채
     *    기준선 대비 상대적으로 판단합니다.
     * 2) 연속 [MIN_CONSECUTIVE_DANGER_BUCKETS]개 버킷 이상 임계값을 넘어야 위험으로 확정해
     *    단발성 스파이크에 의한 오탐을 줄입니다.
     */
    private fun resolveNoiseDanger(noiseStats: StatsUtil.RollingStats): Boolean {
        if (noiseStats.count == 0) {
            consecutiveDangerBuckets = 0
            return false
        }

        if (sessionAmbientBaselineDb == null) {
            calibrationBucketNoiseAvgs.add(noiseStats.avg)
            if (calibrationBucketNoiseAvgs.size >= CALIBRATION_BUCKET_COUNT) {
                sessionAmbientBaselineDb = calibrationBucketNoiseAvgs.average().toFloat()
            }
        }

        val effectiveThreshold = sessionAmbientBaselineDb?.let { baseline ->
            maxOf(NOISE_DANGER_THRESHOLD, baseline + DANGER_BASELINE_DELTA_DB)
        } ?: NOISE_DANGER_THRESHOLD

        consecutiveDangerBuckets = if (noiseStats.avg > effectiveThreshold) {
            consecutiveDangerBuckets + 1
        } else {
            0
        }
        return consecutiveDangerBuckets >= MIN_CONSECUTIVE_DANGER_BUCKETS
    }

    /** 동시성 큐에 쌓인 모든 항목을 한 번에 꺼내 리스트로 비운다(락 없이 최소한의 시간만 점유). */
    private fun <T> ConcurrentLinkedDeque<T & Any>.pollAll(): List<T> {
        val result = mutableListOf<T>()
        var item = this.poll()
        while (item != null) {
            result.add(item)
            item = this.poll()
        }
        return result
    }

    /** count가 0(샘플이 전혀 없었음)이면 default 값으로, 아니면 원래 값을 그대로 쓴다. */
    private fun Float.ifEmpty(count: Int, default: Float): Float = if (count == 0) default else this
}