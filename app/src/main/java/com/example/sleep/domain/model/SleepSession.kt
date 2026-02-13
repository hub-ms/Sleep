package com.example.sleep.domain.model

import java.time.Instant

// 수면 세션
data class SleepSession(
    val id: Long, // 수면 세션 고유 ID
    val startTime: Instant, // 수면 시작 시각
    val endTime: Instant?, // 수면 종료 시각
    val totalDuration: Long?, // 전체 수면 시간
    val createdAt: Instant // 수면 세션 기록 시간
)
