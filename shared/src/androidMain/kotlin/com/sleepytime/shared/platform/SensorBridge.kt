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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

actual class SensorBridge(
    private val context: Context,
    private val heartRateMonitor: HeartRateMonitor,
    private val noiseDetector: NoiseDetector
) {
    companion object {
        private const val TAG = "AndroidSensorBridge"
        private const val BASE_SPL_OFFSET_DB = 94.0

        private val DEVICE_FREQ_CORRECTION_DB = mapOf(
            63 to 2.0,
            125 to 1.5,
            250 to 1.0,
            500 to 0.5,
            1000 to 0.0,
            2000 to -0.5,
            4000 to -1.0,
            8000 to 1.0,
            16000 to 3.0
        )
        private const val FFT_SIZE = 1024
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var heartRateListener: SensorEventListener? = null
    private var noiseJob: Job? = null

    private val hannWindow: DoubleArray = DoubleArray(FFT_SIZE) { n ->
        0.5 * (1.0 - cos(2.0 * PI * n / (FFT_SIZE - 1)))
    }

    actual fun startHeartRateSensor(scope: CoroutineScope) {
        heartRateMonitor.startMonitoring(scope)
        val hrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (!hasBodySensorsPermission()) {
            Log.w(TAG, "BODY_SENSORS 권한 없음 — 심박수 시뮬레이션으로 대체")
            simulateHeartRate(scope)
            return
        }
        heartRateListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val bpm = event.values[0]
                if (bpm > 0f) heartRateMonitor.onNewHeartRateSample(bpm)
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
        heartRateMonitor.stopMonitoring()
    }

    actual fun startNoiseSensor(scope: CoroutineScope) {
        if (!hasRecordAudioPermission()) {
            Log.e(TAG, "RECORD_AUDIO 권한 없음 — 소음 측정을 시작할 수 없습니다")
            return
        }
        Log.e(TAG, "소음 측정 시작")
        noiseDetector.startMonitoring(scope)
        val sampleRate = getBestSampleRate()
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuf, FFT_SIZE * 2)
        val perBinGain = buildPerBinGain(sampleRate)

        noiseJob = scope.launch(Dispatchers.IO) {
            val recorder = createAudioRecord(sampleRate, bufferSize)
            try {
                recorder.startRecording()
                val audioBuffer = ShortArray(bufferSize)
                var smoothedDb = 0.0
                val alpha = 0.05
                var isFirstSample = true
                val fftReal = DoubleArray(FFT_SIZE)
                val fftImag = DoubleArray(FFT_SIZE)
                while (isActive) {
                    val read = recorder.read(audioBuffer, 0, bufferSize)
                    if (read < FFT_SIZE) continue
                    val aWeightedDbSpl =
                        computeAWeightedDbSpl(audioBuffer, read, fftReal, fftImag, perBinGain)
                    val rakingCorrectedDb = applyRakingApproximation(aWeightedDbSpl)
                    smoothedDb = if (isFirstSample) {
                        isFirstSample = false
                        rakingCorrectedDb
                    } else {
                        alpha * rakingCorrectedDb + (1.0 - alpha) * smoothedDb
                    }
                    noiseDetector.onNewNoiseSample(smoothedDb.toFloat())
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
        noiseDetector.stopMonitoring()
    }

    actual fun getHeartRate(): Float = heartRateMonitor.getCurrentHeartRate()
    actual fun getNoiseLevel(): Float = noiseDetector.getCurrentNoise()
    actual fun isDataReady(): Boolean =
        heartRateMonitor.getCurrentHeartRate() > 0f || noiseDetector.getCurrentNoise() > 0f

    private fun simulateHeartRate(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                heartRateMonitor.onNewHeartRateSample((55..75).random().toFloat())
                delay(2000L)
            }
        }
    }

    private fun getBestSampleRate(): Int {
        for (rate in intArrayOf(48000, 44100)) {
            if (AudioRecord.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ) > 0
            ) return rate
        }
        return 44100
    }

    @Throws(SecurityException::class)
    private fun createAudioRecord(sampleRate: Int, bufferSize: Int): AudioRecord {
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

    private fun computeAWeightedDbSpl(
        audioBuffer: ShortArray,
        readSize: Int,
        fftReal: DoubleArray,
        fftImag: DoubleArray,
        perBinGain: DoubleArray
    ): Double {
        var weightedEnergySum = 0.0
        var frameCount = 0
        var offset = 0
        while (offset + FFT_SIZE <= readSize) {
            for (i in 0 until FFT_SIZE) {
                fftReal[i] = audioBuffer[offset + i] / 32768.0 * hannWindow[i]
                fftImag[i] = 0.0
            }
            fftInPlace(fftReal, fftImag)
            var frameEnergy = 0.0
            for (k in 1..FFT_SIZE / 2) {
                val mag = fftReal[k].pow(2.0) + fftImag[k].pow(2.0)
                frameEnergy += mag * (if (k < FFT_SIZE / 2) 2.0 else 1.0) * perBinGain[k].pow(2.0)
            }
            weightedEnergySum += frameEnergy
            frameCount++
            offset += FFT_SIZE
        }
        if (frameCount == 0 || weightedEnergySum == 0.0) return -160.0
        return 10.0 * log10((weightedEnergySum / frameCount) * 10.0.pow(BASE_SPL_OFFSET_DB / 10.0))
    }

    private fun buildPerBinGain(sampleRate: Int): DoubleArray {
        val gain = DoubleArray(FFT_SIZE / 2 + 1)
        for (k in 0..FFT_SIZE / 2) {
            val freq = k.toDouble() * sampleRate / FFT_SIZE
            val corr = if (freq < 1.0) 0.0 else interpolateDeviceCorrection(freq.toInt())
            val aWeight = if (freq < 10.0) -160.0 else aWeightingDb(freq)
            gain[k] = 10.0.pow((corr + aWeight) / 20.0)
        }
        return gain
    }

    private fun aWeightingDb(freq: Double): Double {
        val f2 = freq.pow(2.0)
        val ra =
            (12200.0.pow(2) * f2.pow(2)) / ((f2 + 20.6.pow(2)) * sqrt(f2 + 107.7.pow(2)) * sqrt(
                f2 + 737.9.pow(2)
            ) * (f2 + 12200.0.pow(2)))
        return if (ra > 0.0) 2.0 + 20.0 * log10(ra) else -160.0
    }

    private fun applyRakingApproximation(dBA: Double): Double {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val bias = dBA - if (hour in 22..23 || hour in 0..5) 35.0 else 45.0
        return if (bias > 10.0 || bias < -10.0) dBA - bias * 0.5 else dBA
    }

    private fun fftInPlace(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit; bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }; im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angleStep = -2.0 * PI / len
            val wRe = cos(angleStep)
            val wIm = sin(angleStep)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until halfLen) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + halfLen] * curRe - im[i + k + halfLen] * curIm
                    val vIm = re[i + k + halfLen] * curIm + im[i + k + halfLen] * curRe
                    re[i + k] = uRe + vRe; im[i + k] = uIm + vIm
                    re[i + k + halfLen] = uRe - vRe; im[i + k + halfLen] = uIm - vIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun interpolateDeviceCorrection(freq: Int): Double {
        val sorted = DEVICE_FREQ_CORRECTION_DB.keys.sorted()
        if (freq <= sorted.first()) return DEVICE_FREQ_CORRECTION_DB[sorted.first()]!!
        if (freq >= sorted.last()) return DEVICE_FREQ_CORRECTION_DB[sorted.last()]!!
        val lo = sorted.last { it <= freq }
        val hi = sorted.first { it > freq }
        val t = (freq - lo).toDouble() / (hi - lo).toDouble()
        return DEVICE_FREQ_CORRECTION_DB[lo]!! + t * (DEVICE_FREQ_CORRECTION_DB[hi]!! - DEVICE_FREQ_CORRECTION_DB[lo]!!)
    }

    private fun hasRecordAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasBodySensorsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BODY_SENSORS
    ) == PackageManager.PERMISSION_GRANTED
}
