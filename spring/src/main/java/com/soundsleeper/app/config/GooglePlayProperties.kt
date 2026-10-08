package com.soundsleeper.app.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "app.google-play")
@Validated
data class GooglePlayProperties(
    @field:NotBlank val packageName: String,
    val serviceAccountKeyPath: String? = null,
    val rtdnAudience: String? = null,
    val rtdnAllowedServiceAccountEmail: String? = null,
)
