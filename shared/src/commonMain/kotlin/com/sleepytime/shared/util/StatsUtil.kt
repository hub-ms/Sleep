package com.sleepytime.shared.util

import com.sleepytime.shared.domain.model.EnvironmentFeature
import kotlin.math.pow
import kotlin.math.sqrt

object StatsUtil {
    fun computeStats(values: List<Float>): RollingStats {
        if (values.isEmpty()) return RollingStats()
        val count= values.size
        val last = values.last()
        val avg = values.average().toFloat()
        val variance = values.map {
            (it-avg).pow(2)
        }.average().toFloat()
        val std = sqrt(variance)
        val max = values.maxOrNull() ?: last
        val min = values.minOrNull() ?: last
        return RollingStats(avg, std, max, min, last, count)
    }
    fun calcEnvironmentStats(history: List<EnvironmentFeature.Snapshot>): EnvironmentStats {
        if (history.isEmpty()) return EnvironmentStats()

        val hrs = history.map { it.heartRate }
        val noises = history.map { it.noise }
        val temps = history.map { it.temperature }
        val hums = history.map { it.humidity }

        return EnvironmentStats(
            avgHeartRate = computeStats(hrs).std,
            avgNoise = computeStats(noises).std,
            avgTemp = computeStats(temps).std,
            avgHumidity = computeStats(hums).std,

            stddevHeartRate = computeStats(hrs).std,
            stddevNoise = computeStats(noises).std,
            stddevTemp = computeStats(temps).std,
            stddevHumidity = computeStats(hums).std,

            maxHeartRate = computeStats(hrs).max,
            maxNoise = computeStats(noises).max,
            maxTemp = computeStats(temps).max,
            maxHumidity = computeStats(hums).max,

            minHeartRate = computeStats(hrs).min,
            minNoise = computeStats(noises).min,
            minTemp = computeStats(temps).min,
            minHumidity = computeStats(hums).min,
        )
    }
    data class EnvironmentStats(
        val avgTemp: Float = 0f,
        val avgHumidity: Float = 0f,
        val avgHeartRate: Float = 0f,
        val avgNoise: Float = 0f,
        val stddevTemp: Float = 0f,
        val stddevHumidity: Float = 0f,
        val stddevHeartRate: Float = 0f,
        val stddevNoise: Float = 0f,
        val maxTemp: Float = 0f,
        val maxHumidity: Float = 0f,
        val maxHeartRate: Float = 0f,
        val maxNoise: Float = 0f,
        val minTemp: Float = 0f,
        val minHumidity: Float = 0f,
        val minHeartRate: Float = 0f,
        val minNoise: Float = 0f
    )
    data class RollingStats(
        val avg: Float = 0f,
        val std: Float = 0f,
        val max: Float = 0f,
        val min: Float = 0f,
        val last: Float = 0f,
        val count: Int = 0
    )
}