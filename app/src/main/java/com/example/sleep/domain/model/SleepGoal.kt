package com.example.sleep.domain.model

data class SleepGoal(
    val userId: Long,
    val targetDurationMillis: Long,
    val targetBedTime: Long,
    val targetWakeTime: Long
)
