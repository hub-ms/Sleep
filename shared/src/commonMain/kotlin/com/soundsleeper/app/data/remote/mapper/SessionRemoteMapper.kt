package com.soundsleeper.app.data.remote.mapper

import com.soundsleeper.app.data.remote.dto.response.SessionResponse
import com.soundsleeper.app.domain.model.Session

fun SessionResponse.toDomain() = Session(
    id = id,
    userId = userId,
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresAt = expiresAt,
    isActive = isActive
)