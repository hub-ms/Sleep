package com.soundsleeper.app.util

import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64

class JwtLocalParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun decodePayload(token: String): String {
        val parts = token.split(".")
        if (parts.size != 3) throw IllegalArgumentException("Invalid JWT")

        return Base64.decode(parts[1]).contentToString()
    }

    fun isExpired(token: String, skewSeconds: Long = 30): Boolean {
        return try {
            val payload = decodePayload(token)
            val jsonElement = json.parseToJsonElement(payload).jsonObject
            val exp = jsonElement["exp"]?.jsonPrimitive?.longOrNull ?: 0L

            val now = Clock.System.now().epochSeconds
            exp <= (now + skewSeconds)
        } catch (_: Exception) {
            true
        }
    }
}