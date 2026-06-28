package com.sleepytime.shared.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.Stats
import com.sleepytime.shared.domain.repository.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

data class SensorData(val values: FloatArray, val timestamp: Long)
data class HeartRateData(val bpm: Float, val timestamp: Long)
data class NoiseData(val db: Float, val timestamp: Long)
data class TemperatureData(val temperature: Float, val timestamp: Long)

@Singleton
class AndroidSleepMeasureManager @Inject constructor(
    private val context: Context,
    private val weatherRepository: WeatherRepository,
    private val heartRateMonitor: HeartRateMonitor? = null
) : SleepMeasureManager, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val sensorDataQueue = ConcurrentLinkedDeque<SensorData>()
    private val noiseQueue = ConcurrentLinkedDeque<NoiseData>()
    private val heartRateQueue = ConcurrentLinkedDeque<HeartRateData>()
    private val temperatureQueue = ConcurrentLinkedDeque<TemperatureData>()

    private val windowBuffer = mutableListOf<SensorData>()
    private val noiseWindowBuffer = mutableListOf<NoiseData>()
    private val heartRateWindowBuffer = mutableListOf<HeartRateData>()
    private val tempWindowBuffer = mutableListOf<TemperatureData>()

    private val capturedSensorData = CopyOnWriteArrayList<List<FloatArray>>()
    private val capturedEnvironmentFeatures = CopyOnWriteArrayList<EnvironmentFeature>()
    private val capturedTimestamps = CopyOnWriteArrayList<Long>()

    private var isMeasuring = false
    override var onWindowReady: ((List<FloatArray>) -> Unit)? = null
    override var onEnvironmentReady: ((EnvironmentFeature) -> Unit)? = null

    private val windowSize = 1500
    private val maxBufferSize = 3000
    private var currentTemp: Float = 22.5f
    private var lastTempUpdate: Long = 0L

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isMeasuring || event == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val data = SensorData(event.values.clone(), System.currentTimeMillis())
            sensorDataQueue.addLast(data)
            if (sensorDataQueue.size % 100 == 0) {
                Log.d("AndroidSleepMeasureManager", "가속도: ${sensorDataQueue.size}")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun updateHeartRateFromWatch(bpm: Float) {
        val clampedBpm = bpm.coerceIn(40f, 180f)
        heartRateQueue.addLast(HeartRateData(clampedBpm, System.currentTimeMillis()))
    }

    override fun start() {
        Log.d("AndroidSleepMeasureManager", "start()")
        if (isMeasuring) return
        isMeasuring = true
        clearAllBuffers()

        Log.d("AndroidSleepMeasureManager", "accelerometer=$accelerometer")

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        heartRateMonitor?.startMonitoring(CoroutineScope(Dispatchers.Default))
        scheduleWindowUpdate()
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        heartRateMonitor?.stopMonitoring()
        isMeasuring = false
    }

    private fun clearAllBuffers() {
        sensorDataQueue.clear()
        noiseQueue.clear()
        heartRateQueue.clear()
        temperatureQueue.clear()
        windowBuffer.clear()
        noiseWindowBuffer.clear()
        heartRateWindowBuffer.clear()
        tempWindowBuffer.clear()
        capturedSensorData.clear()
        capturedEnvironmentFeatures.clear()
        capturedTimestamps.clear()
    }

    override fun getCapturedSensorData(): List<List<FloatArray>> = capturedSensorData.toList()
    override fun getCapturedEnvironmentFeatures(): List<EnvironmentFeature> =
        capturedEnvironmentFeatures.toList()

    override fun getCapturedTimestamps(): List<Long> = capturedTimestamps.toList()

    private fun scheduleWindowUpdate() {
        CoroutineScope(Dispatchers.Default).launch {
            while (isMeasuring) {
                delay(100)
                windowBuffer.addAll(sensorDataQueue.pollAll())
                noiseWindowBuffer.addAll(noiseQueue.pollAll())
                heartRateWindowBuffer.addAll(heartRateQueue.pollAll())
                tempWindowBuffer.addAll(temperatureQueue.pollAll())

                val now = System.currentTimeMillis()
                val epochStart = now - 30_000L

                val accelEpoch = windowBuffer.filter { it.timestamp >= epochStart }
                if (accelEpoch.size >= 1500) {
                    val windowData = accelEpoch.takeLast(1500).map { it.values }
                    capturedSensorData.add(windowData)
                    capturedTimestamps.add(now)
                    onWindowReady?.invoke(windowData)
                }

                val envFeature = createEnvironmentFeature(epochStart, now)
                if (envFeature != null) {
                    capturedEnvironmentFeatures.add(envFeature)
                    onEnvironmentReady?.invoke(envFeature)
                }

                windowBuffer.removeAll { it.timestamp < now - 60_000 }
                noiseWindowBuffer.removeAll { it.timestamp < now - 60_000 }
            }
        }
    }

    private suspend fun createEnvironmentFeature(
        epochStart: Long,
        timestamp: Long
    ): EnvironmentFeature? {
        val hrData = heartRateWindowBuffer.filter { it.timestamp >= epochStart }
        val heartRate =
            if (hrData.isNotEmpty()) Stats.from(hrData.map { it.bpm }) else Stats(75f, 5f, 60f, 90f)

        val noiseData = noiseWindowBuffer.filter { it.timestamp >= epochStart }
        val noise = if (noiseData.isNotEmpty()) Stats.from(noiseData.map { it.db }) else Stats(
            35f,
            10f,
            25f,
            45f
        )

        val weatherTemp = weatherRepository.getCurrentTemperature()
        val weatherHumidity = weatherRepository.getCurrentHumidity()
        val temperature = Stats(weatherTemp, 1f, weatherTemp - 0.5f, weatherTemp + 0.5f)
        val humidity = Stats(weatherHumidity, 3f, 40f, 60f)

        return EnvironmentFeature(
            timestamp = timestamp,
            snapshot = EnvironmentFeature.Snapshot(
                heartRate = heartRate.avg,
                noise = noise.avg,
                temperature = temperature.avg,
                humidity = humidity.avg
            ),
            stats = EnvironmentFeature.Statistics(
                heartRate = heartRate,
                noise = noise,
                temperature = temperature,
                humidity = humidity
            ),
            flag = EnvironmentFeature.Flag(
                isHeartRateAnomaly = heartRate.stddev > 15f || heartRate.max > 110f,
                isNoiseDanger = noise.avg > 60f,
                isTempExtreme = temperature.avg !in 18f..28f,
                isHumidityExtreme = humidity.avg !in 40f..60f,
            )
        )
    }
    fun <T> ConcurrentLinkedDeque<T>.pollAll(): List<T> {
        val result = mutableListOf<T>()
        var item: T?
        do {
            item = poll()
            item?.let { result.add(it) }
        } while (item != null)
        return result
    }
}