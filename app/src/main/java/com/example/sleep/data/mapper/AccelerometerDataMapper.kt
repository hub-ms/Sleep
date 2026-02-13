package com.example.sleep.data.mapper

import com.example.sleep.data.local.entity.AccelerometerEntity
import com.example.sleep.domain.model.AccelerometerData

object AccelerometerDataMapper {
    fun toDomain(entity: AccelerometerEntity): AccelerometerData = AccelerometerData(
        id = entity.id,
        sessionId = entity.sessionId,
        x = entity.x,
        y = entity.y,
        z = entity.z,
        timestamp = entity.timestamp
    )

    fun fromDomain(domain: AccelerometerData): AccelerometerEntity = AccelerometerEntity(
        id = domain.id,
        sessionId = domain.sessionId,
        x = domain.x,
        y = domain.y,
        z = domain.z,
        timestamp = domain.timestamp
    )
}
