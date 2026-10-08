package com.somniotune.app.data.remote.mapper

import com.somniotune.app.data.remote.dto.response.UserResponse
import com.somniotune.app.domain.model.User

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
