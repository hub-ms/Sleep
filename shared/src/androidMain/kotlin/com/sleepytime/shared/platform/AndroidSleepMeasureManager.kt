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
import kotlin.math.abs
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

    // 💡 소음측정 정확도 개선: 세션마다 침실의 배경소음 수준이 다르므로, 고정 임계값 하나로
    // 모든 사용자를 판단하지 않고 세션 초반 몇 분을 "이 밤의 개인 배경소음 기준선"으로 삼아
    // 위험 판정 임계값을 개인화합니다. 기준선이 원래 임계값보다 낮을 때는 절대 임계값보다
    // 민감해지지 않도록 max()로 하한을 둡니다(회귀 방지).
    private var sessionAmbientBaselineDb: Float? = null
    private val calibrationBucketNoiseAvgs = mutableListOf<Float>()

    // 💡 소음측정 정확도 개선: 단발성 스파이크(알림음, 한 번의 기침 등)에 과민 반응하지 않도록
    // 연속된 버킷에서 임계값을 넘을 때만 "소음 위험"으로 판정합니다.
    private var consecutiveDangerBuckets = 0

    companion object {
        private const val MOVEMENT_THRESHOLD_MS2 = 12.0f
        private const val DEFAULT_NOISE_DB = 30f
        private const val NOISE_DANGER_THRESHOLD = 60f
        private const val WINDOW_BUCKET_MS = 30_000L
        private const val CALIBRATION_BUCKET_COUNT = 4 // 첫 2분(4×30초)을 세션 배경소음 기준선으로 사용
        private const val DANGER_BASELINE_DELTA_DB = 30f // 개인 기준선 대비 이만큼 이상 올라야 위험으로 판단
        private const val MIN_CONSECUTIVE_DANGER_BUCKETS = 2 // 연속 2버킷(60초) 이상 지속되어야 위험으로 판단
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

            sessionAmbientBaselineDb = null
            calibrationBucketNoiseAvgs.clear()
            consecutiveDangerBuckets = 0
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
        // 버킷의 마지막 소음값을 일괄 태깅하지 않고, 각 accel 샘플과 시간상 가장 가까운
        // 소음 샘플을 매칭합니다(accel/noise 모두 타임스탬프 오름차순이므로 two-pointer로 O(n+m)).
        var noiseIdx = 0
        val windowData = minuteAccel.mapIndexed { index, accel ->
            val gyro = minuteGyro.getOrNull(index) ?: RawGyro(floatArrayOf(0f, 0f, 0f), accel.timestamp)

            while (noiseIdx < minuteNoise.size - 1 &&
                abs(minuteNoise[noiseIdx + 1].timestamp - accel.timestamp) <=
                abs(minuteNoise[noiseIdx].timestamp - accel.timestamp)
            ) {
                noiseIdx++
            }
            val noise = minuteNoise.getOrNull(noiseIdx)?.db ?: DEFAULT_NOISE_DB

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

    /**
     * 이번 버킷이 "소음 위험"인지 판정합니다.
     * 1) 세션 초반 [CALIBRATION_BUCKET_COUNT]개 버킷 평균으로 이 밤의 개인 배경소음 기준선을
     *    추정하고, 절대 임계값([NOISE_DANGER_THRESHOLD])보다 낮아지지는 않게 하한을 둔 채
     *    기준선 대비 상대적으로 판단합니다.
     * 2) 연속 [MIN_CONSECUTIVE_DANGER_BUCKETS]개 버킷 이상 임계값을 넘어야 위험으로 확정해
     *    단발성 스파이크에 의한 오탐을 줄입니다.
     */
    private fun resolveNoiseDanger(noiseStats: StatsUtil.RollingStats): Boolean {
        if (noiseStats.count == 0) {
            consecutiveDangerBuckets = 0
            return false
        }

        if (sessionAmbientBaselineDb == null) {
            calibrationBucketNoiseAvgs.add(noiseStats.avg)
            if (calibrationBucketNoiseAvgs.size >= CALIBRATION_BUCKET_COUNT) {
                sessionAmbientBaselineDb = calibrationBucketNoiseAvgs.average().toFloat()
            }
        }

        val effectiveThreshold = sessionAmbientBaselineDb?.let { baseline ->
            maxOf(NOISE_DANGER_THRESHOLD, baseline + DANGER_BASELINE_DELTA_DB)
        } ?: NOISE_DANGER_THRESHOLD

        consecutiveDangerBuckets = if (noiseStats.avg > effectiveThreshold) {
            consecutiveDangerBuckets + 1
        } else {
            0
        }
        return consecutiveDangerBuckets >= MIN_CONSECUTIVE_DANGER_BUCKETS
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