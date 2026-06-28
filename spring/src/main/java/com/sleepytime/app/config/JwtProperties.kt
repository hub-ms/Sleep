package com.sleepytime.app.config

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "jwt")
@Validated
data class JwtProperties(
    @field:NotBlank val secretKey: String,
    @field:Positive val accessTokenExpiry: Long = 1800000L,
    @field:Positive val refreshTokenExpiry: Long = 604800000L
)
