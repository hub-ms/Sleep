package com.sleepytime.shared.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepMinuteAggregate
import com.sleepytime.shared.domain.model.Stats
import com.sleepytime.shared.domain.repository.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

data class RawAccel(val values: FloatArray, val timestamp: Long) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawAccel

        if (timestamp != other.timestamp) return false
        if (!values.contentEquals(other.values)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }
}

data class RawHeartRate(val bpm: Float, val timestamp: Long)
data class RawNoise(val db: Float, val timestamp: Long)


@Singleton
class AndroidSleepMeasureManager @Inject constructor(
    context: Context,
    private val weatherRepository: WeatherRepository,
    private val heartRateMonitor: HeartRateMonitor? = null
) : SleepMeasureManager, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val accelQueue = ConcurrentLinkedDeque<RawAccel>()
    private val heartRateQueue = ConcurrentLinkedDeque<RawHeartRate>()
    private val noiseQueue = ConcurrentLinkedDeque<RawNoise>()

    private val minuteAccel = mutableListOf<RawAccel>()
    private val minuteHeartRate = mutableListOf<RawHeartRate>()
    private val minuteNoise = mutableListOf<RawNoise>()
    private var lastBucketTimestamp: Long = 0

    private val capturedAggregates = CopyOnWriteArrayList<SleepMinuteAggregate>()
    private val capturedEnvironmentFeatures = CopyOnWriteArrayList<EnvironmentFeature>()

    private val bufferMutex = Mutex()

    private var isMeasuring = false
    override var onWindowReady: ((List<FloatArray>) -> Unit)? = null
    override var onEnvironmentReady: ((EnvironmentFeature) -> Unit)? = null
    var onMinuteAggregateReady: ((SleepMinuteAggregate) -> Unit)? = null
    private var measureScope: CoroutineScope? = null

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isMeasuring || event == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            accelQueue.addLast(RawAccel(event.values.clone(), System.currentTimeMillis()))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun start() {
        if (isMeasuring) return
        isMeasuring = true

        measureScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        measureScope?.launch {
            clearAllBuffers()
            lastBucketTimestamp = (System.currentTimeMillis() / 60000) * 60000

            withContext(Dispatchers.Main) {
                accelerometer?.let {
                    sensorManager.registerListener(this@AndroidSleepMeasureManager, it, SensorManager.SENSOR_DELAY_GAME)
                }
            }
            heartRateMonitor?.startMonitoring(CoroutineScope(Dispatchers.Default))
            scheduleWindowUpdate()
        }
    }

    override fun stop() {
        isMeasuring = false
        sensorManager.unregisterListener(this)
        measureScope?.cancel()  // ← 루프 코루틴 정상 취소
        measureScope = null
    }

    private suspend fun clearAllBuffers() {
        bufferMutex.withLock {
            accelQueue.clear()
            noiseQueue.clear()
            heartRateQueue.clear()

            minuteAccel.clear()
            minuteNoise.clear()
            minuteHeartRate.clear()

            capturedAggregates.clear()
            capturedEnvironmentFeatures.clear()
        }
    }
    private fun scheduleWindowUpdate() {
        measureScope?.launch {
            while (isMeasuring) {
                delay(500) // 배터리 절약을 위한 500ms 폴링 주기

                val now = System.currentTimeMillis()
                val currentBucket = (now / 60000) * 60000

                // 1. Thread-safe 큐에서 임시로 데이터를 안전하게 먼저 꺼냄 (락 범위 최소화)
                val newAccels = accelQueue.pollAll()
                val newNoises = noiseQueue.pollAll()
                val newHeartRates = heartRateQueue.pollAll()

                bufferMutex.withLock {
                    minuteAccel.addAll(newAccels)
                    minuteNoise.addAll(newNoises)
                    minuteHeartRate.addAll(newHeartRates)

                    // 1분이 지나 버킷 타임스탬프가 변경되었을 때 압축 수행
                    if (currentBucket > lastBucketTimestamp) {
                        // 내부에서 minute* 버퍼를 읽고 clear하므로 반드시 락 내부에서 실행되어야 함
                        processMinuteAggregate(lastBucketTimestamp)
                        lastBucketTimestamp = currentBucket
                    }
                }
            }
        }
    }
    private suspend fun processMinuteAggregate(bucketTimestamp: Long) {
        if (minuteAccel.isEmpty() && minuteHeartRate.isEmpty() && minuteNoise.isEmpty()) return

        val hrValues = minuteHeartRate.map { it.bpm }
        val avgHr = if (hrValues.isNotEmpty()) hrValues.average().toFloat() else 65f
        val maxHr = if (hrValues.isNotEmpty()) hrValues.maxOrNull() ?: 65f else 65f
        val minHr = if (hrValues.isNotEmpty()) hrValues.minOrNull() ?: 65f else 65f

        val noiseValues = minuteNoise.map { it.db }
        val avgNoise = if (noiseValues.isNotEmpty()) noiseValues.average().toFloat() else 30f
        val maxNoise = if (noiseValues.isNotEmpty()) noiseValues.maxOrNull() ?: 30f else 30f
        val minNoise = if (noiseValues.isNotEmpty()) noiseValues.minOrNull() ?: 30f else 30f

        val movementCount = minuteAccel.count {
            val totalForce = sqrt(it.values[0] * it.values[0] + it.values[1] * it.values[1] + it.values[2] * it.values[2])
            totalForce > 12.0f
        }

        val aggregate = SleepMinuteAggregate(
            timestampBucket = bucketTimestamp,
            avgHeartRate = avgHr,
            maxHeartRate = maxHr,
            minHeartRate = minHr,
            avgNoiseDb = avgNoise,
            maxNoiseDb = maxNoise,
            minNoiseDb = minNoise,
            movementCount = movementCount
        )

        capturedAggregates.add(aggregate)
        onMinuteAggregateReady?.invoke(aggregate)

        // 하위 호환 컴포넌트 기능 유지용 환경 피처 생성 호출
        val envFeature = createEnvironmentFeature(bucketTimestamp, aggregate)
        capturedEnvironmentFeatures.add(envFeature)
        onEnvironmentReady?.invoke(envFeature)

        minuteAccel.clear()
        minuteHeartRate.clear()
        minuteNoise.clear()
    }
    private suspend fun createEnvironmentFeature(timestamp: Long, aggregate: SleepMinuteAggregate): EnvironmentFeature {
        val weatherTemp = weatherRepository.getCurrentTemperature()
        val weatherHumidity = weatherRepository.getCurrentHumidity()

        return EnvironmentFeature(
            timestamp = timestamp,
            snapshot = EnvironmentFeature.Snapshot(
                heartRate = aggregate.avgHeartRate,
                noise = aggregate.avgNoiseDb,
                temperature = weatherTemp,
                humidity = weatherHumidity
            ),
            stats = EnvironmentFeature.Statistics(
                heartRate = Stats(aggregate.avgHeartRate, 5f, aggregate.minHeartRate, aggregate.maxHeartRate),
                noise = Stats(aggregate.avgNoiseDb, 5f, aggregate.minNoiseDb, aggregate.maxNoiseDb),
                temperature = Stats(weatherTemp, 0f, weatherTemp, weatherTemp),
                humidity = Stats(weatherHumidity, 0f, weatherHumidity, weatherHumidity)
            ),
            flag = EnvironmentFeature.Flag(
                isHeartRateAnomaly = aggregate.maxHeartRate > 110f,
                isNoiseDanger = aggregate.avgNoiseDb > 60f,
                isTempExtreme = weatherTemp !in 18f..28f,
                isHumidityExtreme = weatherHumidity !in 40f..60f
            )
        )
    }
    private fun <T> ConcurrentLinkedDeque<T & Any>.pollAll(): List<T> {
        val result = mutableListOf<T>()
        var item = this.poll()
        while (item != null) {
            result.add(item)
            item = this.poll()
        }
        return result
    }

    override fun getCapturedAggregates(): List<SleepMinuteAggregate> = capturedAggregates.toList()
    override fun getCapturedSensorData(): List<List<FloatArray>> = emptyList() // 메모리 절약을 위해 Raw 완전 제거
    override fun getCapturedEnvironmentFeatures(): List<EnvironmentFeature> = capturedEnvironmentFeatures.toList()
    override fun getCapturedTimestamps(): List<Long> = capturedAggregates.map { it.timestampBucket }
}