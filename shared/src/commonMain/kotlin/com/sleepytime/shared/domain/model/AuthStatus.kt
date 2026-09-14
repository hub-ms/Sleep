package com.sleepytime.shared.domain.model

import com.sleepytime.shared.enum_.AuthProvider

sealed class AuthStatus {
    object Loading: AuthStatus()
    object FirstLaunch: AuthStatus()
    object NotLoggedIn: AuthStatus()
    object LoggedOut: AuthStatus()

    data class LoggedIn(
        val user: User,
        val provider: AuthProvider,
        val accessToken: String
    ): AuthStatus()
    data class TokenExpired(
        val user: User,
        val provider: AuthProvider
    ) : AuthStatus()
}