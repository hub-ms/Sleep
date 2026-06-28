package com.sleepytime.app.dto_new

import com.sleepytime.shared.enum_.AuthProvider


data class SocialLoginInfo(
    val email: String?,
    val nickname: String,
    val socialId: String,
    val provider: AuthProvider
)