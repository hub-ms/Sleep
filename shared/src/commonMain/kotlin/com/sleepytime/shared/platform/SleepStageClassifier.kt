package com.sleepytime.shared.platform

expect class SleepStageClassifier {
    companion object {

        // 온디바이스 모델이 한 번에 필요로 하는 시퀀스 길이(epoch 개수).
        // SleepAnalyzer는 이 길이만큼 epoch를 모아서 classifySleepStage()를 호출해야 합니다.
        val CONTEXT_LEN: Int

        val COMBINED_CHANNEL_MEAN: FloatArray
        val COMBINED_CHANNEL_STD: FloatArray

        // EDF 채널 인덱스
        val CH_EEG1: Int
        val CH_EEG2: Int
        val CH_EOG: Int
        val CH_EMG: Int

        // Accel 채널 인덱스
        val CH_ACCEL_X: Int
        val CH_ACCEL_Y: Int
        val CH_ACCEL_Z: Int
        val CH_TILT: Int
        val CH_HEART_RATE: Int
        val CH_HRV: Int
        val CH_MFCC_ENERGY: Int
        val CH_TIME_FEATURE: Int
    }

    fun classifySleepStage(sensorData: List<List<FloatArray>>): Int
    fun close()
    fun isReady(): Boolean
}
