package com.sleepytime.shared.data.remote.mapper

import com.sleepytime.shared.data.remote.dto.response.UserResponse
import com.sleepytime.shared.domain.model.User

fun UserResponse.responseToUser() = User(
    userId = userId,
    email = email,
    nickname = nickname,
    profileImageUrl = profileImageUrl,
    isPremium = isPremium,
    isActive = isActive,
    lastLoginAt = lastLoginAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
