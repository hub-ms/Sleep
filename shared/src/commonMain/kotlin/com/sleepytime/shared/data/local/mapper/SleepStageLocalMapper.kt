package com.sleepytime.shared.data.local.mapper

import com.sleepytime.shared.data.local.SleepStageEntity
import com.sleepytime.shared.domain.model.SleepStage
import com.sleepytime.shared.enum_.SleepStageType

fun SleepStageEntity.toDomain(): SleepStage = SleepStage(
    id = id,
    sessionId = sessionId,
    type = SleepStageType.valueOf(type),
    startTime = startTime,
    duration = duration
)

fun SleepStage.toEntity(): SleepStageEntity = SleepStageEntity(
    id = id,
    sessionId = sessionId,
    type = type.name,
    startTime = startTime,
    endTime = endTime,
    duration = duration
)
