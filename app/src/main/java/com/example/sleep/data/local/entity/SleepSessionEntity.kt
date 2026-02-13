package com.example.sleep.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// 수면 기록 엔티티
@Entity(tableName = "sleep_session")
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0L, // 수면 세션 고유 ID(PK)
    val startTimeMillis: Long, // 수면 시작 시각
    val endTimeMillis: Long?, // 수면 종료 시각(수면 중이면 null)
    val totalDurationMillis: Long?, // 전체 수면 시간(계산 후 저장)
    val createdAt: Long = System.currentTimeMillis() // 수면 세션 기록 시간
)