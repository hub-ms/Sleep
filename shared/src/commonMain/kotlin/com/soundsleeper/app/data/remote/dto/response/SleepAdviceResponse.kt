package com.soundsleeper.app.data.remote.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class SleepAdviceResponse(
    val sessionId: String,
    /** LLM 이 다듬은 조언 문장. */
    val adviceText: String,
    /** "LLM" 또는 "CACHE". 디버깅·비용 추적용이며 화면에는 쓰지 않는다. */
    val source: String = "LLM",
)
