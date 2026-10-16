package com.soundsleeper.app.data.local.repository

import com.russhwolf.settings.ObservableSettings
import com.soundsleeper.app.data.remote.api.SleepAdviceApi
import com.soundsleeper.app.data.remote.dto.request.SleepAdviceRequest
import com.soundsleeper.app.data.remote.dto.request.SleepFindingDto
import com.soundsleeper.app.util.SleepAdviceAnalyzer
import com.soundsleeper.app.domain.model.SleepAdvice
import com.soundsleeper.app.domain.repository.SleepAdviceRepository
import io.github.aakira.napier.Napier

class SleepAdviceRepositoryImpl(
    private val api: SleepAdviceApi,
    private val settings: ObservableSettings,
): SleepAdviceRepository {



    override suspend fun getAdvice(
        sessionId: String,
        findings: List<SleepAdviceAnalyzer.Finding>,
    ): SleepAdvice {
        val fallback = findings.map { it.localizedFallbackText() }.joinToString("\n\n")

        val isUnjudgeable = findings.all { it.code == SleepAdviceAnalyzer.FindingCode.INSUFFICIENT_DATA }
        if (sessionId.isBlank() || findings.isEmpty() || isUnjudgeable) {
            return SleepAdvice(text = fallback, isFromServer = false)
        }

        cachedAdvice(sessionId)?.let { cached ->
            return SleepAdvice(text = cached, isFromServer = true)
        }

        val request = SleepAdviceRequest(
            sessionId = sessionId,
            findings = findings.map {
                SleepFindingDto(
                    code = it.code.name,
                    severity = it.severity.name,
                    measured = it.measured,
                    threshold = it.threshold,
                )
            }
        )

        return api.generateAdvice(request).fold(
            onSuccess = { response ->
                val text = response.adviceText.takeIf { it.isNotBlank() }
                if (text == null) {
                    SleepAdvice(text = fallback, isFromServer = false)
                } else {
                    settings.putString(cacheKey(sessionId), text)
                    SleepAdvice(text = text, isFromServer = true)
                }
            },
            onFailure = {
                Napier.d("수면 조언 생성 실패, 규칙 기반 문구로 대체합니다: ${it.message}")
                SleepAdvice(text = fallback, isFromServer = false)
            }
        )
    }

    private fun cachedAdvice(sessionId: String): String? =
        settings.getStringOrNull(cacheKey(sessionId))?.takeIf { it.isNotBlank() }

    private fun cacheKey(sessionId: String) = "$CACHE_PREFIX$sessionId"

    private companion object {
        const val CACHE_PREFIX = "sleep_advice_"
    }
}
