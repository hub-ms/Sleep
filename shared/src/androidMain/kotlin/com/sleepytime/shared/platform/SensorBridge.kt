// androidMain/src/androidMain/kotlin/com/sleepytime/shared/platform/SensorBridge.kt
package com.sleepytime.shared.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.sleepytime.shared.util.CircularFloatBuffer
import com.sleepytime.shared.util.StatsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.*



@OptIn(ExperimentalAtomicApi::class)
actual class SensorBridge(
    private val context: Context,
    bufferSize: Int,
    private val sampleIntervalMs: Long
) {
    actual constructor() : this(
        context = AndroidContextProvider.context,
        bufferSize = 60,
        sampleIntervalMs = 1000L
    )

    private val hrBuffer = CircularFloatBuffer(bufferSize)
    private val noiseBuffer = CircularFloatBuffer(bufferSize)

    private val _latestHrStats = AtomicReference(StatsUtil.RollingStats())
    private val _latestNoiseStats = AtomicReference(StatsUtil.RollingStats())

    actual val latestHeartRateStats: StatsUtil.RollingStats get() = _latestHrStats.load()
    actual val latestNoiseStats: StatsUtil.RollingStats get() = _latestNoiseStats.load()

    private var hrMonitorJob: Job? = null
    private var noiseMonitorJob: Job? = null
    private var simulationJob: Job? = null
    // -------------------------------------------------------------

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var heartRateListener: SensorEventListener? = null
    private var noiseJob: Job? = null

    companion object {
        private const val TAG = "AndroidSensorBridge"
        private const val BASE_SPL_OFFSET_DB = 94.0
        private const val NOISE_FLOOR_DB = 30.0
        private const val FFT_SIZE = 1024
        private const val HOP_SIZE = FFT_SIZE / 2
        private const val F1_SQ = 20.6 * 20.6
        private const val F2_SQ = 107.7 * 107.7
        private const val F3_SQ = 737.9 * 737.9
        private const val F4_SQ = 12200.0 * 12200.0
        private val SPL_LINEAR_SCALE = 10.0.pow(BASE_SPL_OFFSET_DB / 10.0)
        private val DEVICE_FREQ_CORRECTION_DB = mapOf(
            63 to 2.0, 125 to 1.5, 250 to 1.0, 500 to 0.5,
            1000 to 0.0, 2000 to -0.5, 4000 to -1.0, 8000 to 1.0, 16000 to 3.0
        )
    }

    private val hannWindow: DoubleArray = DoubleArray(FFT_SIZE) { n ->
        0.5 * (1.0 - cos(2.0 * PI * n / (FFT_SIZE - 1)))
    }
    private val hannPowerCorrection: Double = FFT_SIZE.toDouble() / hannWindow.sumOf { it * it }

    // --- 심박수 센서 제어 ---
    actual fun startHeartRateSensor(scope: CoroutineScope) {
        // 1. 심박수 통계 모니터링 루프 자체 구동
        startHeartRateMonitoring(scope)

        val hrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (hrSensor == null || !hasBodySensorsPermission()) {
            Log.w(TAG, "BODY_SENSORS 권한 없거나 센서 미지원 — 심박수 시뮬레이션으로 대체")
            simulateHeartRate(scope)
            return
        }
        heartRateListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val bpm = event.values[0]
                if (bpm > 0f) hrBuffer.add(bpm) // 모니터 클래스 대신 내부 버퍼에 직접 추가
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sensorManager.registerListener(
            heartRateListener,
            hrSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    actual fun stopHeartRateSensor() {
        heartRateListener?.let { sensorManager.unregisterListener(it) }
        heartRateListener = null
        simulationJob?.cancel()
        simulationJob = null

        // 심박수 통계 모니터링 루프 정지 및 버퍼 비우기
        stopHeartRateMonitoring()
    }

    // --- 소음 센서 제어 ---
    actual fun startNoiseSensor(scope: CoroutineScope) {
        if (!hasRecordAudioPermission()) {
            Log.e(TAG, "RECORD_AUDIO 권한 없음 — 소음 측정을 시작할 수 없습니다")
            return
        }
        Log.d(TAG, "소음 측정 시작")

        // 1. 소음 통계 모니터링 루프 자체 구동
        startNoiseMonitoring(scope)

        val sampleRate = getBestSampleRate()
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuf, FFT_SIZE * 4)
        val perBinGain = buildPerBinGain(sampleRate)

        noiseJob = scope.launch(Dispatchers.IO) {
            val recorder = createAudioRecord(sampleRate, bufferSize)
            try {
                recorder.startRecording()
                val slidingBuffer = ShortArray(FFT_SIZE)
                val hopBuffer = ShortArray(HOP_SIZE)
                val fftReal = DoubleArray(FFT_SIZE)
                val fftImag = DoubleArray(FFT_SIZE)

                val alphaAttack = 1.0 - exp(-HOP_SIZE.toDouble() / (sampleRate * 0.035))
                val alphaRelease = 1.0 - exp(-HOP_SIZE.toDouble() / (sampleRate * 1.5))
                var smoothedEnergy = 10.0.pow(NOISE_FLOOR_DB / 10.0)
                var isBufferReady = false

                while (isActive) {
                    val read = recorder.read(hopBuffer, 0, HOP_SIZE)
                    if (read < HOP_SIZE) continue

                    System.arraycopy(slidingBuffer, HOP_SIZE, slidingBuffer, 0, HOP_SIZE)
                    System.arraycopy(hopBuffer, 0, slidingBuffer, HOP_SIZE, HOP_SIZE)

                    if (!isBufferReady) {
                        isBufferReady = true
                        continue
                    }

                    val frameEnergy =
                        computeFrameEnergy(slidingBuffer, fftReal, fftImag, perBinGain)
                    val floorEnergy = 10.0.pow(NOISE_FLOOR_DB / 10.0)
                    val gatedEnergy = maxOf(frameEnergy * SPL_LINEAR_SCALE, floorEnergy)

                    val alpha = if (gatedEnergy > smoothedEnergy) alphaAttack else alphaRelease
                    smoothedEnergy = alpha * gatedEnergy + (1.0 - alpha) * smoothedEnergy

                    val dBSpl = 10.0 * log10(smoothedEnergy)
                    val finalDb = dBSpl.toFloat().coerceIn(NOISE_FLOOR_DB.toFloat(), 120f)

                    noiseBuffer.add(finalDb) // 모니터 클래스 대신 내부 버퍼에 직접 추가
                }
            } finally {
                recorder.stop()
                recorder.release()
            }
        }
    }

    actual fun stopNoiseSensor() {
        noiseJob?.cancel()
        noiseJob = null

        // 소음 통계 모니터링 루프 정지 및 버퍼 비우기
        stopNoiseMonitoring()
    }

    // -------------------------------------------------------------
    // [통합된 내부 연산 메소드] 기존 SensorStatsMonitor의 연산 루프 분할 구현
    // -------------------------------------------------------------
    private fun startHeartRateMonitoring(scope: CoroutineScope) {
        if (hrMonitorJob?.isActive == true) return
        hrMonitorJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val stats = StatsUtil.computeStats(hrBuffer.toList())
                _latestHrStats.store(stats)
                delay(sampleIntervalMs)
            }
        }
    }

    private fun stopHeartRateMonitoring() {
        hrMonitorJob?.cancel()
        hrMonitorJob = null
        hrBuffer.clear()
        _latestHrStats.store(StatsUtil.RollingStats())
    }

    private fun startNoiseMonitoring(scope: CoroutineScope) {
        if (noiseMonitorJob?.isActive == true) return
        noiseMonitorJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val stats = StatsUtil.computeStats(noiseBuffer.toList())
                _latestNoiseStats.store(stats)
                delay(sampleIntervalMs)
            }
        }
    }

    private fun stopNoiseMonitoring() {
        noiseMonitorJob?.cancel()
        noiseMonitorJob = null
        noiseBuffer.clear()
        _latestNoiseStats.store(StatsUtil.RollingStats())
    }
    // -------------------------------------------------------------

    // ... 기존 오디오 처리 및 FFT 헬퍼 메소드들 (getBestSampleRate, fftInPlace 등) 동일하게 유지 ...
    private fun getBestSampleRate(): Int { /* 기존 코드 유지 */ return 44100
    }

    @Throws(SecurityException::class)
    private fun createAudioRecord(sampleRate: Int, bufferSize: Int): AudioRecord {
        // UNPROCESSED 오디오 소스 시도 후 실패 시 MIC로 폴백하는 기존 구조 유지
        val unprocessed = AudioRecord(
            MediaRecorder.AudioSource.UNPROCESSED,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (unprocessed.state == AudioRecord.STATE_INITIALIZED) return unprocessed
        unprocessed.release()

        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
    }

    private fun computeFrameEnergy(
        frame: ShortArray,
        fftReal: DoubleArray,
        fftImag: DoubleArray,
        perBinGain: DoubleArray
    ): Double { /* 기존 코드 유지 */ return 0.0
    }

    private fun buildPerBinGain(sampleRate: Int): DoubleArray { /* 기존 코드 유지 */ return DoubleArray(0)
    }

    private fun aWeightingDb(freq: Double): Double { /* 기존 코드 유지 */ return 0.0
    }

    private fun fftInPlace(re: DoubleArray, im: DoubleArray) { /* 기존 코드 유지 */
    }

    private fun interpolateDeviceCorrection(freq: Double): Double { /* 기존 코드 유지 */ return 0.0
    }

    private fun hasRecordAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasBodySensorsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BODY_SENSORS
    ) == PackageManager.PERMISSION_GRANTED

    private fun simulateHeartRate(scope: CoroutineScope) {
        simulationJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                hrBuffer.add((55..75).random().toFloat())
                delay(2000L)
            }
        }
    }
}
