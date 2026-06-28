package com.sleepytime.shared.data.remote.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmailVerifyRequest(
    @SerialName("email") val email: String = "",
    @SerialName("code") val code: String = ""
)
