package com.soundsleeper.app.domain.model

import com.soundsleeper.app.enum_.AuthProvider

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