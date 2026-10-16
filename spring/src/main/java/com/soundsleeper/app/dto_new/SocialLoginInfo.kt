package com.soundsleeper.app.dto_new

import com.soundsleeper.app.enum_.AuthProvider


data class SocialLoginInfo(
    val email: String?,
    val nickname: String,
    val profileImageUrl: String?,
    val socialId: String,
    val provider: AuthProvider
)