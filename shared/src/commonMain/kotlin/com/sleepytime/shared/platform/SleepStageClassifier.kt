package com.sleepytime.shared.platform

expect class SleepStageClassifier {
    companion object {
        val CHANNEL_MEAN: FloatArray
        val CHANNEL_STD: FloatArray

        val CH_ACCEL_X: Int
        val CH_ACCEL_Y: Int
        val CH_ACCEL_Z: Int
        val CH_TILT_ANGLE: Int
        val CH_HEART_RATE: Int
        val CH_MIC: Int
        val CH_MFCC_ENERGY: Int
        val CH_TIME_FEATURE: Int
    }
    
    fun classifySleepStage(sensorData: List<List<FloatArray>>): Int
    fun close()
    fun isReady(): Boolean
}
