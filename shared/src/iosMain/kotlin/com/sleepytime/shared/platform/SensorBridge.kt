package com.sleepytime.shared.platform

import kotlinx.coroutines.CoroutineScope

actual class SensorBridge(
    // iOS implementations would likely take dependency containers or simple initializers
) {
    actual fun startNoiseSensor(scope: CoroutineScope) {
        // iOS implementation
    }
    actual fun stopNoiseSensor() {
        // iOS implementation
    }
    actual fun getHeartRate(): Float = 0f
    actual fun getNoiseLevel(): Float = 0f
    actual fun isDataReady(): Boolean = false
}
