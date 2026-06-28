package com.sleepytime.shared.data.remote.dto.response

import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.enum_.AuthProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthInfoResponse(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String,
    @SerialName("user") val user: UserResponse,
    @SerialName("authId") val authId: String,
    @SerialName("provider") val provider: AuthProvider
)
