package com.sleepytime.app

import com.sleepytime.app.config.EmailAuthProperties
import com.sleepytime.app.config.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class, EmailAuthProperties::class)
class SleepApplication

fun main(args: Array<String>) {
    runApplication<SleepApplication>(*args)
}
