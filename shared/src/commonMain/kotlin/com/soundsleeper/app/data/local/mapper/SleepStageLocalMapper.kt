package com.soundsleeper.app.data.local.mapper

import com.soundsleeper.app.data.local.SleepStageEntity
import com.soundsleeper.app.domain.model.SleepStage
import com.soundsleeper.app.enum_.SleepStageType

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
