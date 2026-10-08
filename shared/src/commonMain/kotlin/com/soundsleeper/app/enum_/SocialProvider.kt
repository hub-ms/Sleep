package com.soundsleeper.app.enum_

enum class AuthProvider {
    KAKAO,
    GOOGLE,
    EMAIL;

    val isSocial: Boolean get() = this == KAKAO || this == GOOGLE
}