package com.sleepytime.shared.platform
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

actual class DeviceSensorProvider(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    @Volatile private var pressure: Float?    = null
    @Volatile private var ambientTemp: Float? = null
    @Volatile private var humidity: Float?    = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_PRESSURE           -> pressure    = e.values[0]
                Sensor.TYPE_AMBIENT_TEMPERATURE -> ambientTemp = e.values[0]
                Sensor.TYPE_RELATIVE_HUMIDITY  -> humidity    = e.values[0]
            }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
    }

    actual fun startListening() {
        listOf(
            Sensor.TYPE_PRESSURE,
            Sensor.TYPE_AMBIENT_TEMPERATURE,
            Sensor.TYPE_RELATIVE_HUMIDITY
        ).forEach { type ->
            sensorManager.getDefaultSensor(type)?.let {
                sensorManager.registerListener(
                    listener, it, SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        }
    }

    actual fun stopListening() = sensorManager.unregisterListener(listener)

    actual fun getLatestReadings() =
        SensorReadings(pressure, ambientTemp, humidity)
}