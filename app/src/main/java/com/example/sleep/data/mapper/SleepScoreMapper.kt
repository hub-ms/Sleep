package com.example.sleep.data.mapper

import com.example.sleep.data.local.entity.SleepScoreEntity
import com.example.sleep.domain.model.SleepScore

object SleepScoreMapper {
    fun toDomain(entity: SleepScoreEntity): SleepScore = SleepScore(
        id = entity.id,
        sessionId = entity.sessionId,
        score = entity.score,
        calculatedAt = entity.calculatedAt
    )

    fun fromDomain(domain: SleepScore): SleepScoreEntity = SleepScoreEntity(
        id = domain.id,
        sessionId = domain.sessionId,
        score = domain.score,
        calculatedAt = domain.calculatedAt
    )
}
