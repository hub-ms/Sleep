package com.sleepytime.shared.platform

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

        // 🐛 버그 수정: 예전에는 16채널(EDF 8 + Accel 8) COMBINED 배열을 썼는데, buildInputBuffer가
        // NUM_CHANNELS(8)만큼만 순회해 실제로는 앞 8개(EDF/EEG용) 값만 참조하고 있었습니다.
        // 이 모델(accel_infer_model 기반 sleep_model.tflite)의 입력은 가속도계 데이터라
        // Accel 도메인 정규화 기준이 필요합니다.
        // compute_stats.py가 bidsleep·sleep_accel 각각의 norm_stats.npy를 단순 평균해 계산한 값입니다.
        actual val ACCEL_CHANNEL_MEAN = floatArrayOf(-0.120757f, -0.043699f, -0.363963f, 0.044877f, 65.443787f, 0.000867f, -0.000003f, 0.449583f)
        actual val ACCEL_CHANNEL_STD = floatArrayOf(0.378034f, 0.514663f, 0.660465f, 0.196010f, 9.678553f, 0.002571f, 0.000835f, 0.292682f)

        // EDF 채널 인덱스
        actual const val CH_EEG1 = 0
        actual const val CH_EEG2 = 1
        actual const val CH_EOG = 2
        actual const val CH_EMG = 3

        // Accel 채널 인덱스
        actual const val CH_ACCEL_X = 0
        actual const val CH_ACCEL_Y = 1
        actual const val CH_ACCEL_Z = 2
        actual const val CH_TILT = 3
        actual const val CH_HEART_RATE = 4
        actual const val CH_HRV = 5
        actual const val CH_MFCC_ENERGY = 6
        actual const val CH_TIME_FEATURE = 7

        private const val WINDOW_SIZE = 1500
        private const val NUM_CHANNELS = 8

        // 🐛 버그 수정: 학습 시(ml/script/build_dataset_hybrid.py, model.py)는 4클래스
        // (0=Wake, 1=Light[N1+N2], 2=Deep[N3+N4], 3=REM)로 학습됐는데, 여기는 5로 선언되어
        // 있어 TFLite 출력 텐서 크기 계산이 실제 모델과 어긋나 있었습니다.
        private const val NUM_CLASSES = 4
    }

    private var interpreter: Interpreter? = null
    private var isInitialized = false

    actual fun classifySleepStage(sensorData: List<List<FloatArray>>): Int {
        if (!isInitialized || interpreter == null) throw IllegalStateException("모델이 초기화되지 않았습니다.")

        val inputBuffer = buildInputBuffer(sensorData)

        // Output Shape: (1, 60, 4) -> 1 * 60 * 4 * 4 bytes
        val outputBuffer = ByteBuffer.allocateDirect(1 * CONTEXT_LEN * NUM_CLASSES * Float.SIZE_BYTES).apply {
            order(ByteOrder.nativeOrder())
        }

        // TFLite 추론 실행
        interpreter?.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()

        // 모델은 컨텍스트로 넘어온 60개 epoch 각각에 대한 클래스 확률을 순서대로 출력합니다.
        // 🐛 버그 수정: 이전에는 60개 epoch x NUM_CLASSES개 확률값 전체를 통틀어 하나의
        // 전역 최댓값(epoch, class) 쌍만 찾았는데, 이는 "현재 수면 단계"와 무관한 값입니다.
        // sensorData의 마지막 원소가 가장 최근(현재) epoch이므로, 그 epoch의 확률 분포에서만
        // argmax를 구해야 "지금" 수면 단계를 얻을 수 있습니다.
        val floatBuffer = outputBuffer.asFloatBuffer()
        floatBuffer.position((CONTEXT_LEN - 1) * NUM_CLASSES)
        val lastEpochProb = FloatArray(NUM_CLASSES)
        floatBuffer.get(lastEpochProb)

        var maxProb = -1.0f
        var bestClassIndex = 0
        for (j in 0 until NUM_CLASSES) {
            if (lastEpochProb[j] > maxProb) {
                maxProb = lastEpochProb[j]
                bestClassIndex = j
            }
        }

        return bestClassIndex
    }

    actual fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
    }

    actual fun isReady() = isInitialized && interpreter != null

    private fun buildInputBuffer(sensorData: List<List<FloatArray>>): ByteBuffer {
        require(sensorData.size == CONTEXT_LEN) {
            "Context 길이는 $CONTEXT_LEN 이어야 합니다. (현재: ${sensorData.size})"
        }

        val buf = ByteBuffer.allocateDirect(1 * CONTEXT_LEN * WINDOW_SIZE * NUM_CHANNELS * Float.SIZE_BYTES).apply {
            order(ByteOrder.nativeOrder())
        }

        for (epochIdx in 0 until CONTEXT_LEN) {
            val epochData = sensorData[epochIdx]
            require(epochData.size == WINDOW_SIZE) {
                "Epoch $epochIdx 의 타임스텝 수가 $WINDOW_SIZE 과 다릅니다. (현재: ${epochData.size})"
            }

            for (timeStep in epochData) {
                require(timeStep.size == NUM_CHANNELS) {
                    "채널 수가 $NUM_CHANNELS 과 다릅니다. (현재: ${timeStep.size})"
                }

                for (ch in 0 until NUM_CHANNELS) {
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

    private fun loadModelFile(assetManager: AssetManager, modelName: String): ByteBuffer {
        val fd = assetManager.openFd(modelName)
        return FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            val assetManager = context.assets
            val modelBuffer = loadModelFile(assetManager, "sleep_model.tflite")
            interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
                setNumThreads(4)
            })
            interpreter?.allocateTensors()
            isInitialized = true
            Log.d("SleepStageClassifier", "TFLite 모델 초기화 성공")
        } catch (e: Exception) {
            Log.e("SleepStageClassifier", "모델 인스턴스 초기화 에러", e)
            close()
        }
    }
}
