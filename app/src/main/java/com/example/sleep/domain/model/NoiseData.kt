package com.example.sleep.domain.model

data class NoiseData(
    val id: Long,
    val sessionId: Long,
    val decibel: Float,
    val timestamp: Long
)
