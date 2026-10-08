package com.soundsleeper.app.service_new.ai

import com.soundsleeper.app.dto_new.request.SleepFindingDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

/**
 * 규칙 엔진이 낸 판정을 자연스러운 문장으로 다듬는 역할만 하는 LLM 클라이언트.
 *
 * 판정(무엇이 문제인가)은 이미 앱의 규칙 엔진이 끝냈다. 여기서 수치를 다시 해석하게 하면
 * 같은 데이터에 매번 다른 결론이 나오고 근거를 추적할 수도 없다. 그래서 시스템 프롬프트에서
 * "주어진 것만 쓰고 새로운 수치를 지어내지 말 것"을 못 박는다.
 *
 * API 키는 서버에만 둔다. 클라이언트에 넣으면 추출당한다.
 */
@Component
class AiAdviceClient(
    @Value("\${ai.advice.api-key:}") private val apiKey: String,
    @Value("\${ai.advice.model:claude-sonnet-5}") private val model: String,
    @Value("\${ai.advice.base-url:https://api.anthropic.com/v1/messages}") private val baseUrl: String,
) {
    private val log = LoggerFactory.getLogger(AiAdviceClient::class.java)
    private val restTemplate = RestTemplate()

    private val systemPrompt = """
        당신은 수면 코치입니다. 이미 분석이 끝난 수면 지표 판정 목록을 받아, 사용자가 읽기 쉬운
        한국어 조언으로 다듬는 것이 당신의 역할입니다.

        규칙:
        - 주어진 판정과 수치만 사용하세요. 새로운 수치나 진단명을 지어내지 마세요.
        - 의학적 진단처럼 단정하지 말고, 실천할 수 있는 습관 위주로 제안하세요.
        - 3~4문장, 따뜻하지만 담백한 말투로 작성하세요.
        - 심각도가 높은 항목을 먼저 다루세요.
    """.trimIndent()

    /** 실패하면 null. 호출부는 규칙 기반 문구로 되돌아간다. */
    fun generate(findings: List<SleepFindingDto>): String? {
        if (apiKey.isBlank()) {
            log.warn("ai.advice.api-key 가 설정되지 않아 LLM 호출을 건너뜁니다.")
            return null
        }

        val userContent = findings.joinToString("\n") { finding ->
            "- ${finding.code} (심각도 ${finding.severity}): 측정값 ${finding.measured}, 기준 ${finding.threshold}"
        }

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("x-api-key", apiKey)
            set("anthropic-version", "2023-06-01")
        }

        val body = mapOf(
            "model" to model,
            "max_tokens" to 512,
            "system" to systemPrompt,
            "messages" to listOf(
                mapOf("role" to "user", "content" to userContent)
            )
        )

        return runCatching {
            val response = restTemplate.exchange(
                baseUrl,
                HttpMethod.POST,
                HttpEntity(body, headers),
                Map::class.java
            ).body ?: return null

            // content 는 블록 배열이다. text 블록만 이어 붙인다.
            val content = response["content"] as? List<*> ?: return null
            content.mapNotNull { block ->
                (block as? Map<*, *>)?.takeIf { it["type"] == "text" }?.get("text") as? String
            }.joinToString("\n").takeIf { it.isNotBlank() }
        }.onFailure {
            log.warn("수면 조언 생성 실패: {}", it.message)
        }.getOrNull()
    }
}
