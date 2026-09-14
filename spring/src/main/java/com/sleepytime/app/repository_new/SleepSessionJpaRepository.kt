package com.sleepytime.app.repository_new

import com.sleepytime.app.entity_new.SleepSessionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface SleepSessionJpaRepository : JpaRepository<SleepSessionEntity, Long> {

    fun findByUserIdOrderByStartAtDesc(userId: Long): List<SleepSessionEntity>

    @Query("""
        SELECT s FROM SleepSessionEntity s
        WHERE s.userId = :userId
        AND s.startAt BETWEEN :from AND :to
        ORDER BY s.startAt DESC
    """)
    fun findByUserIdAndRange(
        userId: Long,
        from: Instant,
        to: Instant
    ): List<SleepSessionEntity>

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM SleepSessionEntity s WHERE s.userId = :userId")
    fun deleteByUserId(userId: Long): Int
}