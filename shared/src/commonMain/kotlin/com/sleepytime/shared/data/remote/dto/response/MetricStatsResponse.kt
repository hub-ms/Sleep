package com.sleepytime.shared.data.remote.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MetricStatsResponse(
    @SerialName("average") val average: Float,
    @SerialName("max") val max: Float,
    @SerialName("min") val min: Float
)
