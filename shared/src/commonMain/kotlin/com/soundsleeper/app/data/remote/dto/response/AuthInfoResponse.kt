package com.soundsleeper.app.data.remote.dto.response

import com.soundsleeper.app.enum_.AuthProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthInfoResponse(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String,
    @SerialName("user") val user: UserResponse,
    @SerialName("authId") val authId: String,
    @SerialName("provider") val provider: AuthProvider,
    // 이 응답으로 계정이 "처음 만들어졌는지". 가입 직후에만 페이월을 보여주기 위해 서버가 채운다.
    // 기본값을 둬서 이 필드를 보내지 않는 구버전 서버 응답과도 호환된다.
    @SerialName("isNewUser") val isNewUser: Boolean = false
)
