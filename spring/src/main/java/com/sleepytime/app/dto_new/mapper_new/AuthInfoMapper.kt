package com.sleepytime.app.dto_new.mapper_new

import com.sleepytime.app.entity_new.AuthInfoEntity
import com.sleepytime.shared.data.remote.dto.response.AuthInfoResponse
import com.sleepytime.shared.data.remote.dto.response.UserResponse
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.enum_.AuthProvider

fun AuthInfoEntity.toDomain(): User.AuthInfo {
    val currentProvider = this.provider

    return if (currentProvider == null) {
        User.AuthInfo.Guest
    } else {
        User.AuthInfo.Member(
            memberEmail = this.user?.email,
            id = this.authId,
            authProvider = currentProvider
        )
    }
}

fun User.AuthInfo.toEntity(): MutableList<AuthInfoEntity> {
    // 1. 단건 AuthInfoEntity 객체를 먼저 생성합니다.
    val authInfoEntity = AuthInfoEntity(
        authId = this.authId,
        provider = this.provider,
        user = null // 관계 매핑용 UserEntity 객체는 외부에서 주입 권장
    )

    // 2. mutableListOf() 함수를 사용하여 MutableList 타입으로 감싸서 반환합니다.
    return mutableListOf(authInfoEntity)
}

// 3. Entity -> Response DTO 변환
fun AuthInfoEntity.toResponse(
    accessToken: String,
    refreshToken: String,
    userResponse: UserResponse
): AuthInfoResponse {
    // [해결] 마찬가지로 지역 변수로 캡처
    val currentProvider = this.provider

    return AuthInfoResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
        user = userResponse,
        authId = this.authId,
        provider = currentProvider ?: AuthProvider.EMAIL
    )
}