package com.soundsleeper.app.data.remote.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChannelTalkHashResponse(
    @SerialName("memberHash") val memberHash: String
)