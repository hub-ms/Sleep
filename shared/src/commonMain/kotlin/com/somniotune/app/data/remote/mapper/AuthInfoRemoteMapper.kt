package com.somniotune.app.data.remote.mapper

import com.somniotune.app.data.remote.dto.response.AuthInfoResponse
import com.somniotune.app.domain.model.User

fun AuthInfoResponse.toDomain() = User.AuthInfo.Member(
    memberEmail = "",
    authId = authId,
    provider = provider
)