package com.sleepytime.app.repository_new

import com.sleepytime.app.entity_new.RefreshTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenEntity, Long> {
    fun deleteByUserId(userId: Long)
    fun deleteByExpiresAtBefore(dateTime: LocalDateTime): Int
}