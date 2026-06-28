package com.sleepytime.app.entity_new

import com.sleepytime.shared.enum_.SleepStageType
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "sleep_stages",
    indexes = [
        Index(name = "idx_stage_session", columnList = "sleep_session_id"),
        Index(name = "idx_stage_time", columnList = "start_at, end_at")
    ]
)
class SleepStageEntity(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "sleep_session_id", nullable = false)
    val sleepSessionId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    val stage: SleepStageType,

    @Column(name = "start_at", nullable = false)
    val startAt: Instant,

    @Column(name = "end_at", nullable = false)
    val endAt: Instant,

    // 초 단위 캐싱 (조회 최적화)
    @Column(name = "duration_sec", nullable = false)
    val durationSec: Int
)
