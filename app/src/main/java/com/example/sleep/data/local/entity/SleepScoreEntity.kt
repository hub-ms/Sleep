package com.example.sleep.data.local.entity

import androidx.room.*

// 수면 점수 엔티티
@Entity(
    tableName = "sleep_score",
    indices = [Index("sessionId")]
)
data class SleepScoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L, // 점수 ID
    val sessionId: Long, // 수면 세션 ID
    val score: Int, // 계산된 수면 점수(0~100)
    val calculatedAt: Long // 점수 계산 시각
)