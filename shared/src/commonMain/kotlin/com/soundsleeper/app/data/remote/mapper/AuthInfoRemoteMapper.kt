package com.soundsleeper.app.data.remote.mapper

import com.soundsleeper.app.data.remote.dto.response.AuthInfoResponse
import com.soundsleeper.app.domain.model.User

fun AuthInfoResponse.toDomain() = User.AuthInfo.Member(
    memberEmail = "",
    authId = authId,
    provider = provider
)