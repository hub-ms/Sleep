package com.example.sleep.data.local.entity

import androidx.room.*
import com.example.sleep.domain.SleepStage

// 수면 단계 엔티티
@Entity(
    tableName = "sleep_stage",
    indices = [Index("sessionId")]
)
data class SleepStageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L, // 단계 ID
    val sessionId: Long, // 수면 세션 ID
    val stage: SleepStage, // 수면 단계(AWAKE, LIGHT, DEEP, REM)
    val startTimeMillis: Long, // 단계 시작 시각
    val endTimeMillis: Long, // 단계 종료 시각
    val durationMillis: Long // 단계 지속 시간
)
