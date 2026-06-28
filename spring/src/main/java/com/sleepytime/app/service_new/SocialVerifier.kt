package com.sleepytime.app.service_new

import com.sleepytime.app.dto_new.SocialLoginInfo

interface SocialVerifier {
    fun verify(accessToken: String): SocialLoginInfo
}