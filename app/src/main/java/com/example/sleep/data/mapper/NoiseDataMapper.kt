package com.example.sleep.data.mapper

import com.example.sleep.data.local.entity.NoiseEntity
import com.example.sleep.domain.model.NoiseData

object NoiseDataMapper {
    fun toDomain(entity: NoiseEntity): NoiseData = NoiseData(
        id = entity.id,
        sessionId = entity.sessionId,
        decibel = entity.decibel,
        timestamp = entity.timestamp
    )

    fun fromDomain(domain: NoiseData): NoiseEntity = NoiseEntity(
        id = domain.id,
        sessionId = domain.sessionId,
        decibel = domain.decibel,
        timestamp = domain.timestamp
    )
}
