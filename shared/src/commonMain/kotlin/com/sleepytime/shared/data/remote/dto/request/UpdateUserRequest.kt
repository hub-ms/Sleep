package com.sleepytime.shared.data.remote.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateUserRequest(
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("profileImageUrl") val profileImageUrl: String? = null,
)
