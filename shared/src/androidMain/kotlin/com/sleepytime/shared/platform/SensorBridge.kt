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
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
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
import kotlin.random.Random



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

    private val noiseBuffer = CircularFloatBuffer(bufferSize)
    private val gyroBuffer = CircularFloatBuffer(bufferSize * 3)

    private val _latestNoiseStats = AtomicReference(StatsUtil.RollingStats())
    private val _latestGyroStats = AtomicReference(StatsUtil.RollingStats())

    actual val latestNoiseStats: StatsUtil.RollingStats get() = _latestNoiseStats.load()
    actual val latestGyroStats: StatsUtil.RollingStats get() = _latestGyroStats.load()

    private var noiseMonitorJob: Job? = null
    private var gyroMonitorJob: Job? = null
    // -------------------------------------------------------------

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var gyroscopeListener: SensorEventListener? = null
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

        // 💡 소음측정 정확도 개선: 이름과 달리 기기별 값이 아니라 전 기기에 동일하게 적용되는
        // 일반적인 마이크 주파수 보정 커브였습니다(DEVICE_FREQ_CORRECTION_DB라는 이름이 오해의
        // 소지가 있어 명확히 함). 특정 기기 모델의 실측 보정값이 확보되면
        // DEVICE_MODEL_FREQ_CORRECTION_DB에 Build.MODEL을 키로 추가하면 해당 기기에서만
        // 다른 커브가 적용되도록 구조를 마련해두었습니다 — 다만 실측 데이터가 없는 지금은
        // 비워두고 모든 기기가 GENERIC 커브를 사용합니다.
        private val GENERIC_MIC_FREQ_CORRECTION_DB = mapOf(
            63 to 2.0, 125 to 1.5, 250 to 1.0, 500 to 0.5,
            1000 to 0.0, 2000 to -0.5, 4000 to -1.0, 8000 to 1.0, 16000 to 3.0
        )
        private val DEVICE_MODEL_FREQ_CORRECTION_DB: Map<String, Map<Int, Double>> = emptyMap()
    }

    private val hannWindow: DoubleArray = DoubleArray(FFT_SIZE) { n ->
        0.5 * (1.0 - cos(2.0 * PI * n / (FFT_SIZE - 1)))
    }
    private val hannPowerCorrection: Double = FFT_SIZE.toDouble() / hannWindow.sumOf { it * it }

    // --- 자이로스코프 센서 제어 ---
    actual fun startGyroscopeSensor(scope: CoroutineScope) {
        startGyroscopeMonitoring(scope)

        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyroSensor == null) {
            Log.w(TAG, "자이로스코프 센서를 지원하지 않는 기기입니다")
            return
        }

        gyroscopeListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    // X, Y, Z 회전 속도 값 저장 (rad/s)
                    gyroBuffer.add(event.values[0])
                    gyroBuffer.add(event.values[1])
                    gyroBuffer.add(event.values[2])
                    
                    // Log.v(TAG, "Gyro: X=${event.values[0]}, Y=${event.values[1]}, Z=${event.values[2]}")
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        sensorManager.registerListener(
            gyroscopeListener,
            gyroSensor,
            SensorManager.SENSOR_DELAY_FASTEST // 💡 심박 추출(BCG)을 위해 최대한 빠른 속도로 수집
        )
    }

    actual fun stopGyroscopeSensor() {
        gyroscopeListener?.let { sensorManager.unregisterListener(it) }
        gyroscopeListener = null
        stopGyroscopeMonitoring()
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

    private fun startGyroscopeMonitoring(scope: CoroutineScope) {
        if (gyroMonitorJob?.isActive == true) return
        gyroMonitorJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val stats = StatsUtil.computeStats(gyroBuffer.toList())
                _latestGyroStats.store(stats)
                delay(sampleIntervalMs)
            }
        }
    }

    private fun stopGyroscopeMonitoring() {
        gyroMonitorJob?.cancel()
        gyroMonitorJob = null
        gyroBuffer.clear()
        _latestGyroStats.store(StatsUtil.RollingStats())
    }
    // -------------------------------------------------------------

    // 💡 소음측정 정확도 개선: 이전에는 44100Hz로 고정되어 있었습니다. 기기의 실제 오디오
    // 입력 네이티브 샘플레이트를 먼저 시도하고, AudioRecord가 지원하지 않으면 흔히 쓰이는
    // 샘플레이트로 순서대로 폴백합니다.
    private fun getBestSampleRate(): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val nativeRate = audioManager
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()

        val candidates = listOfNotNull(nativeRate, 44100, 48000, 16000).distinct()
        for (rate in candidates) {
            val minBuf = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf > 0) return rate
        }
        return 44100
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
    ): Double {
        for (i in 0 until FFT_SIZE) {
            fftReal[i] = frame[i].toDouble() * hannWindow[i]
            fftImag[i] = 0.0
        }
        fftInPlace(fftReal, fftImag)

        var totalEnergy = 0.0
        for (i in 1 until FFT_SIZE / 2) {
            val magSq = fftReal[i] * fftReal[i] + fftImag[i] * fftImag[i]
            totalEnergy += magSq * perBinGain[i]
        }
        return (totalEnergy / (FFT_SIZE / 2)) * hannPowerCorrection
    }

    private fun buildPerBinGain(sampleRate: Int): DoubleArray {
        val binFreqStep = sampleRate.toDouble() / FFT_SIZE
        return DoubleArray(FFT_SIZE) { i ->
            val freq = i * binFreqStep
            val aWeight = aWeightingDb(freq)
            val correction = interpolateDeviceCorrection(freq)
            10.0.pow((aWeight + correction) / 10.0)
        }
    }

    private fun aWeightingDb(freq: Double): Double {
        val fSq = freq * freq
        val rA = (F4_SQ * fSq * fSq) /
                ((fSq + F1_SQ) * sqrt((fSq + F2_SQ) * (fSq + F3_SQ)) * (fSq + F4_SQ))
        return 20.0 * log10(rA) + 2.0
    }

    private fun fftInPlace(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                val tempRe = re[i]; re[i] = re[j]; re[j] = tempRe
                val tempIm = im[i]; im[i] = im[j]; im[j] = tempIm
            }
            var m = n shr 1
            while (m >= 1 && j >= m) { j -= m; m = m shr 1 }
            j += m
        }
        var len = 2
        while (len <= n) {
            val ang = 2.0 * PI / len
            val wlenRe = cos(ang)
            val wlenIm = -sin(ang)
            for (i in 0 until n step len) {
                var wRe = 1.0
                var wIm = 0.0
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                    val vIm = re[i + k + len / 2] * wRe + im[i + k + len / 2] * wIm
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextWRe = wRe * wlenRe - wIm * wlenIm
                    wIm = wRe * wlenIm + wIm * wlenRe
                    wRe = nextWRe
                }
            }
            len = len shl 1
        }
    }

    private fun interpolateDeviceCorrection(freq: Double): Double {
        val correctionDb = DEVICE_MODEL_FREQ_CORRECTION_DB[Build.MODEL] ?: GENERIC_MIC_FREQ_CORRECTION_DB
        val keys = correctionDb.keys.sorted()
        if (freq <= keys.first()) return correctionDb[keys.first()]!!
        if (freq >= keys.last()) return correctionDb[keys.last()]!!
        for (i in 0 until keys.size - 1) {
            val f1 = keys[i]
            val f2 = keys[i+1]
            if (freq in f1.toDouble()..f2.toDouble()) {
                val v1 = correctionDb[f1]!!
                val v2 = correctionDb[f2]!!
                return v1 + (v2 - v1) * (freq - f1) / (f2 - f1)
            }
        }
        return 0.0
    }

    private fun hasRecordAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

}
