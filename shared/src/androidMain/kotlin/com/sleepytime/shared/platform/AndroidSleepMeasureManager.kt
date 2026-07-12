package com.sleepytime.shared.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepMinuteAggregate
import com.sleepytime.shared.domain.model.Stats
import com.sleepytime.shared.util.StatsUtil
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

    private val capturedSensorWindows = CopyOnWriteArrayList<List<FloatArray>>()
    private val capturedAggregates = CopyOnWriteArrayList<SleepMinuteAggregate>()
    private val capturedEnvironmentFeatures = CopyOnWriteArrayList<EnvironmentFeature>()

    private val bufferMutex = Mutex()

    private var isMeasuring = false
    private var measureScope: CoroutineScope? = null

    companion object {
        private const val MOVEMENT_THRESHOLD_MS2 = 12.0f
        private const val DEFAULT_HEART_RATE_BPM = 65f
        private const val DEFAULT_NOISE_DB = 30f
        private const val HEART_RATE_ANOMALY_LOW_THRESHOLD = 40f
        private const val HEART_RATE_ANOMALY_HIGH_THRESHOLD = 110f
        private const val NOISE_DANGER_THRESHOLD = 60f
        private const val WINDOW_BUCKET_MS = 30_000L
    }
    override var onWindowReady: ((List<FloatArray>) -> Unit)? = null
    override var onEnvironmentReady: ((EnvironmentFeature) -> Unit)? = null
    override var onMinuteAggregateReady: ((SleepMinuteAggregate) -> Unit)? = null

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
            lastBucketTimestamp = (System.currentTimeMillis() / WINDOW_BUCKET_MS) * WINDOW_BUCKET_MS
            registerAccelerometerListener()
            scheduleWindowUpdate()
        }
    }
    private suspend fun registerAccelerometerListener() {
        withContext(Dispatchers.Main) {
            accelerometer?.let {
                sensorManager.registerListener(this@AndroidSleepMeasureManager, it, SensorManager.SENSOR_DELAY_GAME)
            }
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

            capturedSensorWindows.clear()
            capturedAggregates.clear()
            capturedEnvironmentFeatures.clear()
        }
    }
    private fun scheduleWindowUpdate() {
        measureScope?.launch {
            while (isMeasuring) {
                delay(500) // 배터리 절약을 위한 500ms 폴링 주기

                val now = System.currentTimeMillis()
                val currentBucket = (now / WINDOW_BUCKET_MS) * WINDOW_BUCKET_MS

                // 1. Thread-safe 큐에서 임시로 데이터를 안전하게 먼저 꺼냄 (락 범위 최소화)
                val newAccels = accelQueue.pollAll()
                val newNoises = noiseQueue.pollAll()
                val newHeartRates = heartRateQueue.pollAll()

                bufferMutex.withLock {
                    minuteAccel.addAll(newAccels)
                    minuteNoise.addAll(newNoises)
                    minuteHeartRate.addAll(newHeartRates)

                    if (currentBucket > lastBucketTimestamp) {
                        processMinuteAggregate(lastBucketTimestamp)
                        lastBucketTimestamp = currentBucket
                    }
                }
            }
        }
    }
    private fun processMinuteAggregate(bucketTimestamp: Long) {
        if (minuteAccel.isEmpty() && minuteHeartRate.isEmpty() && minuteNoise.isEmpty()) return

        val windowData = minuteAccel.map { it.values }
        if (windowData.isNotEmpty()) {
            capturedSensorWindows.add(windowData)
            onWindowReady?.invoke(windowData)
        }

        val hrStats = StatsUtil.computeStats(minuteHeartRate.map { it.bpm })
        val noiseStats = StatsUtil.computeStats(minuteNoise.map { it.db })
        val movementCount = countMovements(minuteAccel)

        val aggregate = SleepMinuteAggregate(
            timestampBucket = bucketTimestamp,
            avgHeartRate = hrStats.avg.ifEmpty(hrStats.count, DEFAULT_HEART_RATE_BPM),
            maxHeartRate = hrStats.max.ifEmpty(hrStats.count, DEFAULT_HEART_RATE_BPM),
            minHeartRate = hrStats.min.ifEmpty(hrStats.count, DEFAULT_HEART_RATE_BPM),
            avgNoiseDb = noiseStats.avg.ifEmpty(noiseStats.count, DEFAULT_NOISE_DB),
            maxNoiseDb = noiseStats.max.ifEmpty(noiseStats.count, DEFAULT_NOISE_DB),
            minNoiseDb = noiseStats.min.ifEmpty(noiseStats.count, DEFAULT_NOISE_DB),
            movementCount = movementCount,
        )

        capturedAggregates.add(aggregate)
        onMinuteAggregateReady?.invoke(aggregate)

        val envFeature = createEnvironmentFeature(bucketTimestamp, hrStats,noiseStats)
        capturedEnvironmentFeatures.add(envFeature)
        onEnvironmentReady?.invoke(envFeature)

        minuteAccel.clear()
        minuteHeartRate.clear()
        minuteNoise.clear()
    }
    private fun countMovements(accel: List<RawAccel>): Int = accel.count {
        val force = sqrt(it.values[0] * it.values[0] + it.values[1] * it.values[1] + it.values[2] * it.values[2])
        force > MOVEMENT_THRESHOLD_MS2
    }
    private fun createEnvironmentFeature(
        timestamp: Long,
        hrStats: StatsUtil.RollingStats,
        noiseStats: StatsUtil.RollingStats,
    ): EnvironmentFeature {
        return EnvironmentFeature(
            timestamp = timestamp,
            snapshot = EnvironmentFeature.Snapshot(
                heartRate = hrStats.last,
                noise = noiseStats.last,
            ),
            stats = EnvironmentFeature.Statistics(
                heartRate = Stats(hrStats.avg, hrStats.std, hrStats.min, hrStats.max),
                noise = Stats(noiseStats.avg, noiseStats.std, noiseStats.min, noiseStats.max),
            ),
            flag = EnvironmentFeature.Flag(
                isHeartRateAnomaly = hrStats.min < HEART_RATE_ANOMALY_LOW_THRESHOLD || hrStats.max > HEART_RATE_ANOMALY_HIGH_THRESHOLD,
                isNoiseDanger = noiseStats.avg > NOISE_DANGER_THRESHOLD,
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
    private fun Float.ifEmpty(count: Int, default: Float): Float = if (count == 0) default else this

    override fun getCapturedSensorData(): List<List<FloatArray>> = capturedSensorWindows.toList()
    override fun getCapturedEnvironmentFeatures(): List<EnvironmentFeature> = capturedEnvironmentFeatures.toList()
    override fun getCapturedTimestamps(): List<Long> = capturedAggregates.map { it.timestampBucket }
    override fun submitHeartRate(bpm: Float, timestamp: Long) {
        if (!isMeasuring) return
        heartRateQueue.addLast(RawHeartRate(bpm, timestamp))
    }

    override fun submitNoise(db: Float, timestamp: Long) {
        if (!isMeasuring) return
        noiseQueue.addLast(RawNoise(db, timestamp))
    }
}