package com.sleepytime.shared.data.remote.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SleepMusicRequest(
    @SerialName("musicName") val musicName: String,
    @SerialName("isFavorite") val isFavorite: Boolean
)