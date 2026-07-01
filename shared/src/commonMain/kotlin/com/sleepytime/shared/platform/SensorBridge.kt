package com.sleepytime.shared.platform

import kotlinx.coroutines.CoroutineScope

/**
 * Interface to bridge sensor data collection with tracking services.
 */
expect class SensorBridge {
    fun startHeartRateSensor(scope: CoroutineScope)
    fun stopHeartRateSensor()
    fun startNoiseSensor(scope: CoroutineScope)
    fun stopNoiseSensor()
    fun getHeartRate(): Float
    fun getNoiseLevel(): Float
}
