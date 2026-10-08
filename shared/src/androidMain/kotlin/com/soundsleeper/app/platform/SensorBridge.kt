// androidMain/src/androidMain/kotlin/com/sleepytime/shared/platform/SensorBridge.kt
package com.soundsleeper.app.platform

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
import com.soundsleeper.app.util.CircularFloatBuffer
import com.soundsleeper.app.util.SleepFeatureBuilder
import com.soundsleeper.app.util.StatsUtil
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

    private val noiseBuffer = CircularFloatBuffer(bufferSize)
    private val gyroBuffer = CircularFloatBuffer(bufferSize * 3)

    private val _latestNoiseStats = AtomicReference(StatsUtil.RollingStats())
    private val _latestGyroStats = AtomicReference(StatsUtil.RollingStats())

    actual val latestNoiseStats: StatsUtil.RollingStats get() = _latestNoiseStats.load()
    actual val latestGyroStats: StatsUtil.RollingStats get() = _latestGyroStats.load()

    private var noiseMonitorJob: Job? = null
    private var gyroMonitorJob: Job? = null
    // -------------------------------------------------------------

    // --- 신규(Phase 8): raw 오디오 에포크 보존 ---
    // 기존 dB 소음 계산(noiseBuffer)과 완전히 별개로 동작하는 병렬 경로입니다. 같은 녹음
    // 스트림(AudioRecord)의 hop을 함께 읽기만 하고, dB 계산 로직은 전혀 건드리지 않습니다.
    private val _latestAudioEpoch = AtomicReference<FloatArray?>(null)

    /**
     * 가장 최근에 완성된 30초 오디오 에포크(16kHz로 리샘플링된 raw 샘플, [-1,1] 정규화)를
     * 꺼내고 내부 상태를 비웁니다. 아직 완성된 에포크가 없으면 null을 반환합니다.
     * [SleepAnalyzer]가 한 epoch 분석마다 한 번씩 호출해 멜스펙토그램 계산 입력으로 씁니다.
     */
    fun consumeLatestAudioEpoch(): FloatArray? {
        val value = _latestAudioEpoch.load()
        if (value != null) _latestAudioEpoch.store(null)
        return value
    }

    /** 선형보간으로 샘플레이트를 바꿉니다(SleepFeatureBuilder.resampleTo와 같은 방식). */
    private fun resampleAudioLinear(src: FloatArray, dstLength: Int): FloatArray {
        if (src.isEmpty()) return FloatArray(dstLength)
        if (src.size == 1) return FloatArray(dstLength) { src[0] }
        val ratio = (src.size - 1).toFloat() / (dstLength - 1).toFloat()
        return FloatArray(dstLength) { i ->
            val srcIndex = i * ratio
            val lo = srcIndex.toInt().coerceIn(0, src.size - 1)
            val hi = (lo + 1).coerceAtMost(src.size - 1)
            val frac = srcIndex - lo
            src[lo] + (src[hi] - src[lo]) * frac
        }
    }

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
    /**
     * 자이로스코프 센서를 등록해 X/Y/Z 회전 속도를 최대 속도(SENSOR_DELAY_FASTEST)로 수집한다.
     * 이 값은 BCG(심박 추출) 등 통계용 롤링 버퍼([gyroBuffer])에 쌓이며, [AndroidTrackingManager]가
     * 직접 쓰는 가속도/자이로 분류 파이프라인과는 별개의 경로다.
     */
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

    /** 자이로스코프 리스너를 해제하고 통계 모니터링 루프도 함께 멈춘다. */
    actual fun stopGyroscopeSensor() {
        gyroscopeListener?.let { sensorManager.unregisterListener(it) }
        gyroscopeListener = null
        stopGyroscopeMonitoring()
    }

    // --- 소음 센서 제어 ---
    /**
     * 마이크로 주변 소음(dB)을 실시간 측정한다. 이 측정 화면 전체에서 유일하게 raw 오디오를
     * 다루는 경로로, AudioRecord → 1024pt FFT([computeFrameEnergy]) → A-weighting/기기 보정
     * ([buildPerBinGain]) → attack/release 스무딩 → dB SPL 변환까지 거쳐 [noiseBuffer]에 쌓는다.
     * 여기서 만들어진 dB 값은 raw 파형이 아니라 스칼라 하나뿐이며, 측정 화면의 소음 표시와
     * [AndroidTrackingManager]의 환경(EnvironmentFeature) 계산에 쓰인다.
     */
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
            // 신규(Phase 8): 30초치 raw 오디오를 native 샘플레이트로 모았다가 16kHz로
            // 리샘플링해 한 epoch씩 내보냅니다. dB 계산과 같은 녹음 스트림을 함께 읽기만
            // 하므로, 아래 dB 관련 코드는 전혀 바뀌지 않습니다.
            val audioEpochCapacity = sampleRate * AccelChannels.EPOCH_SEC
            var audioEpochBuf = FloatArray(audioEpochCapacity)
            var audioEpochIdx = 0
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

                    // 신규(Phase 8): 이번 hop의 raw 샘플을 현재 오디오 에포크 버퍼에 쌓습니다.
                    // 버퍼가 30초치(native rate 기준)로 가득 차면 16kHz로 리샘플링해 내보내고
                    // 새 에포크를 시작합니다 — dB 계산의 워밍업 여부(isBufferReady)와 무관하게
                    // 항상 쌓습니다(오디오 에포크 경계가 dB 계산 워밍업과 맞을 필요는 없음).
                    for (i in 0 until HOP_SIZE) {
                        if (audioEpochIdx < audioEpochBuf.size) {
                            audioEpochBuf[audioEpochIdx++] = hopBuffer[i] / 32768f
                        }
                    }
                    if (audioEpochIdx >= audioEpochBuf.size) {
                        val targetLength = SleepFeatureBuilder.AUDIO_SAMPLE_RATE * AccelChannels.EPOCH_SEC
                        _latestAudioEpoch.store(resampleAudioLinear(audioEpochBuf, targetLength))
                        audioEpochBuf = FloatArray(audioEpochCapacity)
                        audioEpochIdx = 0
                    }

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

    /** 녹음 코루틴을 취소하고 소음 통계 모니터링 루프도 멈춘 뒤 버퍼를 비운다. */
    actual fun stopNoiseSensor() {
        noiseJob?.cancel()
        noiseJob = null

        // 소음 통계 모니터링 루프 정지 및 버퍼 비우기
        stopNoiseMonitoring()
    }

    // -------------------------------------------------------------
    // [통합된 내부 연산 메소드] 기존 SensorStatsMonitor의 연산 루프 분할 구현
    // -------------------------------------------------------------
    /** [sampleIntervalMs]마다 소음 버퍼의 평균/최대/최소 등을 계산해 [latestNoiseStats]에 반영한다. */
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

    /** 소음 통계 루프를 멈추고 버퍼/통계를 초기 상태로 되돌린다. */
    private fun stopNoiseMonitoring() {
        noiseMonitorJob?.cancel()
        noiseMonitorJob = null
        noiseBuffer.clear()
        _latestNoiseStats.store(StatsUtil.RollingStats())
    }

    /** [sampleIntervalMs]마다 자이로 버퍼의 통계를 계산해 [latestGyroStats]에 반영한다. */
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

    /** 자이로 통계 루프를 멈추고 버퍼/통계를 초기 상태로 되돌린다. */
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
    /** 기기의 네이티브 샘플레이트를 먼저 시도하고, AudioRecord가 받아주지 않으면 흔한 값들로 폴백한다. */
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

    /** 마이크 입력을 여는 AudioRecord를 생성한다. UNPROCESSED 소스를 먼저 시도하고 실패하면 MIC로 폴백한다. */
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

    /**
     * 한 프레임(FFT_SIZE 샘플)에 Hann 윈도우를 씌워 FFT를 돌리고, 각 주파수 빈에
     * A-weighting/기기 보정 게인([perBinGain])을 곱해 합산한 뒤 평균 에너지로 정규화한다.
     * 이 값이 그대로 dB SPL 계산의 입력이 된다.
     */
    private fun computeFrameEnergy(
        frame: ShortArray,
        fftReal: DoubleArray,
        fftImag: DoubleArray,
        perBinGain: DoubleArray
    ): Double {
        for (i in 0 until FFT_SIZE) {
            fftReal[i] = (frame[i].toDouble() / 32768.0) * hannWindow[i]
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

    /** 각 FFT 주파수 빈에 대해 A-weighting과 기기별 마이크 보정을 합친 선형 게인 테이블을 만든다. */
    private fun buildPerBinGain(sampleRate: Int): DoubleArray {
        val binFreqStep = sampleRate.toDouble() / FFT_SIZE
        return DoubleArray(FFT_SIZE) { i ->
            val freq = i * binFreqStep
            val aWeight = aWeightingDb(freq)
            val correction = interpolateDeviceCorrection(freq)
            10.0.pow((aWeight + correction) / 10.0)
        }
    }

    /** 표준 A-weighting 보정 공식(IEC 61672)으로 주어진 주파수의 가중치를 dB로 계산한다. */
    private fun aWeightingDb(freq: Double): Double {
        val fSq = freq * freq
        val rA = (F4_SQ * fSq * fSq) /
                ((fSq + F1_SQ) * sqrt((fSq + F2_SQ) * (fSq + F3_SQ)) * (fSq + F4_SQ))
        return 20.0 * log10(rA) + 2.0
    }

    /** 제자리(in-place) radix-2 고속 푸리에 변환. 비트 반전으로 재배열한 뒤 버터플라이 연산을 반복한다. */
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

    /** 보정 테이블에 없는 주파수는 가장 가까운 두 기준점 사이를 선형보간해 보정값을 구한다. */
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

    /** 마이크(RECORD_AUDIO) 권한이 부여되어 있는지 확인한다. */
    private fun hasRecordAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

}
