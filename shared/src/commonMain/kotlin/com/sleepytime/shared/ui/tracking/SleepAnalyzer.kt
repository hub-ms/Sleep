package com.sleepytime.shared.ui.tracking

import com.sleepytime.shared.platform.SleepStageClassifier
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepAnalysis
import com.sleepytime.shared.enum_.PredictionStageType
import com.sleepytime.shared.util.SignalProcessor
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAG = "SleepAnalyzer"
private const val TARGET_SAMPLE_COUNT = 1500
private const val RAW_GYRO_X = 3
private const val RAW_GYRO_Y = 4
private const val RAW_GYRO_Z = 5

class SleepAnalyzer(private val classifier: SleepStageClassifier) {

    // 🐛 버그 수정: 모델은 한 번에 CONTEXT_LEN(60)개의 연속된 epoch를 컨텍스트로 요구하는데,
    // 이전에는 매번 새로 들어온 epoch 1개만 담아(listOf(expanded)) classifySleepStage를 호출해서
    // SleepStageClassifier의 require(sensorData.size == CONTEXT_LEN) 체크에 항상 걸려 추론이
    // 조용히 실패(Result.failure)했습니다. 최근 CONTEXT_LEN개의 epoch를 슬라이딩 버퍼로 유지합니다.
    private val epochBuffer = ArrayDeque<List<FloatArray>>()

    fun analyzeWindow(
        sensorData: List<FloatArray>,
        environmentFeature: EnvironmentFeature?,
        sessionStartTimeMs: Long,
    ): Result<SleepAnalysis> {
        if (!classifier.isReady()) {
            Napier.d(tag = TAG, message = "모델 미초기화")
            return Result.failure(Exception("모델 미초기화"))
        }

        return try {
            val currentTimeMs = Clock.System.now().toEpochMilliseconds()

            val hrFallback = SleepStageClassifier.ACCEL_CHANNEL_MEAN[SleepStageClassifier.CH_HEART_RATE]

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

                val gx = sample.getOrElse(RAW_GYRO_X) { 0f }
                val gy = sample.getOrElse(RAW_GYRO_Y) { 0f }
                val gz = sample.getOrElse(RAW_GYRO_Z) { 0f }
                val gyroEnergy = sqrt(gx * gx + gy * gy + gz * gz)

                // 💡 심박수 제거(앱단): 더 이상 심박수를 수집하지 않으므로, 온디바이스 모델이
                // 기대하는 채널 shape은 그대로 유지한 채 학습 시점의 채널 평균(중립값)을 채웁니다.
                // 모델 재학습(8→6채널)은 별도 ML 작업입니다.
                val hrValue = hrFallback
                
                floatArrayOf(
                    x,
                    y,
                    z,
                    gyroEnergy,
                    hrValue,
                    noiseValue,
                    mfccEnergy,
                    timeFeature
                )
            }

            val contextLen = SleepStageClassifier.CONTEXT_LEN
            epochBuffer.addLast(expanded)
            while (epochBuffer.size > contextLen) {
                epochBuffer.removeFirst()
            }
            // 세션 초반(버퍼가 아직 60개 미만)에는 가장 오래된 epoch를 앞쪽에 반복 채워서
            // 컨텍스트 길이를 맞춥니다 (resampleTo가 샘플 1개일 때 반복하는 것과 같은 방식).
            val padCount = (contextLen - epochBuffer.size).coerceAtLeast(0)
            val contextWindows = List(padCount) { epochBuffer.first() } + epochBuffer

            val stageIdx = classifier.classifySleepStage(contextWindows)
            var predictionStage = indexToStage(stageIdx)


            val avgGyroEnergy = expanded.map { it[3] }.average().toFloat()
            val avgMotion = SignalProcessor.calculateMovementEnergy(
                floatArrayOf(avgX, avgY, avgZ),
                floatArrayOf(avgGyroEnergy, avgGyroEnergy, avgGyroEnergy)
            )
            
            if (avgMotion > 0.5f && predictionStage == PredictionStageType.N3) {
                Napier.d(tag = TAG, message = "움직임 감지로 인한 추론 보정: N3 -> AWAKE (motion=$avgMotion)")
                predictionStage = PredictionStageType.AWAKE
            }

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

    // 세션이 끝난 뒤 다음 세션에서 이전 세션의 마지막 epoch들이 새 세션의 컨텍스트에
    // 섞여 들어가지 않도록 버퍼를 비웁니다.
    fun resetContext() {
        epochBuffer.clear()
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

    // 🐛 버그 수정: 모델은 4클래스(0=Wake, 1=Light[N1+N2 통합], 2=Deep[N3], 3=REM)로 학습되어
    // N1/N2를 구분하지 않습니다. index 1은 편의상 N1으로 매핑하지만, SleepSessionRepositoryImpl에서
    // N1/N2를 모두 SleepStageType.LIGHT로 합산하므로 최종 리포트 결과에는 영향이 없습니다.
    private fun indexToStage(index: Int): PredictionStageType = when (index) {
        0 -> PredictionStageType.AWAKE
        1 -> PredictionStageType.N1
        2 -> PredictionStageType.N3
        3 -> PredictionStageType.REM
        else -> PredictionStageType.AWAKE
    }
}
