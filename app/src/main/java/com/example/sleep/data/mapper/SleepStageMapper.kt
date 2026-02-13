package com.example.sleep.data.mapper

import com.example.sleep.data.local.entity.SleepStageEntity
import com.example.sleep.domain.model.SleepStage

object SleepStageMapper {
    fun toDomain(entity: SleepStageEntity): SleepStage = entity.stage

    fun fromDomain(domain: SleepStage, sessionId: Long, startTime: Long, endTime: Long): SleepStageEntity = SleepStageEntity(
        sessionId = sessionId,
        stage = domain,
        startTimeMillis = startTime,
        endTimeMillis = endTime,
        durationMillis = endTime - startTime
    )
}
