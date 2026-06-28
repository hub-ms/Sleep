package com.sleepytime.shared.data.local.mapper

import com.sleepytime.shared.data.local.AuthInfoEntity
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.enum_.AuthProvider


fun AuthInfoEntity.toDomain() = User.AuthInfo.Member(
    memberEmail = "test",
    id = "test",
    authProvider = AuthProvider.GOOGLE
)
fun User.AuthInfo.toEntity() = AuthInfoEntity(
    authId = authId,
    provider = provider
)