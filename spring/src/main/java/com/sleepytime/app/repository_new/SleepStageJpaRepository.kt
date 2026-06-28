package com.sleepytime.app.repository_new

import com.sleepytime.app.entity_new.SleepStageEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface SleepStageJpaRepository : JpaRepository<SleepStageEntity, Long> {

    fun findBySleepSessionIdIn(sessionIds: List<Long>): List<SleepStageEntity>

    @Modifying(clearAutomatically = true)
    @Query("""
        DELETE FROM SleepStageEntity s
        WHERE s.sleepSessionId IN (
            SELECT ss.id FROM SleepSessionEntity ss WHERE ss.userId = :userId
        )
    """)
    fun deleteByUserId(userId: Long): Int
}