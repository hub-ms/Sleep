package com.soundsleeper.app.dto_new.response

data class SleepAdviceResponse(
    val sessionId: String,
    val adviceText: String,
    /** "LLM" 또는 "CACHE". 비용 추적용. */
    val source: String,
)
