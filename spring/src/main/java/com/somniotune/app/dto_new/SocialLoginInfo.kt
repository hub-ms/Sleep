package com.somniotune.app.dto_new

import com.somniotune.app.enum_.AuthProvider


data class SocialLoginInfo(
    val email: String?,
    val nickname: String,
    val profileImageUrl: String?,
    val socialId: String,
    val provider: AuthProvider
)