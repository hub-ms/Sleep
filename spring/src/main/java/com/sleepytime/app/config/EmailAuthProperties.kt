package com.sleepytime.app.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.auth.email")
data class EmailAuthProperties(
    val codePrefix: String = "AUTH_CODE:",
    val codeExpirationTime: Long = 180L,
    val fromEmail: String = "cp03.kim@gmail.com",
    val subject: String = "[수면 앱] 이메일 인증 번호 안내"
)