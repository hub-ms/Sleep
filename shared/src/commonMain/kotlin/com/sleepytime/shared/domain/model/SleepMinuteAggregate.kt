package com.sleepytime.shared.domain.model

data class SleepMinuteAggregate(
    val timestampBucket: Long,
    val avgNoiseDb: Float,
    val maxNoiseDb: Float,
    val minNoiseDb: Float,
    val movementCount: Int
)
