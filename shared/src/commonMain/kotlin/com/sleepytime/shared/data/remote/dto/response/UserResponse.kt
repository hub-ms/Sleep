package com.sleepytime.shared.data.remote.dto.response

import com.sleepytime.shared.domain.model.User.AuthInfo
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    @SerialName("userId") val userId: Long = 0L,
    @SerialName("email") val email: String? = null,
    @SerialName("nickname") val nickname: String = "",
    @SerialName("profileImageUrl") val profileImageUrl: String? = null,
    @SerialName("isPremium") val isPremium: Boolean = false,
    @SerialName("isActive") val isActive: Boolean = true,
    @SerialName("lastLoginAt") val lastLoginAt: LocalDateTime? = null,
    @SerialName("createdAt") val createdAt: LocalDateTime = LocalDateTime(2023, 1, 1, 0, 0),
    @SerialName("updatedAt") val updatedAt: LocalDateTime? = null
)
