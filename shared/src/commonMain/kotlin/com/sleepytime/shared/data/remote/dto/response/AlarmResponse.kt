package com.sleepytime.shared.data.remote.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlarmResponse(
    @SerialName("alarmId") val alarmId: Long? = null,
    @SerialName("hour") val hour: Int = 7,
    @SerialName("minute") val minute: Int = 30,
    @SerialName("isEnabled") val isEnabled: Boolean = false
)
