package com.sleepytime.shared.domain.repository

import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.enum_.AuthProvider

interface AuthInfoRepository {

    fun save(socialInfo: User.AuthInfo): User.AuthInfo

    fun findByAuthIdAndProvider(
        socialId: String,
        provider: AuthProvider
    ): User.AuthInfo?

    fun findByUser_UserId(userId: Long): User?

    fun deleteByUser_UserId(userId: Long)
}