package com.example.sleep.data.mapper

import com.example.sleep.data.local.entity.SleepInterruptionEntity
import com.example.sleep.domain.model.SleepInterruption

object SleepInterruptionMapper {
    fun toDomain(entity: SleepInterruptionEntity): SleepInterruption = SleepInterruption(
        id = entity.id,
        sessionId = entity.sessionId,
        durationMillis = entity.durationMillis,
        reason = entity.reason
    )

    fun fromDomain(domain: SleepInterruption): SleepInterruptionEntity = SleepInterruptionEntity(
        id = domain.id,
        sessionId = domain.sessionId,
        durationMillis = domain.durationMillis,
        reason = domain.reason
    )
}
