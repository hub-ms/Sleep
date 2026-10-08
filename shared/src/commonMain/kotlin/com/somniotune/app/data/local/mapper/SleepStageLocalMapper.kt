package com.somniotune.app.data.local.mapper

import com.somniotune.app.data.local.SleepStageEntity
import com.somniotune.app.domain.model.SleepStage
import com.somniotune.app.enum_.SleepStageType

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
