package com.sleepytime.app.entity_new

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "sleep_sessions",
    indexes = [
        Index(name = "idx_sleep_session_user_start", columnList = "user_id, start_at"),
        Index(name = "idx_sleep_session_user_end", columnList = "user_id, end_at")
    ]
)
class SleepSessionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "start_at", nullable = false)
    val startAt: Instant,
    @Column(name = "end_at")
    val endAt: Instant,
    @Column(name = "duration_sec", nullable = false)
    val durationSec: Int,
    @Column(name = "sleep_score")
    val sleepScore: Int? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    fun updateScore(score: Int) {
        this.updatedAt = Instant.now()
    }
}

