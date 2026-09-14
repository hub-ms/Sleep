package com.sleepytime.shared.util

import kotlin.math.pow
import kotlin.math.sqrt

object StatsUtil {
    fun computeStats(values: List<Float>): RollingStats {
        if (values.isEmpty()) return RollingStats()
        val count= values.size
        val avg = values.average().toFloat()
        val variance = if (count > 1) {
            values.sumOf { ((it - avg).pow(2)).toDouble() }.toFloat() / (count - 1)
        } else 0f
        val std = sqrt(variance)
        return RollingStats(
            avg,
            std,
            values.max(),
            values.min(),
            values.last(),
            count
        )
    }
    data class RollingStats(
        val avg: Float = 0f,
        val std: Float = 0f,
        val max: Float = 0f,
        val min: Float = 0f,
        val last: Float = 0f,
        val count: Int = 0
    )
}