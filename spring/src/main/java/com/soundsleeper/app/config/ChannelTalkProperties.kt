package com.soundsleeper.app.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "channel-talk")
@Validated
data class ChannelTalkProperties(
    @field:NotBlank val hashSecret: String,
)