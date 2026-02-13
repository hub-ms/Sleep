package com.example.sleep.domain.model

data class AccelerometerData(
    val id: Long,
    val sessionId: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Long
)
