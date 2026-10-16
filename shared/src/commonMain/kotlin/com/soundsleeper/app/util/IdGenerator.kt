package com.soundsleeper.app.util

import com.benasher44.uuid.uuid4
import com.soundsleeper.app.domain.model.User
import com.soundsleeper.app.enum_.AuthProvider
import kotlin.time.Clock

object IdGenerator {
    fun generateSessionId(user: User.AuthInfo): String {
        val prefix = when (user) {
            is User.AuthInfo.Guest -> "guest"
            is User.AuthInfo.Member -> when (user.provider) {
                AuthProvider.KAKAO -> "kakao"
                AuthProvider.GOOGLE -> "google"
                AuthProvider.EMAIL -> "email"
            }
        }
        val shortId = when (user) {
            User.AuthInfo.Guest -> "guest"
            is User.AuthInfo.Member -> user.authId.takeLast(8)
        }
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val random = (0..0xFFFFFF)
            .random()
            .toString(16)
            .padStart(6, '0')
        return "session_${prefix}_${shortId}_${timestamp}_$random"
    }
    fun randomUuidString(): String {
        return uuid4().toString()
    }
}
