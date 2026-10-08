package com.soundsleeper.app

import com.soundsleeper.app.config.ChannelTalkProperties
import com.soundsleeper.app.config.EmailAuthProperties
import com.soundsleeper.app.config.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class, EmailAuthProperties::class, ChannelTalkProperties::class)
class SleepApplication

fun main(args: Array<String>) {
    runApplication<SleepApplication>(*args)
}
