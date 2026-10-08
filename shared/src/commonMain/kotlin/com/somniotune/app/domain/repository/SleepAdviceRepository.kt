package com.somniotune.app.domain.repository

import com.somniotune.app.util.SleepAdviceAnalyzer
import com.somniotune.app.domain.model.SleepAdvice

interface SleepAdviceRepository {
    suspend fun getAdvice(sessionId: String, findings: List<SleepAdviceAnalyzer.Finding>): SleepAdvice
}