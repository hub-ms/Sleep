package com.soundsleeper.app.data.local.mapper

import com.soundsleeper.app.data.local.SessionEntity
import com.soundsleeper.app.domain.model.Session


fun SessionEntity.toDomain(): Session {
    return Session(
        id = this.id,
        userId = this.userId,
        accessToken = this.accessToken,
        refreshToken = this.refreshToken,
        expiresAt = this.expiresAt,
        isActive = this.isActive
    )
}
fun Session.toEntity(createdAt: Long): SessionEntity {
    return SessionEntity(
        id = this.id,
        userId = this.userId,
        accessToken = this.accessToken,
        refreshToken = this.refreshToken,
        expiresAt = this.expiresAt,
        isActive = this.isActive,
        createdAt = createdAt
    )
}