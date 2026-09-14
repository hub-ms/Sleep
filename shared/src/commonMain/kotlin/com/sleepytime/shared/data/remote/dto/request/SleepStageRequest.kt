package com.sleepytime.shared.data.remote.dto.request

import com.sleepytime.shared.enum_.SleepStageType
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class SleepStageRequest(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("type") val type: SleepStageType,
    @SerialName("startTime") val startTime: LocalDateTime,
    @SerialName("endTime") val endTime: LocalDateTime,
    @SerialName("duration") val duration: Duration
)