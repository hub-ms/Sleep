package com.sleepytime.shared.platform

actual class SleepStageClassifier {
    actual companion object {
        actual const val CONTEXT_LEN = 60

        actual val COMBINED_CHANNEL_MEAN = floatArrayOf()
        actual val COMBINED_CHANNEL_STD  = floatArrayOf()

        actual const val CH_EEG1 = 0
        actual const val CH_EEG2 = 1
        actual const val CH_EOG = 2
        actual const val CH_EMG = 3

        actual const val CH_ACCEL_X = 0
        actual const val CH_ACCEL_Y = 1
        actual const val CH_ACCEL_Z = 2
        actual const val CH_TILT = 3
        actual const val CH_HEART_RATE = 4
        actual const val CH_HRV = 5
        actual const val CH_MFCC_ENERGY = 6
        actual const val CH_TIME_FEATURE = 7
    }

    actual fun classifySleepStage(sensorData: List<List<FloatArray>>): Int = 0
    actual fun close() {}
    actual fun isReady(): Boolean = false
}
