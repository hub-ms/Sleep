package com.sleepytime.shared.enum_

enum class AuthProvider {
    KAKAO,
    GOOGLE,
    APPLE,
    EMAIL;

    val isSocial: Boolean get() = this == KAKAO || this == GOOGLE || this == APPLE
}