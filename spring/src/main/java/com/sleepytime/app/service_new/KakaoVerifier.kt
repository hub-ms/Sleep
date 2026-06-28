package com.sleepytime.app.service_new

import com.sleepytime.app.dto_new.SocialLoginInfo
import com.sleepytime.shared.enum_.AuthProvider
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import kotlin.collections.get

@Component
class KakaoVerifier : SocialVerifier {
    override fun verify(accessToken: String): SocialLoginInfo {
        val url = "https://kapi.kakao.com/v2/user/me"
        val headers = HttpHeaders().apply {
            set("Authorization", "Bearer $accessToken")
        }

        val response = RestTemplate().exchange(
            url,
            HttpMethod.GET,
            HttpEntity<Any>(headers),
            Map::class.java
        ).body ?: throw IllegalArgumentException("카카오 토큰 검증 실패")

        val kakaoAccount = response["kakao_account"] as? Map<*, *>
        val properties = response["properties"] as? Map<*, *>

        val kakaoId = (response["id"] as? Number)?.toString()
            ?: throw IllegalArgumentException("카카오 id 없음")
        val email = kakaoAccount?.get("email") as? String ?: "${kakaoId}@kakao.user"
        val nickname = properties?.get("nickname") as? String ?: "KakaoUser"

        return SocialLoginInfo(
            email = email,
            nickname = nickname,
            socialId = kakaoId,
            provider = AuthProvider.KAKAO
        )
    }
}