package com.sleepytime.shared.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.sqrt

@Serializable
data class Stats(
    val avg: Float = 0f,
    val stddev: Float = 0f,
    val min: Float = 0f,
    val max: Float = 0f
) {
    companion object {
        fun from(values: List<Float>): Stats {
            if (values.isEmpty()) {
                // 정의한 기본 생성자를 활용해 가독성 향상
                return Stats()
            }

            val n = values.size
            val avg = values.average().toFloat()

            val min = values.minOrNull() ?: 0f
            val max = values.maxOrNull() ?: 0f

            val variance = if (n > 1) {
                values.sumOf { (it - avg).toDouble().pow(2.0) } / (n - 1)
            } else {
                0.0
            }
            val stddev = sqrt(variance)

            return Stats(avg, stddev.toFloat(), min, max)
        }
    }
}