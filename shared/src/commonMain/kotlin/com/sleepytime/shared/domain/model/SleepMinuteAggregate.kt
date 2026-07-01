package com.sleepytime.shared.domain.model

data class SleepMinuteAggregate(
    val timestampBucket: Long,
    val avgHeartRate: Float,
    val maxHeartRate: Float,
    val minHeartRate: Float,
    val avgNoiseDb: Float,
    val maxNoiseDb: Float,
    val minNoiseDb: Float,
    val movementCount: Int
)
