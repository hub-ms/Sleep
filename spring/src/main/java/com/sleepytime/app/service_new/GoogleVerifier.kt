package com.sleepytime.app.service_new

import com.sleepytime.app.dto_new.SocialLoginInfo
import com.sleepytime.shared.enum_.AuthProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class GoogleVerifier : SocialVerifier {
    private val log = LoggerFactory.getLogger("")
    private val restTemplate = RestTemplate()

    private data class GoogleTokenInfo(
        val sub: String,
        val email: String,
        val email_verified: String,
        val name: String? = null,
    )
    override fun verify(accessToken: String): SocialLoginInfo {
        val response = restTemplate.getForObject(
            "https://oauth2.googleapis.com/tokeninfo?id_token=$accessToken",
            GoogleTokenInfo::class.java
        ) ?: throw IllegalArgumentException("Google token verify failure")

        log.debug("Google token verify result: {}", response)
        log.debug("emailVerified: ${response.email_verified}")
        val isVerified = response.email_verified.toBoolean()

        if (!isVerified) {
            throw IllegalArgumentException("Google email required")
        }

        return SocialLoginInfo(
            email = response.email,
            nickname = response.name ?: "GoogleUser",
            socialId = response.sub,
            provider = AuthProvider.GOOGLE
        )
    }
}