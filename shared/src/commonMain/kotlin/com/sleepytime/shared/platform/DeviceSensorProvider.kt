package com.sleepytime.shared.platform

data class SensorReadings(
    val pressureHpa: Float?,
    val ambientTempC: Float?,
    val humidityPercent: Float?
) {
    companion object {
        val EMPTY = SensorReadings(null, null, null)
    }
}

expect class DeviceSensorProvider {
    fun startListening()
    fun stopListening()
    fun getLatestReadings(): SensorReadings
}