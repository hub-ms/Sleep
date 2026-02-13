package com.example.sleep.data.local.entity

import androidx.room.*

// 가속도 센서 엔티티
@Entity(
    tableName = "accelerometer_data",
    indices = [Index("sessionId"), Index("timestamp")]
)
data class AccelerometerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L, // 센서 데이터 고유 ID
    val sessionId: Long, // 수면 세션 ID(FK)
    val x: Float, // x축 값
    val y: Float, // y축 값
    val z: Float, // z축 값
    val timestamp: Long // 측정 시각
)
