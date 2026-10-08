package com.soundsleeper.app.data.local.mapper

import com.soundsleeper.app.data.local.AuthInfoEntity
import com.soundsleeper.app.domain.model.User


fun AuthInfoEntity.toDomain(email: String) =
    User.AuthInfo.Member(
        memberEmail = email,
        authId = authId,
        provider = provider
    )
fun User.AuthInfo.toEntity(): AuthInfoEntity? =
    when (this) {
        is User.AuthInfo.Member -> AuthInfoEntity(
            authId = authId,
            provider = provider
        )
        is User.AuthInfo.Guest -> null
    }