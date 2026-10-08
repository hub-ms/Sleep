package com.somniotune.app.data.auth

import com.somniotune.app.data.local.UserEntity
import com.somniotune.app.data.local.mapper.toUserDomain
import com.somniotune.app.domain.model.AuthStatus
import com.somniotune.app.enum_.AuthProvider

class AuthStatusResolver {
    fun resolve(
        userEntity: UserEntity?,
        provider: AuthProvider?,
        accessToken: String,
        hasRefreshToken: Boolean,
        isAccessTokenValid: Boolean,
    ): AuthStatus {
        return when {
            accessToken.isNotEmpty() && provider != null && userEntity == null -> AuthStatus.Loading
            userEntity == null || provider == null -> AuthStatus.NotLoggedIn
            !hasRefreshToken ->
                AuthStatus.TokenExpired(
                    user = userEntity.toUserDomain(),
                    provider = provider
                )
            accessToken.isNotEmpty() || isAccessTokenValid ->
                AuthStatus.LoggedIn(
                    user = userEntity.toUserDomain(),
                    provider = provider,
                    accessToken = accessToken
                )
            else -> AuthStatus.NotLoggedIn
        }
    }
}