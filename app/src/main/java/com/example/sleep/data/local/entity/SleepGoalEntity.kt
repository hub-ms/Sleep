package com.example.sleep.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// 사용자 수면 목표 설정 엔티티
@Entity(tableName = "sleep_goal")
data class SleepGoalEntity(
    @PrimaryKey(autoGenerate = true)
    val userId: Long, // 사용자 ID
    val targetDurationMillis: Long, // 목표 수면 시간
    val targetBedTime: Long, // 목표 취침 시각
    val targetWakeTime: Long // 목표 기상 시각
)