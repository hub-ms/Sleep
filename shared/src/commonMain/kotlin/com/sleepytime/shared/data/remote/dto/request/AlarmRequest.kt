package com.sleepytime.shared.data.remote.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlarmRequest(
    @SerialName("hour") val hour: Int,
    @SerialName("minute") val minute: Int,
    @SerialName("isEnabled") val isEnabled: Boolean,
)
