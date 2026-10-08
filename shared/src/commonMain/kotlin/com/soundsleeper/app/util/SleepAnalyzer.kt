package com.soundsleeper.app.util

import com.soundsleeper.app.platform.AccelChannels
import com.soundsleeper.app.platform.SleepStageClassifier
import com.soundsleeper.app.domain.model.EnvironmentFeature
import com.soundsleeper.app.domain.model.SleepAnalysis
import com.soundsleeper.app.enum_.PredictionStageType
import io.github.aakira.napier.Napier
import kotlin.time.Clock
import kotlin.math.sqrt

private const val TAG = "SleepAnalyzer"
private const val RAW_GYRO_X = 3
private const val RAW_GYRO_Y = 4
private const val RAW_GYRO_Z = 5

/**
 * 측정 중 30초마다 들어오는 원시 센서 윈도우를 실제 수면 단계로 분류하는 핵심 클래스.
 * [AndroidTrackingManager.handleWindow]가 매 윈도우마다 [analyzeWindow]를 호출하며,
 * 내부적으로 특징 계산([SleepFeatureBuilder])과 모델 추론([SleepStageClassifier])을 연결한다.
 */
class SleepAnalyzer(private val classifier: SleepStageClassifier) {

    // 🐛 버그 수정: 모델은 한 번에 CONTEXT_LEN(60)개의 연속된 epoch를 컨텍스트로 요구하는데,
    // 이전에는 매번 새로 들어온 epoch 1개만 담아(listOf(expanded)) classifySleepStage를 호출해서
    // SleepStageClassifier의 require(sensorData.size == CONTEXT_LEN) 체크에 항상 걸려 추론이
    // 조용히 실패(Result.failure)했습니다. 최근 CONTEXT_LEN개의 epoch를 슬라이딩 버퍼로 유지합니다.
    private val epochBuffer = ArrayDeque<List<FloatArray>>()

    // 💡 channels.py의 recent_epoch_means(deque)와 동일한 역할 — 과거 epoch들의
    // 평균 활동량(가속도 벡터 크기)만 누적하는 인과적(미래 데이터 미사용) 버퍼입니다.
    private val recentEpochActivityMeans = ArrayDeque<Float>()

    // 신규(Phase 8): accel의 epochBuffer와 같은 역할을 오디오 쪽에도 둡니다 — 최근
    // CONTEXT_LEN개의 멜스펙토그램을 슬라이딩 버퍼로 유지합니다. 모델이 아직 오디오를
    // 받지 않는 동안(지금 상태)에는 이 버퍼가 쌓이기만 하고 classifySleepStage 호출에는
    // 영향을 주지 않습니다(그 함수가 audioMelData=null일 때와 똑같이 동작).
    private val audioEpochBuffer = ArrayDeque<Array<FloatArray>>()

    /**
     * 한 윈도우(30초)의 원시 7채널 센서 샘플을 받아 수면 단계를 추론한다.
     * 순서: 리샘플링 → g단위 변환 → 활동 추세/변동성 계산 → 7채널 모델 입력 조립 →
     * 최근 CONTEXT_LEN개 epoch 버퍼에 추가 → 모델 추론 → 움직임 기반 보정(N3 오탐 방지).
     * 모델이 아직 준비되지 않았으면 즉시 실패를 반환한다.
     *
     * [rawAudioEpochSamples]는 신규(Phase 8) — 같은 30초 구간의 raw 오디오(16kHz로 리샘플링된
     * mono 샘플, [SensorBridge.consumeLatestAudioEpoch]가 제공)를 받으면 멜스펙토그램으로
     * 변환해 함께 로깅/버퍼링한다. 기본값 null은 기존 호출부(오디오 없이 accel만 분석하던
     * 경로)를 전혀 바꾸지 않는다.
     */
    fun analyzeWindow(
        sensorData: List<FloatArray>,
        environmentFeature: EnvironmentFeature?,
        sessionStartTimeMs: Long,
        rawAudioEpochSamples: FloatArray? = null,
    ): Result<SleepAnalysis> {
        if (!classifier.isReady()) {
            Napier.d(tag = TAG, message = "모델 미초기화")
            return Result.failure(Exception("모델 미초기화"))
        }

        return try {
            val currentTimeMs = Clock.System.now().toEpochMilliseconds()

            // 💡 Phase 1: 학습 쪽 time_feature도 같은 식(min(경과/8시간, 1.0))으로 바꿨습니다.
            // 이전 학습 정의는 `경과 / 야간_전체_길이`라 밤이 끝나야 알 수 있는 값이었고(추론 시
            // 계산 불가 + 라벨 누수), 앱의 이 정의와 분포 자체가 달랐습니다.
            val timeFeature = SleepFeatureBuilder.timeFeature(currentTimeMs - sessionStartTimeMs)

            val resampled = SleepFeatureBuilder.resampleTo(sensorData)

            // 🐛 단위 불일치 수정: Android 가속도계는 m/s²(정지 시 ≈9.81)인데 학습 데이터인
            // Apple Watch 가속도는 g(정지 시 ≈1.0)입니다. 변환 없이 넣고 있었기 때문에 모델은
            // 학습 때보다 약 9.8배 큰 값을 받았고, 정규화하면 -14 sigma 수준으로 분포를
            // 완전히 벗어났습니다. 모델 입력용으로만 g로 변환합니다 — 아래 움직임 보정
            // 로직(SignalProcessor, 9.81을 빼는 계산)은 계속 m/s² 값을 써야 합니다.
            val modelInput = SleepFeatureBuilder.toGravityUnits(resampled)

            val currentEpochActivityMean = SleepFeatureBuilder.epochActivityMean(modelInput)
            val (activityTrend, activityVariability) =
                SleepFeatureBuilder.computeCausalTrendVariability(
                    recentEpochActivityMeans.toList(), currentEpochActivityMean
                )
            recentEpochActivityMeans.addLast(currentEpochActivityMean)
            while (recentEpochActivityMeans.size > AccelChannels.ACTIVITY_LOOKBACK_EPOCHS) {
                recentEpochActivityMeans.removeFirst()
            }

            // 🐛 버그 수정: 학습 데이터의 tilt 채널은 가속도 x/y/z가 이 epoch 평균에서 얼마나
            // 벗어났는지를 뜻하는데(|x-avgX|+|y-avgY|+|z-avgZ|), 여기서는 자이로스코프
            // 크기(gyroEnergy)를 대신 넣고 있어 학습/추론 채널의 물리적 의미가 완전히 달랐습니다.
            // gyroEnergy는 모델 입력이 아니라 아래 움직임 보정 로직 전용으로 따로 계산합니다.
            val avgGyroEnergy = resampled.map { sample ->
                val gx = sample.getOrElse(RAW_GYRO_X) { 0f }
                val gy = sample.getOrElse(RAW_GYRO_Y) { 0f }
                val gz = sample.getOrElse(RAW_GYRO_Z) { 0f }
                sqrt(gx * gx + gy * gy + gz * gz)
            }.average().toFloat()

            // 💡 Phase 1: 심박 채널을 제거해 7채널이 되었습니다. 앱은 심박을 수집하지 않으므로
            // 이전에는 학습셋 평균(상수 65.44)을 그 자리에 꽂고 있었는데, 학습에서는 실측 심박을
            // 쓰고 서빙에서는 상수를 넣으면 그 채널의 기여분이 전부 손실됩니다. 모델을 7채널로
            // 재학습해 학습과 서빙을 일치시켰습니다.
            val expanded = SleepFeatureBuilder.buildEpochChannels(
                modelInput, activityVariability, activityTrend, timeFeature
            )

            val contextLen = SleepStageClassifier.CONTEXT_LEN
            epochBuffer.addLast(expanded)
            while (epochBuffer.size > contextLen) {
                epochBuffer.removeFirst()
            }
            // 세션 초반(버퍼가 아직 60개 미만)에는 가장 오래된 epoch를 앞쪽에 반복 채워서
            // 컨텍스트 길이를 맞춥니다. 학습/평가 쪽(evaluation.predict_night_probs)도 녹화가
            // 컨텍스트보다 짧을 때 같은 방식으로 앞을 패딩합니다.
            val padCount = (contextLen - epochBuffer.size).coerceAtLeast(0)
            val contextWindows = List(padCount) { epochBuffer.first() } + epochBuffer

            // 신규(Phase 8): 오디오 샘플이 왔으면 멜스펙토그램으로 변환해 accel과 같은 방식으로
            // 슬라이딩 버퍼에 쌓고 패딩한다. 아직 실제 오디오 모델이 없어 classifySleepStage는
            // 이 값을 받아도 내부적으로 무시하지만(isAudioCapable()==false), 멜 계산 자체가
            // 정상 shape으로 매 epoch 로깅되는지는 여기서 확인할 수 있다.
            val audioContextWindows = rawAudioEpochSamples?.let { samples ->
                val mel = SleepFeatureBuilder.audioEpochToMelSpectrogram(samples)
                Napier.d(tag = TAG, message = "오디오 멜스펙토그램: ${mel.size}프레임 x ${mel.firstOrNull()?.size ?: 0}멜")
                audioEpochBuffer.addLast(mel)
                while (audioEpochBuffer.size > contextLen) {
                    audioEpochBuffer.removeFirst()
                }
                val audioPadCount = (contextLen - audioEpochBuffer.size).coerceAtLeast(0)
                (List(audioPadCount) { audioEpochBuffer.first() } + audioEpochBuffer).map { it.toList() }
            }

            // 신규: 멀티입력(fusion) 모델의 time_bucket_id 입력용. 이미 위에서 계산한
            // timeFeature(0~1, "밤 안에서 지금이 언제인지")를 ml/script/preprocess.py의
            // time_feature_bucket()과 같은 식으로 정수 버킷으로 양자화한다. 지금(단일 입력
            // accel-only 모델)은 쓰이지 않지만, 분류기가 이미 런타임에 모델 입력 개수를
            // 감지해 라우팅하므로 미리 실제 값을 넘겨 둔다.
            val timeBucketId = SleepFeatureBuilder.timeFeatureBucket(timeFeature)
            val stageIdx = classifier.classifySleepStage(contextWindows, audioContextWindows, timeBucketId)
            var predictionStage = indexToStage(stageIdx)

            // 아래 움직임 보정 로직 전용 평균값 (모델 입력과는 별개)
            val avgX = resampled.map { it.getOrElse(AccelChannels.CH_ACCEL_X) { 0f } }.average().toFloat()
            val avgY = resampled.map { it.getOrElse(AccelChannels.CH_ACCEL_Y) { 0f } }.average().toFloat()
            val avgZ = resampled.map { it.getOrElse(AccelChannels.CH_ACCEL_Z) { 1f } }.average().toFloat()

            val avgMotion = SignalProcessor.calculateMovementEnergy(
                floatArrayOf(avgX, avgY, avgZ),
                floatArrayOf(avgGyroEnergy, avgGyroEnergy, avgGyroEnergy)
            )

            if (avgMotion > 0.5f && predictionStage == PredictionStageType.N3) {
                Napier.d(tag = TAG, message = "움직임 감지로 인한 추론 보정: N3 -> AWAKE (motion=$avgMotion)")
                predictionStage = PredictionStageType.AWAKE
            }

            val confidence = 1.0f

            Napier.d(
                tag = TAG,
                message = "추론 성공: $predictionStage (tilt/variability/trend/time=" +
                    "${expanded.first()[AccelChannels.CH_TILT]}/$activityVariability/" +
                    "$activityTrend/$timeFeature)"
            )

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

    /** 분류기(TFLite 인터프리터 등 네이티브 리소스)를 해제한다. */
    fun close() {
        classifier.close()
    }

    /**
     * 세션이 끝난 뒤 다음 세션에서 이전 세션의 마지막 epoch들이 새 세션의 컨텍스트에
     * 섞여 들어가지 않도록 버퍼를 비웁니다.
     */
    fun resetContext() {
        epochBuffer.clear()
        recentEpochActivityMeans.clear()
        audioEpochBuffer.clear()
    }

    /** 모델(분류기)이 추론 가능한 상태인지 반환한다. */
    fun isReady(): Boolean = classifier.isReady()

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
