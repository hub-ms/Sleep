package com.soundsleeper.app.service_new

import com.soundsleeper.app.dto_new.SocialLoginInfo
import com.soundsleeper.app.enum_.AuthProvider
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
        val profile = kakaoAccount?.get("profile") as? Map<*, *>
        val properties = response["properties"] as? Map<*, *>

        val kakaoId = (response["id"] as? Number)?.toString()
            ?: throw IllegalArgumentException("카카오 id 없음")

        // 예전에는 이메일이 없으면 "{id}@kakao.user" 를 지어내 저장했다. 그 값이 그대로
        // 계정 화면에 찍히는 바람에 사용자에게는 자기 이메일이 이상한 주소로 보였다.
        // 카카오는 이메일 동의 항목(account_email)이 승인·동의된 경우에만 이메일을 준다.
        // 받지 못했으면 "모른다"(null)로 두는 것이 정직하다. 사용자 식별은 email 이 아니라
        // socialId + provider 로 하므로(AuthInfoEntity 의 유니크 제약) 로그인에는 지장이 없다.
        val email = kakaoAccount?.get("email") as? String
        val nickname = properties?.get("nickname") as? String ?: "KakaoUser"
        val profileImageUrl = profile?.get("profile_image_url") as? String
            ?: properties?.get("profile_image") as? String

        return SocialLoginInfo(
            email = email,
            nickname = nickname,
            profileImageUrl = profileImageUrl,
            socialId = kakaoId,
            provider = AuthProvider.KAKAO
        )
    }
}