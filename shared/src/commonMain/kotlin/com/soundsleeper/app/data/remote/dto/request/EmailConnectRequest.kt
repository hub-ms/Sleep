package com.soundsleeper.app.data.remote.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 이메일 연결 요청.
 *
 * 예전에는 emailToken 하나를 보냈는데, 그 토큰을 만들어 Redis 에 넣는 코드가 서버 어디에도
 * 없어서 연결은 구조적으로 성공할 수 없었다. 실제로 동작하는 인증코드(AUTH_CODE) 방식에
 * 맞춰 (이메일, 코드) 쌍을 보낸다.
 */
@Serializable
data class EmailConnectRequest(
    @SerialName("email") val email: String,
    @SerialName("code") val code: String,
)
