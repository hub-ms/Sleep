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

data class RawNoise(val db: Float, val timestamp: Long)
data class RawGyro(val values: FloatArray, val timestamp: Long)


@Singleton
class AndroidSleepMeasureManager @Inject constructor(
    context: Context,
) : SleepMeasureManager, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val accelQueue = ConcurrentLinkedDeque<RawAccel>()
    private val gyroQueue = ConcurrentLinkedDeque<RawGyro>()
    private val noiseQueue = ConcurrentLinkedDeque<RawNoise>()

    private val minuteAccel = mutableListOf<RawAccel>()
    private val minuteGyro = mutableListOf<RawGyro>()
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
        private const val DEFAULT_NOISE_DB = 30f
        private const val NOISE_DANGER_THRESHOLD = 60f
        private const val WINDOW_BUCKET_MS = 30_000L
    }
    override var onWindowReady: ((List<FloatArray>) -> Unit)? = null
    override var onEnvironmentReady: ((EnvironmentFeature) -> Unit)? = null
    override var onMinuteAggregateReady: ((SleepMinuteAggregate) -> Unit)? = null

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isMeasuring || event == null) return
        val now = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelQueue.addLast(RawAccel(event.values.clone(), now))
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroQueue.addLast(RawGyro(event.values.clone(), now))
            }
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
            gyroscope?.let {
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
            gyroQueue.clear()
            noiseQueue.clear()

            minuteAccel.clear()
            minuteGyro.clear()
            minuteNoise.clear()

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
                val newGyros = gyroQueue.pollAll()
                val newNoises = noiseQueue.pollAll()

                bufferMutex.withLock {
                    minuteAccel.addAll(newAccels)
                    minuteGyro.addAll(newGyros)
                    minuteNoise.addAll(newNoises)

                    if (currentBucket > lastBucketTimestamp) {
                        processMinuteAggregate(lastBucketTimestamp)
                        lastBucketTimestamp = currentBucket
                    }
                }
            }
        }
    }
    private fun processMinuteAggregate(bucketTimestamp: Long) {
        if (minuteAccel.isEmpty() && minuteNoise.isEmpty()) return

        // 💡 1주차 로드맵: 가속도계 + 자이로스코프 RAW 데이터 로깅 (7채널, 심박수 제거)
        val windowData = minuteAccel.mapIndexed { index, accel ->
            val gyro = minuteGyro.getOrNull(index) ?: RawGyro(floatArrayOf(0f, 0f, 0f), accel.timestamp)
            val noise = minuteNoise.lastOrNull()?.db ?: DEFAULT_NOISE_DB

            floatArrayOf(
                accel.values[0], accel.values[1], accel.values[2], // 0, 1, 2: Accel XYZ
                gyro.values[0], gyro.values[1], gyro.values[2],    // 3, 4, 5: Gyro XYZ
                noise                                              // 6: Noise
            )
        }

        if (windowData.isNotEmpty()) {
            capturedSensorWindows.add(windowData)
            onWindowReady?.invoke(windowData)
        }

        val noiseStats = StatsUtil.computeStats(minuteNoise.map { it.db })
        val movementCount = countMovements(minuteAccel)

        val aggregate = SleepMinuteAggregate(
            timestampBucket = bucketTimestamp,
            avgNoiseDb = noiseStats.avg.ifEmpty(noiseStats.count, DEFAULT_NOISE_DB),
            maxNoiseDb = noiseStats.max.ifEmpty(noiseStats.count, DEFAULT_NOISE_DB),
            minNoiseDb = noiseStats.min.ifEmpty(noiseStats.count, DEFAULT_NOISE_DB),
            movementCount = movementCount,
        )

        capturedAggregates.add(aggregate)
        onMinuteAggregateReady?.invoke(aggregate)

        val envFeature = createEnvironmentFeature(bucketTimestamp, noiseStats)
        capturedEnvironmentFeatures.add(envFeature)
        onEnvironmentReady?.invoke(envFeature)

        minuteAccel.clear()
        minuteGyro.clear()
        minuteNoise.clear()
    }
    private fun countMovements(accel: List<RawAccel>): Int = accel.count {
        val force = sqrt(it.values[0] * it.values[0] + it.values[1] * it.values[1] + it.values[2] * it.values[2])
        force > MOVEMENT_THRESHOLD_MS2
    }
    private fun createEnvironmentFeature(
        timestamp: Long,
        noiseStats: StatsUtil.RollingStats,
    ): EnvironmentFeature {
        return EnvironmentFeature(
            timestamp = timestamp,
            snapshot = EnvironmentFeature.Snapshot(
                noise = noiseStats.last,
            ),
            stats = EnvironmentFeature.Statistics(
                noise = Stats(noiseStats.avg, noiseStats.std, noiseStats.min, noiseStats.max),
            ),
            flag = EnvironmentFeature.Flag(
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

    override fun submitNoise(db: Float, timestamp: Long) {
        if (!isMeasuring) return
        noiseQueue.addLast(RawNoise(db, timestamp))
    }

    override fun submitGyroscope(values: FloatArray, timestamp: Long) {
        if (!isMeasuring) return
        gyroQueue.addLast(RawGyro(values.clone(), timestamp))
    }
}