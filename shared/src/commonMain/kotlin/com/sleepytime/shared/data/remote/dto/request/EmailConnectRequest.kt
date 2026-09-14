package com.sleepytime.shared.data.remote.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmailConnectRequest(
    @SerialName("emailToken") val emailToken: String
)
