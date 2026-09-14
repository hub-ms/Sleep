package com.sleepytime.app.dto_new

import com.sleepytime.app.entity_new.SleepSessionEntity
import com.sleepytime.app.entity_new.SleepStageEntity

data class SleepSessionDto(
    val session: SleepSessionEntity,
    val stages: List<SleepStageEntity>,
)
