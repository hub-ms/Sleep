package com.soundsleeper.app.domain.model

import com.soundsleeper.app.enum_.AuthProvider
import kotlinx.datetime.LocalDateTime

data class User(
    val userId: Long,
    val email: String?,
    val nickname: String,
    val profileImageUrl: String?,
    val isPremium: Boolean,
    val isActive: Boolean,
    val lastLoginAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?,
) {
    sealed interface AuthInfo {
        data object Guest : AuthInfo
        data class Member(
            val memberEmail: String?,
            val authId: String,
            val provider: AuthProvider
        ) : AuthInfo
    }
}