package com.sleepytime.shared.data.remote.dto.request

import com.sleepytime.shared.enum_.AuthProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SocialConnectRequest(
    @SerialName("provider") val provider: AuthProvider,
    @SerialName("accessToken") val accessToken: String
)
