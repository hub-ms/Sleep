package com.sleepytime.shared.data.remote.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SleepMusicResponse(
    @SerialName("musicName") val musicName: String,
    @SerialName("isFavorite") val isFavorite: Boolean
)
