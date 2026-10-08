package com.somniotune.app.data.local.mapper

import com.somniotune.app.data.local.UserEntity
import com.somniotune.app.domain.model.User


fun UserEntity.toUserDomain() = User(
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
fun User.toUserEntity() = UserEntity(
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


