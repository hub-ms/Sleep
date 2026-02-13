package com.example.sleep.domain.model

import com.example.sleep.domain.InterruptionReason

// 수면 중단
data class SleepInterruption(
    val id: Long,
    val sessionId: Long,
    val durationMillis: Long,
    val reason: InterruptionReason
)
