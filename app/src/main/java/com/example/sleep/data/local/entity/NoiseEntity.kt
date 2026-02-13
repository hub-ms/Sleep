package com.example.sleep.data.local.entity

import androidx.room.*

// 소음 센서 엔티티
@Entity(
    tableName = "noise_data",
    indices = [Index("sessionId"), Index("timestamp")]
)
data class NoiseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L, // 센서 데이터 고유 ID
    val sessionId: Long, // 수면 세션 ID(FK)
    val decibel: Float, // 측정된 소음 값
    val timestamp: Long // 측정 시각
)
