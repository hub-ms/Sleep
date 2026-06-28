package com.sleepytime.shared.data.remote.mapper

import com.sleepytime.shared.data.remote.dto.response.AuthInfoResponse
import com.sleepytime.shared.domain.model.User

fun AuthInfoResponse.toDomain() = User.AuthInfo.Member(
    memberEmail = "",
    id = authId,
    authProvider = provider
)