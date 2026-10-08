package com.somniotune.app.data.remote.mapper

import com.somniotune.app.data.remote.dto.response.SessionResponse
import com.somniotune.app.domain.model.Session

fun SessionResponse.toDomain() = Session(
    id = id,
    userId = userId,
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresAt = expiresAt,
    isActive = isActive
)