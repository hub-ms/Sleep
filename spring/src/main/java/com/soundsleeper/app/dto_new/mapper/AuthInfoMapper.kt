package com.soundsleeper.app.dto_new.mapper

import com.soundsleeper.app.entity_new.AuthInfoEntity
import com.soundsleeper.app.data.remote.dto.response.AuthInfoResponse
import com.soundsleeper.app.data.remote.dto.response.UserResponse
import com.soundsleeper.app.enum_.AuthProvider

// Entity -> Response DTO 변환
fun AuthInfoEntity.toResponse(
    accessToken: String,
    refreshToken: String,
    userResponse: UserResponse,
    // 이 로그인으로 계정이 처음 만들어졌는지. 앱이 가입 직후에만 페이월을 띄우는 데 쓴다.
    // 기본값 false 라서 이 정보와 무관한 호출부는 그대로 둔다.
    isNewUser: Boolean = false
): AuthInfoResponse {
    // [해결] 마찬가지로 지역 변수로 캡처
    val currentProvider = this.provider

    return AuthInfoResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
        user = userResponse,
        authId = this.authId,
        provider = currentProvider ?: AuthProvider.EMAIL,
        isNewUser = isNewUser
    )
}
