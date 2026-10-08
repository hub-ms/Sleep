package com.soundsleeper.app.platform

/**
 * 이 플랫폼에는 온디바이스 모델을 아직 배포하지 않습니다 — isReady()가 항상 false이므로
 * SleepAnalyzer가 추론을 시도하지 않습니다.
 *
 * 💡 채널 인덱스 상수는 [AccelChannels](commonMain)로 옮겨 이 파일에서 중복 선언하지 않습니다.
 */
actual class SleepStageClassifier {
    actual companion object {
        actual const val CONTEXT_LEN = 60

        // 모델이 없으므로 정규화 기준도 비어 있습니다. isReady()가 false라 참조되지 않습니다.
        actual val ACCEL_CHANNEL_MEAN = floatArrayOf()
        actual val ACCEL_CHANNEL_STD = floatArrayOf()
        actual val AUDIO_MEL_MEAN = floatArrayOf()
        actual val AUDIO_MEL_STD = floatArrayOf()
    }

    actual fun classifySleepStage(
        accelData: List<List<FloatArray>>,
        audioMelData: List<List<FloatArray>>?,
        timeBucketId: Int,
    ): Int = 0
    actual fun close() {}
    actual fun isReady(): Boolean = false
}
