package com.sleepytime.app.config

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

// Google Cloud Pub/Sub 푸시 구독이 웹훅 요청에 첨부하는 OIDC 토큰을 검증한다.
// 서명(RS256), audience(웹훅 URL), 만료 여부를 Google의 공개키로 검증하며,
// 토큰 발급자(email)가 우리가 설정한 Pub/Sub 푸시 서비스 계정과 일치하는지도 함께 확인한다.
@Component
class RtdnPushVerifier(
    private val props: GooglePlayProperties,
) {
    private val log = LoggerFactory.getLogger(RtdnPushVerifier::class.java)

    private val verifier: GoogleIdTokenVerifier? by lazy {
        val audience = props.rtdnAudience?.takeIf { it.isNotBlank() } ?: return@lazy null
        GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(listOf(audience))
            .build()
    }

    fun verify(authorizationHeader: String?): Boolean {
        val currentVerifier = verifier ?: run {
            log.warn("app.google-play.rtdn-audience 가 설정되어 있지 않아 RTDN 요청을 거부합니다.")
            return false
        }
        val token = authorizationHeader?.removePrefix("Bearer ")?.trim()
        if (token.isNullOrEmpty()) return false

        val idToken = runCatching { currentVerifier.verify(token) }
            .onFailure { log.error("RTDN OIDC 토큰 검증 중 예외: ${it.message}", it) }
            .getOrNull() ?: return false

        val allowedEmail = props.rtdnAllowedServiceAccountEmail?.takeIf { it.isNotBlank() }
        if (allowedEmail != null && idToken.payload.email != allowedEmail) {
            log.warn("RTDN 토큰 발급자 불일치: expected=$allowedEmail actual=${idToken.payload.email}")
            return false
        }
        return true
    }
}
