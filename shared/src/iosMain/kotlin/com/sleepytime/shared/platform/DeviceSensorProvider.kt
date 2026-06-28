package com.sleepytime.shared.platform

actual class DeviceSensorProvider {
    actual fun startListening()  = Unit
    actual fun stopListening()   = Unit
    actual fun getLatestReadings() = SensorReadings.EMPTY
}