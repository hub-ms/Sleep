package com.sleepytime.shared.data.remote.dto.response

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MonthlySleepStatsResponse(
    @SerialName("yearMonth") val yearMonth: String,
    @Serializable val summary: PeriodSummaryResponse,
    @SerialName("sessions") val sessions: List<SleepSessionResponse>
)
