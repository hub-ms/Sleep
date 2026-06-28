package com.sleepytime.shared.domain.model

import com.sleepytime.shared.enum_.MetricType
import kotlinx.serialization.Serializable

@Serializable
data class SleepMetrics(
    val wakeCountScore: Double = 0.0,
    val continuityScore: Double = 0.0,
    val deepScore: Double = 0.0,
    val remScore: Double = 0.0,
    val latencyScore: Double = 0.0,

    val awakeMinutes: Double = 0.0,
    val lightMinutes: Double = 0.0,
    val deepMinutes: Double = 0.0,
    val remMinutes: Double = 0.0,
    val sleepLatencyMinutes: Double = 0.0,
    val wakeCount: Int = 0
) {
    fun scoreList(): List<Pair<MetricType, Double>> = listOf(
        MetricType.WAKE_COUNT to wakeCountScore,
        MetricType.CONTINUITY to continuityScore,
        MetricType.DEEP_SLEEP to deepScore,
        MetricType.REM_SLEEP  to remScore,
        MetricType.LATENCY    to latencyScore,
    )

    val totalSleepMinutes: Double
        get() = lightMinutes + deepMinutes + remMinutes
}