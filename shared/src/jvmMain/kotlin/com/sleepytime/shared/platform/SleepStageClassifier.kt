package com.sleepytime.shared.platform

actual class SleepStageClassifier {
    actual companion object {
        actual val CHANNEL_MEAN = floatArrayOf(-0.0000f, 0.0000f, 9.7986f, 68.5769f, 40.0434f, 10.8971f, 23.5710f, 50.3241f)
        actual val CHANNEL_STD  = floatArrayOf(0.1668f, 0.1668f, 0.2047f, 9.1942f, 8.9610f, 6.3135f, 3.1568f, 11.4427f)

        actual const val CH_ACCEL_X     = 0
        actual const val CH_ACCEL_Y     = 1
        actual const val CH_ACCEL_Z     = 2
        actual const val CH_TILT_ANGLE  = 3
        actual const val CH_HEART_RATE  = 4
        actual const val CH_MIC         = 5
        actual const val CH_MFCC_ENERGY = 6
        actual const val CH_TIME_FEATURE = 7
    }

    actual fun classifySleepStage(sensorData: List<List<FloatArray>>): Int = 0
    actual fun close() {}
    actual fun isReady(): Boolean = false
}
