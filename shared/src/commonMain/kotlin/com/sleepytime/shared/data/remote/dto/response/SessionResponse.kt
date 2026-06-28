package com.sleepytime.shared.data.remote.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionResponse(
    @SerialName("id") val id: Long,
    @SerialName("userId") val userId: Long,
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String?,
    @SerialName("expiresAt") val expiresAt: Long,
    @SerialName("isActive") val isActive: Boolean,
    @SerialName("createdAt") val createdAt: Long
)