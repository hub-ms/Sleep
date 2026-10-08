package com.somniotune.app.service_new

import com.somniotune.app.dto_new.SocialLoginInfo

interface SocialVerifier {
    fun verify(accessToken: String): SocialLoginInfo
}