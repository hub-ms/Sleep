package com.example.sleep.data.mapper

import com.example.sleep.data.local.entity.SleepSessionEntity
import java.time.Instant

object SleepSessionMapper {
    fun toDomain(entity: SleepSessionEntity): SleepSessionEntity = SleepSessionEntity(
        id = entity.id,
        startTime = Instant.ofEpochMilli(entity.startTime),
        endTime = entity.endTime?.let { Instant.ofEpochMilli(it) },
        qualityScore = entity.qualityScore,
        totalDuration = entity.totalDuration
    )

    fun fromDomain(domain: SleepSessionEntity): SleepSessionEntity = SleepSessionEntity(
        id = domain.id,
        startTime = domain.startTime.toEpochMilli(),
        endTime = domain.endTime?.toEpochMilli(),
        qualityScore = domain.qualityScore,
        totalDuration = domain.totalDuration
    )
}