package com.soundsleeper.app.data.local.mapper

import com.soundsleeper.app.data.local.UserEntity
import com.soundsleeper.app.domain.model.User


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


