package com.soundsleeper.app.data.remote.dto

import com.soundsleeper.app.data.remote.dto.response.UserResponse
import com.soundsleeper.app.enum_.AuthProvider
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 서버가 내려주는 연결된 로그인 수단이 실제로 앱까지 도착하는지 고정한다.
 *
 * 이 필드가 DTO 에 아예 없던 시절, 계정 화면은 "지금 로그인한 수단" 하나만 알 수 있었다.
 * 그래서 카카오·구글이 둘 다 연결돼 있어도 화면은 수단이 하나뿐이라고 판단해 해제 버튼을
 * 비활성으로 두었고(눌러도 무반응), 서버의 해제 가드와 판단이 어긋나 정상적인 해제가
 * 409 로 막히기도 했다. 눈으로는 "연결 해제가 고장났다"로만 보이는 증상이라 묶어 둔다.
 */
class UserResponseConnectedProvidersTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun connectedProvidersArrivesFromServerJson() {
        val body = """
            {
              "userId": 7,
              "nickname": "자는사람",
              "email": "me@example.com",
              "isPremium": false,
              "isActive": true,
              "createdAt": "2026-01-01T00:00:00",
              "connectedProviders": ["KAKAO", "GOOGLE"]
            }
        """.trimIndent()

        val user = json.decodeFromString<UserResponse>(body)

        assertEquals(setOf(AuthProvider.KAKAO, AuthProvider.GOOGLE), user.connectedProviders)
        // 화면은 이 크기로 "마지막 로그인 수단인가"를 판단한다. 2개면 해제가 가능해야 한다.
        assertTrue(user.connectedProviders.size > 1)
    }

    /**
     * 이 필드를 내려주지 않는 응답(로그인·프로필 저장 등)에서도 역직렬화가 깨지면 안 된다.
     * 빈 집합은 "연결된 수단이 없다"가 아니라 "이 응답은 모른다"는 뜻이다.
     */
    @Test
    fun missingConnectedProvidersDoesNotBreakDeserialization() {
        val body = """
            {
              "userId": 7,
              "nickname": "자는사람",
              "isPremium": false,
              "isActive": true,
              "createdAt": "2026-01-01T00:00:00"
            }
        """.trimIndent()

        val user = json.decodeFromString<UserResponse>(body)

        assertEquals(emptySet(), user.connectedProviders)
    }
}
