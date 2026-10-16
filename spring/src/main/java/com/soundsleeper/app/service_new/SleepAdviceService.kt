package com.soundsleeper.app.service_new

import com.soundsleeper.app.dto_new.request.SleepAdviceRequest
import com.soundsleeper.app.dto_new.response.SleepAdviceResponse
import com.soundsleeper.app.dto_new.request.SleepFindingDto
import com.soundsleeper.app.service_new.ai.AiAdviceClient
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * 수면 조언 생성 + 캐싱.
 *
 * LLM 호출은 요청마다 돈이 나간다. 같은 세션의 같은 판정에 대해서는 결과가 달라질 이유가
 * 없으므로 캐시로 막는다. 캐시 키에 sessionId 만 쓰지 않고 판정 내용의 해시까지 넣는 이유는,
 * 규칙 엔진이 바뀌어 같은 세션을 다르게 판정하게 됐을 때 옛 조언이 그대로 나가는 것을
 * 막기 위해서다.
 *
 * 앱에도 로컬 캐시가 있어서 보통은 여기까지 오지 않는다. 이 캐시는 앱을 지웠다 깔았거나
 * 기기를 바꾼 경우처럼 로컬 캐시가 비었을 때를 받아 준다.
 */
@Service
class SleepAdviceService(
    private val aiAdviceClient: AiAdviceClient,
    private val redisTemplate: StringRedisTemplate,
) {
    private val log = LoggerFactory.getLogger(SleepAdviceService::class.java)

    fun generate(request: SleepAdviceRequest): SleepAdviceResponse {
        require(request.sessionId.isNotBlank()) { "sessionId 는 비어 있을 수 없습니다" }
        require(request.findings.isNotEmpty()) { "findings 는 비어 있을 수 없습니다" }

        val key = cacheKey(request.sessionId, request.findings)

        runCatching { redisTemplate.opsForValue().get(key) }
            .onFailure { log.warn("조언 캐시 조회 실패(무시하고 생성으로 진행): {}", it.message) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { cached ->
                return SleepAdviceResponse(request.sessionId, cached, source = "CACHE")
            }

        val generated = aiAdviceClient.generate(request.findings)
            ?: throw IllegalStateException("조언 생성에 실패했습니다")

        runCatching { redisTemplate.opsForValue().set(key, generated, CACHE_TTL) }
            .onFailure { log.warn("조언 캐시 저장 실패(응답에는 영향 없음): {}", it.message) }

        return SleepAdviceResponse(request.sessionId, generated, source = "LLM")
    }

    private fun cacheKey(sessionId: String, findings: List<SleepFindingDto>): String {
        // 판정 목록을 문자열로 정규화해 해시한다. 순서가 달라도 같은 내용이면 같은 키가 되도록 정렬한다.
        val normalized = findings
            .map { "${it.code}:${it.severity}:${it.measured}:${it.threshold}" }
            .sorted()
            .joinToString("|")
        return "$KEY_PREFIX$sessionId:${normalized.hashCode()}"
    }

    private companion object {
        const val KEY_PREFIX = "sleep-advice:"
        val CACHE_TTL: Duration = Duration.ofDays(30)
    }
}
