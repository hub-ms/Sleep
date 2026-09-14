package com.sleepytime.shared.domain.model

import kotlinx.datetime.Clock

data class Session(
    val id: Long,
    val userId: Long,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
    val isActive: Boolean
) {
    val isExpired: Boolean
        get() = Clock.System.now().toEpochMilliseconds() >= expiresAt
    val isValid: Boolean
        get() = isActive && !isExpired
}