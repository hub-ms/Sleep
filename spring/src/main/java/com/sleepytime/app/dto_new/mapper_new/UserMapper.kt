package com.sleepytime.app.dto_new.mapper_new

import com.sleepytime.app.entity_new.UserEntity
import com.sleepytime.shared.data.remote.dto.response.UserResponse
import com.sleepytime.shared.domain.model.User
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.LocalDateTime as JavaLocalDateTime
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime

// 1. Entity (Java) -> Domain Model 변환
fun UserEntity.toDomain(): User {
    return User(
        userId = this.userId,
        nickname = this.nickname,
        email = this.email,
        profileImageUrl = this.profileImageUrl,
        isPremium = this.isPremium,
        isActive = this.isActive,
        lastLoginAt = this.lastLoginAt?.toKotlinLocalDateTime(),
        createdAt = this.createdAt.toKotlinLocalDateTime(),
        updatedAt = this.updatedAt.toKotlinLocalDateTime(),
    )
}

// 2. Domain Model -> Entity (Java) 변환
fun User.toEntity(): UserEntity {
    return UserEntity(
        userId = this.userId,
        nickname = this.nickname,
        email = this.email,
        profileImageUrl = this.profileImageUrl,
        isPremium = this.isPremium,
        lastLoginAt = this.lastLoginAt?.toJavaLocalDateTime(),

        // 주의: entity 생성 시 필요한 나머지 JPA 관계 객체(authInfo 등) 및 필수 날짜 필드가 있다면
        // 여기에 비즈니스 로직에 맞게 주입해주어야 합니다.
        createdAt = JavaLocalDateTime.now(),
        updatedAt = JavaLocalDateTime.now()
    )
}

// 3. Entity (Java) -> Response DTO (Kotlinx) 변환
fun UserEntity.toResponse(): UserResponse {
    return UserResponse(
        userId = this.userId,
        nickname = this.nickname,
        email = this.email,
        profileImageUrl = this.profileImageUrl,
        isActive = this.isActive,
        isPremium = this.isPremium,

        // [해결] Nullable Java LocalDateTime -> kotlinx.datetime.LocalDateTime 수동 조립
        lastLoginAt = this.lastLoginAt?.let { javaTime ->
            KotlinLocalDateTime(
                year = javaTime.year,
                monthNumber = javaTime.monthValue,
                dayOfMonth = javaTime.dayOfMonth,
                hour = javaTime.hour,
                minute = javaTime.minute,
                second = javaTime.second,
                nanosecond = javaTime.nano
            )
        },

        // [해결] Non-null Java LocalDateTime -> kotlinx.datetime.LocalDateTime 수동 조립
        createdAt = this.createdAt.let { javaTime ->
            KotlinLocalDateTime(
                year = javaTime.year,
                monthNumber = javaTime.monthValue,
                dayOfMonth = javaTime.dayOfMonth,
                hour = javaTime.hour,
                minute = javaTime.minute,
                second = javaTime.second,
                nanosecond = javaTime.nano
            )
        },
        updatedAt = this.updatedAt.let { javaTime ->
            KotlinLocalDateTime(
                year = javaTime.year,
                monthNumber = javaTime.monthValue,
                dayOfMonth = javaTime.dayOfMonth,
                hour = javaTime.hour,
                minute = javaTime.minute,
                second = javaTime.second,
                nanosecond = javaTime.nano
            )
        }
    )
}