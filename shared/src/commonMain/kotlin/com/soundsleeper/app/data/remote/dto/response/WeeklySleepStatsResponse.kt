package com.soundsleeper.app.data.remote.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeeklySleepStatsResponse(
    @SerialName("weekStart") val weekStart: String,
    @SerialName("weekEnd") val weekEnd: String,
    @SerialName("summary") val summary: PeriodSummaryResponse,
    @SerialName("sessions") val sessions: List<SleepSessionResponse>
)
