package com.sleepytime.shared.data.local.mapper

import com.sleepytime.shared.data.local.SessionEntity
import com.sleepytime.shared.domain.model.Session


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
        createdAt = createdAt // 생성 시간은 매핑하는 시점 혹은 기존 값을 전달받아 바인딩
    )
}