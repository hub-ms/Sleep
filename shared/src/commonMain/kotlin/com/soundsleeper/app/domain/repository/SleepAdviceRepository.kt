package com.soundsleeper.app.domain.repository

import com.soundsleeper.app.util.SleepAdviceAnalyzer
import com.soundsleeper.app.domain.model.SleepAdvice

interface SleepAdviceRepository {
    suspend fun getAdvice(sessionId: String, findings: List<SleepAdviceAnalyzer.Finding>): SleepAdvice
}