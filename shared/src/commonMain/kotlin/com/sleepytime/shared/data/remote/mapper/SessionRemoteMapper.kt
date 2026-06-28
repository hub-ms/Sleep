package com.sleepytime.shared.data.remote.mapper

import com.sleepytime.shared.data.remote.dto.response.SessionResponse
import com.sleepytime.shared.domain.model.Session

fun SessionResponse.toDomain() = Session(
    id = id,
    userId = userId,
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresAt = expiresAt,
    isActive = isActive
)