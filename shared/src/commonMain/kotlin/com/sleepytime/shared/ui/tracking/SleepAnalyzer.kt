package com.sleepytime.shared.ui.tracking

import com.sleepytime.shared.platform.SleepStageClassifier
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepAnalysis
import com.sleepytime.shared.enum_.PredictionStageType
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlin.math.abs

private const val TAG = "SleepAnalyzer"
private const val TARGET_SAMPLE_COUNT = 1500

class SleepAnalyzer(private val classifier: SleepStageClassifier) {
    
    fun analyzeWindow(
        sensorData: List<FloatArray>,
        environmentFeature: EnvironmentFeature?,
        sessionStartTimeMs: Long
    ): Result<SleepAnalysis> {
        if (!classifier.isReady()) {
            Napier.d(tag = TAG, message = "모델 미초기화")
            return Result.failure(Exception("모델 미초기화"))
        }

        return try {
            val currentTimeMs = Clock.System.now().toEpochMilliseconds()

            val hrFallback = SleepStageClassifier.CHANNEL_MEAN[SleepStageClassifier.CH_HEART_RATE]

            val noiseValue = environmentFeature?.stats?.noise?.avg ?: 0f // 기본 진폭 (Noise_RMS)
            val mfccEnergy = environmentFeature?.stats?.noise?.max?.minus(noiseValue)?.coerceAtLeast(0f) ?: 0f

            val elapsedMs = (currentTimeMs - sessionStartTimeMs).coerceAtLeast(0L)
            val timeFeature = (elapsedMs.toDouble() / 28_800_000.0).coerceAtMost(1.0).toFloat()

            val resampled = resampleTo(sensorData, TARGET_SAMPLE_COUNT)

            val avgX = resampled.map { it.getOrElse(SleepStageClassifier.CH_ACCEL_X) { 0f } }.average().toFloat()
            val avgY = resampled.map { it.getOrElse(SleepStageClassifier.CH_ACCEL_Y) { 0f } }.average().toFloat()
            val avgZ = resampled.map { it.getOrElse(SleepStageClassifier.CH_ACCEL_Z) { 1f } }.average().toFloat()

            val expanded: List<FloatArray> = resampled.map { sample ->
                val x = sample.getOrElse(SleepStageClassifier.CH_ACCEL_X) { 0f }
                val y = sample.getOrElse(SleepStageClassifier.CH_ACCEL_Y) { 0f }
                val z = sample.getOrElse(SleepStageClassifier.CH_ACCEL_Z) { 1f }

                val tiltAngle = abs(x - avgX) + abs(y - avgY) + abs(z - avgZ)
                floatArrayOf(
                    x,
                    y,
                    z,
                    tiltAngle,
                    hrFallback,
                    noiseValue,
                    mfccEnergy,
                    timeFeature
                )
            }

            val stageIdx = classifier.classifySleepStage(listOf(expanded))
            val predictionStage = indexToStage(stageIdx)

            val confidence = 1.0f

            Napier.d(tag = TAG, message = "추론 성공: $predictionStage (expanded=$expanded)")

            val calculatedDurationMs = if (sensorData.isNotEmpty()) {
                ((sensorData.size.toDouble() / 50.0) * 1000.0).toLong()
            }
            else 30_000L

            val analysis = SleepAnalysis(
                timestamp = currentTimeMs,
                predictionStageType = predictionStage,
                windowDurationMs = calculatedDurationMs,
                confidence = confidence,
                isSleepOnsetCandidate = false,
                environmentFeature = environmentFeature
            )

            Result.success(analysis)
        } catch (e: Exception) {
            Napier.e(tag = TAG, throwable = e, message = "추론 실패")
            Result.failure(e)
        }
    }

    fun close() {
        classifier.close()
    }

    fun isReady(): Boolean = classifier.isReady()
    private fun resampleTo(samples: List<FloatArray>, targetCount: Int): List<FloatArray> {
        if (samples.size == targetCount) return samples
        if (samples.size == 1) return List(targetCount) { samples[0].copyOf() }

        val result = ArrayList<FloatArray>(targetCount)
        val ratio = (samples.size - 1).toFloat() / (targetCount - 1).toFloat()
        val channelCount = samples[0].size

        for (i in 0 until targetCount) {
            val srcIndex = i * ratio
            val lo = srcIndex.toInt().coerceIn(0, samples.size - 1)
            val hi = (lo + 1).coerceAtMost(samples.size - 1)
            val frac = srcIndex - lo

            val interpolated = FloatArray(channelCount) { ch ->
                val a = samples[lo].getOrElse(ch) { 0f }
                val b = samples[hi].getOrElse(ch) { 0f }
                a + (b - a) * frac
            }
            result.add(interpolated)
        }
        return result
    }

    private fun indexToStage(index: Int): PredictionStageType = when (index) {
        0 -> PredictionStageType.AWAKE
        1 -> PredictionStageType.N1
        2 -> PredictionStageType.N2
        3 -> PredictionStageType.N3
        4 -> PredictionStageType.REM
        else -> PredictionStageType.AWAKE
    }
}
