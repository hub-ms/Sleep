package com.example.sleep.domain.model

data class SleepScore(
    val id: Long,
    val sessionId: Long,
    val score: Int,
    val calculatedAt: Long
)
