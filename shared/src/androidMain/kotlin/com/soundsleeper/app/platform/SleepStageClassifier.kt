package com.soundsleeper.app.platform

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

actual class SleepStageClassifier {
    actual companion object {
        actual const val CONTEXT_LEN = 60

        // ⚠️ 재생성 필요: 아래 값은 **8채널 시절**(심박 포함, 소스별 통계의 산술평균) 기준에서
        // 심박 항목만 빼낸 임시값입니다. Phase 1에서 세 가지가 바뀌었습니다.
        //   1. 채널 8 -> 7 (심박 제거)
        //   2. 정규화 기준: bidsleep/sleep_accel 산술평균 -> 두 소스를 합쳐 한 번에 계산한 단일 통계
        //   3. time_feature 정의: `경과 / 야간_전체_길이` -> `min(경과초 / 28800, 1.0)`
        // 2와 3 때문에 실제 숫자가 달라집니다(특히 마지막 time_feature 항목).
        //
        // 올바른 값을 얻는 방법:
        //   python ml/script/build_dataset_hybrid.py   # 7채널로 전처리 재생성
        //   python ml/script/compute_stats.py          # 통계 계산 + 이 두 줄과 비교·출력
        // compute_stats.py가 이 파일을 직접 읽어 불일치를 알려주고 붙여넣을 리터럴을 출력합니다.
        actual val ACCEL_CHANNEL_MEAN = floatArrayOf(-0.120757f, -0.043699f, -0.363963f, 0.044877f, 0.000867f, -0.000003f, 0.449583f)
        actual val ACCEL_CHANNEL_STD = floatArrayOf(0.378034f, 0.514663f, 0.660465f, 0.196010f, 0.002571f, 0.000835f, 0.292682f)

        // 신규(Phase 8) — 오디오(멜스펙토그램) 입력 정규화 상수. PSG-Audio를 데이터셋에서
        // 제외해 오디오를 지도학습하는 스테이지 자체가 없으므로 아직 비어 있습니다.
        // ml/script의 audio_denoise_aux(APSAA/AI-Hub) 전처리가 끝나고
        // preprocess.compute_audio_domain_stats()가 통계를 계산하면(라벨 불필요), ACCEL_CHANNEL_MEAN/STD와
        // 같은 방식(사람이 값을 복사해 넣는 방식)으로 채워야 합니다. 비어 있는 동안은 아래
        // isAudioCapable()이 false를 반환해 오디오 입력 경로 자체가 쓰이지 않습니다.
        actual val AUDIO_MEL_MEAN = floatArrayOf()
        actual val AUDIO_MEL_STD = floatArrayOf()

        // ── mock 모드 상수 (실제 모델이 없을 때만 쓰임, mockModeEnabled 참고) ──────────────
        // SleepAnalyzer.indexToStage 의 매핑과 같아야 한다: 0=AWAKE, 1=N1(→LIGHT), 2=N3(→DEEP), 3=REM.
        private const val CLASS_AWAKE = 0
        private const val CLASS_LIGHT = 1
        private const val CLASS_DEEP = 2
        private const val CLASS_REM = 3

        /** 가짜 수면그래프의 시간 압축 배율. 18이면 실제 5분이 가상 90분(주기 1회)이 된다. */
        private const val MOCK_TIME_SCALE = 18.0

        /** 가상 수면주기 길이(분) — 실제 사람의 NREM/REM 주기와 같은 90분. */
        private const val MOCK_CYCLE_MIN = 90.0

        /** 가상 입면 잠복기(분). MOCK_TIME_SCALE=18에서 실제 약 33초에 해당한다. */
        private const val MOCK_SLEEP_LATENCY_MIN = 10.0
    }

    private var interpreter: Interpreter? = null
    private var isInitialized = false

    /**
     * 실제 TFLite 모델 없이 가짜 수면단계를 돌려주는 모드([isMockMode]로 조회). [initialize]에 allowMock=true로
     * 들어왔고 assets의 sleep_model.tflite 로드가 실패했을 때만 켜진다.
     *
     * ⚠️ 왜 필요한가: 모델이 없으면 isReady()가 false가 되고, 그러면
     * SleepSessionRepositoryImpl.initializeModel()이 실패를 반환해
     * AndroidTrackingManager.performStart()가 `if (!initializeModel()) return@launch`에서
     * 측정을 아예 시작하지 않는다. 즉 모델 파일 하나가 없다는 이유로 측정→종료→리포트
     * 흐름 전체를 손으로 확인할 수 없다. MESA/AI-Hub 데이터셋 승인을 기다리는 동안
     * 센서 수집·특징 계산(SleepFeatureBuilder)·타임라인 병합·DB 저장·화면 이동까지
     * **추론을 제외한 전부**를 실제 코드로 돌리기 위한 임시 경로다.
     *
     * assets에 실제 sleep_model.tflite를 넣으면 initialize()가 성공해 이 플래그는 false로
     * 남고 mock 경로는 자동으로 죽는다 — 코드를 되돌릴 필요가 없다. 또한 릴리즈 빌드는
     * allowMock=false(AndroidModule이 AppConfig.isDebug를 넘긴다)라 가짜 리포트가
     * 사용자에게 나가지 않는다.
     */
    private var mockModeEnabled = false

    /** [mockModeEnabled] 조회용. AndroidTrackingManager가 최소 측정시간 임계값을 낮출 때 참조한다. */
    fun isMockMode() = mockModeEnabled

    /** 로드된 모델이 입력을 1개보다 많이 받는지(미래의 audio+accel fusion 모델인지). [initialize]에서 설정된다. */
    private var inputTensorCount = 1

    /** 오디오 입력 경로를 실제로 쓸 수 있는 상태인지 — 모델이 다중 입력이고, 정규화 상수도 채워져 있어야 한다. */
    private fun isAudioCapable(): Boolean = inputTensorCount > 1 && AUDIO_MEL_MEAN.isNotEmpty() && AUDIO_MEL_STD.isNotEmpty()

    /**
     * TFLite 모델로 추론을 실행한다. 모델이 입력을 1개만 받으면(지금 실제로 배포된 모든 모델)
     * accel 단독 경로([buildAccelInputBuffer])를 그대로 쓴다. 모델이 여러 입력을 받으면(미래의
     * audio+accel fusion 모델) 각 입력 텐서의 shape을 보고 accel/audio/기타(time_bucket 등)
     * 무엇인지 런타임에 판별해 채운다 — 입력 순서를 하드코딩하지 않아 TFLite 변환이 실제로
     * 어떤 순서를 쓰는지와 무관하게 동작한다. 추론 뒤에는 가장 최근(마지막) epoch의 클래스
     * 확률만 뽑아 argmax로 최종 클래스 인덱스를 구한다.
     */
    actual fun classifySleepStage(
        accelData: List<List<FloatArray>>,
        audioMelData: List<List<FloatArray>>?,
        timeBucketId: Int,
    ): Int {
        if (mockModeEnabled) return mockStage(accelData)
        if (!isInitialized || interpreter == null) throw IllegalStateException("모델이 초기화되지 않았습니다.")
        val interp = interpreter!!

        // Output Shape: (1, CONTEXT_LEN, NUM_CLASSES)
        val outputBuffer = ByteBuffer
            .allocateDirect(1 * CONTEXT_LEN * AccelChannels.NUM_CLASSES * Float.SIZE_BYTES)
            .apply { order(ByteOrder.nativeOrder()) }

        if (audioMelData != null && isAudioCapable()) {
            val inputs = arrayOfNulls<Any>(inputTensorCount)
            for (i in 0 until inputTensorCount) {
                val shape = interp.getInputTensor(i).shape()
                inputs[i] = when {
                    // (1, CONTEXT_LEN, WINDOW_SIZE, NUM_CHANNELS) — accel 입력.
                    shape.size == 4 && shape.getOrNull(1) == CONTEXT_LEN &&
                        shape.getOrNull(2) == AccelChannels.WINDOW_SIZE ->
                        buildAccelInputBuffer(accelData)
                    // (1, CONTEXT_LEN, nMelFrames, nMels, 1) — 오디오(멜스펙토그램) 입력.
                    shape.size == 5 && shape.getOrNull(1) == CONTEXT_LEN ->
                        buildAudioInputBuffer(audioMelData)
                    // 그 외(예: time_bucket_id 스칼라) — 호출부(SleepAnalyzer)가
                    // SleepFeatureBuilder.timeFeatureBucket()으로 계산해 넘긴 실제 값을 쓴다.
                    else ->
                        ByteBuffer.allocateDirect(Int.SIZE_BYTES).order(ByteOrder.nativeOrder()).apply {
                            putInt(timeBucketId)
                            rewind()
                        }
                }
            }
            interp.runForMultipleInputsOutputs(inputs, mutableMapOf<Int, Any>(0 to outputBuffer))
        } else {
            val inputBuffer = buildAccelInputBuffer(accelData)
            interp.run(inputBuffer, outputBuffer)
        }

        outputBuffer.rewind()

        // 모델은 컨텍스트로 넘어온 60개 epoch 각각에 대한 클래스 확률을 순서대로 출력합니다.
        // 🐛 버그 수정: 이전에는 60개 epoch x NUM_CLASSES개 확률값 전체를 통틀어 하나의
        // 전역 최댓값(epoch, class) 쌍만 찾았는데, 이는 "현재 수면 단계"와 무관한 값입니다.
        // sensorData의 마지막 원소가 가장 최근(현재) epoch이므로, 그 epoch의 확률 분포에서만
        // argmax를 구해야 "지금" 수면 단계를 얻을 수 있습니다.
        val floatBuffer = outputBuffer.asFloatBuffer()
        floatBuffer.position((CONTEXT_LEN - 1) * AccelChannels.NUM_CLASSES)
        val lastEpochProb = FloatArray(AccelChannels.NUM_CLASSES)
        floatBuffer.get(lastEpochProb)

        var maxProb = -1.0f
        var bestClassIndex = 0
        for (j in 0 until AccelChannels.NUM_CLASSES) {
            if (lastEpochProb[j] > maxProb) {
                maxProb = lastEpochProb[j]
                bestClassIndex = j
            }
        }

        return bestClassIndex
    }

    /** TFLite 인터프리터를 닫고 상태를 초기화한다. */
    actual fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
        mockModeEnabled = false
        inputTensorCount = 1
    }

    /** 인터프리터가 초기화되어 추론 가능한 상태인지 반환한다. */
    actual fun isReady() = mockModeEnabled || (isInitialized && interpreter != null)

    /** 센서 데이터를 z-score 정규화해 모델이 기대하는 평평한 ByteBuffer(1×CONTEXT_LEN×WINDOW_SIZE×NUM_CHANNELS)로 만든다. */
    private fun buildAccelInputBuffer(sensorData: List<List<FloatArray>>): ByteBuffer {
        require(sensorData.size == CONTEXT_LEN) {
            "Context 길이는 $CONTEXT_LEN 이어야 합니다. (현재: ${sensorData.size})"
        }
        // 💡 정규화 배열 길이가 채널 수와 다르면 조용히 잘못된 정규화를 하게 됩니다
        // (과거에 16채널 배열의 앞 8개만 읽던 버그가 정확히 이 모양이었습니다). 명시적으로 막습니다.
        require(ACCEL_CHANNEL_MEAN.size == AccelChannels.NUM_CHANNELS && ACCEL_CHANNEL_STD.size == AccelChannels.NUM_CHANNELS) {
            "정규화 배열 길이가 채널 수(${AccelChannels.NUM_CHANNELS})와 다릅니다. " +
                "(mean=${ACCEL_CHANNEL_MEAN.size}, std=${ACCEL_CHANNEL_STD.size}) " +
                "— ml/script/compute_stats.py로 재생성하세요."
        }

        val buf = ByteBuffer
            .allocateDirect(1 * CONTEXT_LEN * AccelChannels.WINDOW_SIZE * AccelChannels.NUM_CHANNELS * Float.SIZE_BYTES)
            .apply { order(ByteOrder.nativeOrder()) }

        for (epochIdx in 0 until CONTEXT_LEN) {
            val epochData = sensorData[epochIdx]
            require(epochData.size == AccelChannels.WINDOW_SIZE) {
                "Epoch $epochIdx 의 타임스텝 수가 ${AccelChannels.WINDOW_SIZE} 과 다릅니다. (현재: ${epochData.size})"
            }

            for (timeStep in epochData) {
                require(timeStep.size == AccelChannels.NUM_CHANNELS) {
                    "채널 수가 ${AccelChannels.NUM_CHANNELS} 과 다릅니다. (현재: ${timeStep.size})"
                }

                for (ch in 0 until AccelChannels.NUM_CHANNELS) {
                    val value = timeStep[ch]
                    val mean = ACCEL_CHANNEL_MEAN[ch]
                    val std = ACCEL_CHANNEL_STD[ch]

                    // Z-score Normalization (std가 0인 경우 대비 안전 처리)
                    val normalizedValue = if (std != 0f) (value - mean) / std else 0f
                    buf.putFloat(normalizedValue)
                }
            }
        }

        buf.rewind()
        return buf
    }

    /**
     * 오디오 멜스펙토그램 입력을 z-score 정규화해 모델이 기대하는 평평한 ByteBuffer
     * (1×CONTEXT_LEN×nMelFrames×nMels×1)로 만든다. nMelFrames/nMels는 audioMelData의 실제
     * shape에서 읽는다(모델 쪽 하이퍼파라미터가 바뀌어도 이 함수를 고칠 필요가 없도록).
     */
    private fun buildAudioInputBuffer(audioMelData: List<List<FloatArray>>): ByteBuffer {
        require(audioMelData.size == CONTEXT_LEN) {
            "오디오 Context 길이는 $CONTEXT_LEN 이어야 합니다. (현재: ${audioMelData.size})"
        }
        val nMelFrames = audioMelData[0].size
        val nMels = audioMelData[0].getOrNull(0)?.size ?: 0
        require(AUDIO_MEL_MEAN.size == nMels && AUDIO_MEL_STD.size == nMels) {
            "AUDIO_MEL_MEAN/STD 길이(${AUDIO_MEL_MEAN.size}/${AUDIO_MEL_STD.size})가 멜빈 수($nMels)와 다릅니다 " +
                "— 오디오 모델을 배포하려면 이 상수를 먼저 채워야 합니다(ml/script/preprocess.py의 compute_audio_domain_stats)."
        }

        val buf = ByteBuffer
            .allocateDirect(1 * CONTEXT_LEN * nMelFrames * nMels * Float.SIZE_BYTES)
            .apply { order(ByteOrder.nativeOrder()) }

        for (epochIdx in 0 until CONTEXT_LEN) {
            for (frame in audioMelData[epochIdx]) {
                for (melIdx in 0 until nMels) {
                    val mean = AUDIO_MEL_MEAN[melIdx]
                    val std = AUDIO_MEL_STD[melIdx]
                    buf.putFloat(if (std != 0f) (frame[melIdx] - mean) / std else 0f)
                }
            }
        }
        buf.rewind()
        return buf
    }

    /** Android assets에서 .tflite 모델 파일을 메모리 매핑으로 읽어온다(복사 없이 바로 참조). */
    private fun loadModelFile(assetManager: AssetManager, modelName: String): ByteBuffer {
        val fd = assetManager.openFd(modelName)
        return FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    /**
     * 앱 assets의 sleep_model.tflite를 로드해 인터프리터를 만든다.
     * 모델이 입력을 1개만 받으면(지금 실제로 배포된 모든 모델) 그 shape이 현재 채널 레이아웃
     * (CONTEXT_LEN×WINDOW_SIZE×NUM_CHANNELS)과 맞는지 엄격히 확인해 초기화를 실패시킨다 —
     * 추론 시점에야 드러나는 혼란스러운 실패 대신 바로 원인을 알 수 있게 한다. 모델이 입력을
     * 여러 개 받으면(미래의 audio+accel fusion 모델) 정확한 shape 조합을 아직 모르므로
     * 엄격한 검사는 생략하고 입력 개수만 기록한다 — 실제 검증은 classifySleepStage의
     * shape 기반 라우팅이 담당한다.
     */
    fun initialize(context: Context, allowMock: Boolean = false) {
        if (isInitialized) return
        try {
            val assetManager = context.assets
            val modelBuffer = loadModelFile(assetManager, "sleep_model.tflite")
            interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
                setNumThreads(4)
            })
            interpreter?.allocateTensors()
            inputTensorCount = interpreter?.inputTensorCount ?: 1

            // 💡 모델 입력 shape이 현재 앱의 채널 레이아웃과 맞는지 초기화 시점에 확인합니다.
            // 7채널 모델로 재학습하기 전 8채널 .tflite가 남아 있으면 여기서 바로 드러납니다
            // (이전에는 추론 시점에 ByteBuffer 크기 불일치로 엉뚱하게 실패했습니다). 다중 입력
            // 모델(audio+accel fusion)은 아직 정확한 shape 계약이 없으므로 이 엄격한 검사를
            // 적용하지 않습니다.
            val inputShape = if (inputTensorCount == 1) interpreter?.getInputTensor(0)?.shape() else null
            if (inputShape != null && inputShape.size == 4) {
                val expected = intArrayOf(1, CONTEXT_LEN, AccelChannels.WINDOW_SIZE, AccelChannels.NUM_CHANNELS)
                if (!inputShape.contentEquals(expected)) {
                    Log.e(
                        "SleepStageClassifier",
                        "모델 입력 shape ${inputShape.joinToString()} != 기대값 ${expected.joinToString()} " +
                            "— sleep_model.tflite를 현재 채널 구성으로 다시 변환해야 합니다 " +
                            "(ml/script/convert.py)."
                    )
                    close()
                    enableMockFallback(allowMock, "모델 입력 shape이 현재 채널 구성과 다릅니다")
                    return
                }
            }

            isInitialized = true
            Log.d("SleepStageClassifier", "TFLite 모델 초기화 성공")
        } catch (e: Exception) {
            Log.e("SleepStageClassifier", "모델 인스턴스 초기화 에러", e)
            close()
            enableMockFallback(allowMock, "assets/sleep_model.tflite 를 로드할 수 없습니다 (${e::class.simpleName})")
        }
    }

    /**
     * 모델 로드가 실패했을 때 mock 모드로 넘어갈지 결정한다. [allowMock]이 false면(릴리즈 빌드)
     * 아무것도 하지 않고 false를 돌려주므로 기존 동작(측정 거부)이 그대로 유지된다.
     */
    private fun enableMockFallback(allowMock: Boolean, reason: String): Boolean {
        if (!allowMock) return false
        mockModeEnabled = true
        isInitialized = true
        Log.w(
            "SleepStageClassifier",
            "⚠️ MOCK 수면단계 모드로 동작합니다 — 실제 추론이 아닙니다. 사유: $reason. " +
                "ml/script로 학습한 모델을 androidApp/src/main/assets/sleep_model.tflite 에 넣으면 " +
                "자동으로 실제 추론으로 전환됩니다."
        )
        return true
    }

    /**
     * 실제 추론을 대신하는 결정론적 가짜 수면그래프. 상태를 두지 않고 입력만으로 계산하므로
     * 세션마다 저절로 처음부터 시작한다(리셋 훅이 필요 없다).
     *
     * 근거: [accelData]의 마지막 epoch, 마지막 샘플의 [AccelChannels.CH_TIME_FEATURE] 값이 곧
     * `min(세션 경과 ms / TIME_FEATURE_SPAN_MS, 1.0)`(SleepFeatureBuilder.timeFeature)이라
     * 단조 증가하는 세션 시계로 그대로 쓸 수 있다.
     *
     * [MOCK_TIME_SCALE]배로 시간을 압축해, **실제 5분 측정이 가상 90분(수면주기 1회)**에
     * 대응한다 — 짧은 수동 테스트로도 Wake/Light/Deep/REM 네 단계와 중간 각성이 모두 나와
     * 리포트 화면의 그래프·점수·기상횟수를 한 번에 확인할 수 있다.
     *
     * ⚠️ time_feature는 실제 8시간에서 1.0으로 saturate하므로, 8시간을 넘겨 측정하면 가짜
     * 단계가 마지막 값에 고정된다(mock 전용 한계).
     */
    private fun mockStage(accelData: List<List<FloatArray>>): Int {
        val timeFeature = accelData.lastOrNull()?.lastOrNull()
            ?.getOrNull(AccelChannels.CH_TIME_FEATURE) ?: 0f
        val virtualMin =
            timeFeature * (AccelChannels.TIME_FEATURE_SPAN_MS / 1000.0 / 60.0) * MOCK_TIME_SCALE

        // 입면 잠복기 — SleepSessionFactory/SleepStatisticsCalculator가 sleepLatencyMinutes를
        // 뽑아낼 수 있도록 세션 앞머리에 AWAKE 구간을 만든다.
        if (virtualMin < MOCK_SLEEP_LATENCY_MIN) return CLASS_AWAKE

        val inCycle = (virtualMin - MOCK_SLEEP_LATENCY_MIN) % MOCK_CYCLE_MIN
        return when {
            inCycle < 35.0 -> CLASS_LIGHT
            inCycle < 60.0 -> CLASS_DEEP
            inCycle < 70.0 -> CLASS_LIGHT
            inCycle < 88.0 -> CLASS_REM
            else -> CLASS_AWAKE  // 주기 끝 2분간 중간 각성 — wakeCount가 0이 아니게 된다
        }
    }
}
