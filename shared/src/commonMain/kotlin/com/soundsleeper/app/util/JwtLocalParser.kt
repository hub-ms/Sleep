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

        // JWT는 base64url(-, _, 패딩 없음)이라 표준 Base64로 그대로 돌리면 깨질 수 있어
        // 표준 알파벳 + 패딩으로 정규화한다. 예전에는 디코딩한 바이트 배열에 contentToString()을
        // 써서 "[123, 34, ...]" 같은 디버그 문자열이 나왔다 — 유효 JSON 배열 문법이라 파싱은
        // 되지만 .jsonObject 캐스팅에서 터져 isExpired()가 항상 true를 반환했다(토큰이 멀쩡해도
        // 로그인 직후부터 "만료됨"으로 판정되어 로그인 상태로 절대 전환되지 않던 원인).
        val normalized = parts[1]
            .replace('-', '+')
            .replace('_', '/')
            .let { it + "=".repeat((4 - it.length % 4) % 4) }

        return Base64.decode(normalized).decodeToString()
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