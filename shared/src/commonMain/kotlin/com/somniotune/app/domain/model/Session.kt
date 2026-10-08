package com.somniotune.app.domain.model

data class Session(
    val id: Long,
    val userId: Long,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
    val isActive: Boolean
)