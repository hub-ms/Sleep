package com.example.sleep.data.local.entity

import androidx.room.*;
import com.example.sleep.domain.InterruptionReason

// 수면 중단 엔티티
@Entity(
    tableName = "sleep_interruption",
    indices = [Index("sessionId")]
)
data class SleepInterruptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L, // 중단 ID
    val sessionId: Long, // 수면 세션 ID
    val durationMillis: Long, // 중단 지속 시간
    val reason: InterruptionReason // 중단 유형
)
