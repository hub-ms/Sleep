package com.soundsleeper.app.service_new

import com.soundsleeper.app.dto_new.SocialLoginInfo

interface SocialVerifier {
    fun verify(accessToken: String): SocialLoginInfo
}