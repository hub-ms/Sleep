package com.sleepytime.app.controller_new

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sleepytime.app.config.RtdnPushVerifier
import com.sleepytime.app.dto_new.billing.DeveloperNotification
import com.sleepytime.app.dto_new.billing.PubSubPushEnvelope
import com.sleepytime.app.service_new.SubscriptionService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Base64

// Google Play의 Real-time Developer Notifications(RTDN)는 Cloud Pub/Sub 푸시 구독을 통해
// 이 엔드포인트로 전달된다. 앱 JWT가 아니라 Pub/Sub가 붙이는 OIDC 토큰으로 인증하므로
// SecurityConfig에서 이 경로는 permitAll 처리하고, 인증은 RtdnPushVerifier가 별도로 수행한다.
@RestController
@RequestMapping("/api/billing/rtdn")
class RtdnWebhookController(
    private val rtdnVerifier: RtdnPushVerifier,
    private val subscriptionService: SubscriptionService,
) {
    private val log = LoggerFactory.getLogger(RtdnWebhookController::class.java)
    private val objectMapper = jacksonObjectMapper()

    @PostMapping
    fun receive(
        @RequestHeader("Authorization") authorization: String?,
        @RequestBody envelope: PubSubPushEnvelope,
    ): ResponseEntity<Unit> {
        if (!rtdnVerifier.verify(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val notification = runCatching {
            val decoded = Base64.getDecoder().decode(envelope.message.data)
            objectMapper.readValue(decoded, DeveloperNotification::class.java)
        }.getOrElse {
            log.error("RTDN payload 파싱 실패: ${it.message}", it)
            // Pub/Sub는 2xx가 아니면 재전송을 반복하므로, 파싱 불가능한(재시도해도 소용없는)
            // 페이로드는 로그만 남기고 200으로 응답해 무한 재시도를 막는다.
            return ResponseEntity.ok().build()
        }

        notification.subscriptionNotification?.let {
            runCatching { subscriptionService.handleRtdn(it.purchaseToken) }
                .onFailure { e -> log.error("RTDN 처리 실패 (purchaseToken=${it.purchaseToken}): ${e.message}", e) }
        }

        return ResponseEntity.ok().build()
    }
}
