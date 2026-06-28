package com.sleepytime.shared.util

import com.benasher44.uuid.uuid4
import com.russhwolf.settings.ObservableSettings
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.util.PreferencesKeys.App.UNIQUE_GUEST_ID
import kotlinx.datetime.Clock
import kotlin.random.Random

object IdGenerator {
    fun generateSessionId(user: User.AuthInfo): String {
        val prefix = when (user) {
            is User.AuthInfo.Guest -> "guest"
            is User.AuthInfo.Member -> {
                when (user.provider) {
                    AuthProvider.KAKAO -> "kakao"
                    AuthProvider.GOOGLE -> "google"
                    AuthProvider.APPLE -> "apple"
                    AuthProvider.EMAIL -> "email"
                    else -> {
                        throw IllegalArgumentException("Invalid AuthProvider: ${user.provider}")
                    }
                }
            }
        }
        val shortId = user.authId.takeLast(8)
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val random = (0..0xFFFFFF).random().toString(16).padStart(6, '0')

        return "session_${prefix}_${shortId}_${timestamp}_$random"
    }
    fun randomUuidString(): String {
        return uuid4().toString()
    }
    private fun generateRandomId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..12).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
