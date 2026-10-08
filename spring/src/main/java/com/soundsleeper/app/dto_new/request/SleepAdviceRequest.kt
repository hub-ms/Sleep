package com.soundsleeper.app.dto_new.request

/**
 * 앱이 보내는 것은 원본 센서 데이터가 아니라 이미 판정이 끝난 결과다.
 * 서버는 이 판정을 해석하지 않고 문장으로만 옮긴다.
 */
data class SleepAdviceRequest(
    val sessionId: String = "",
    val findings: List<SleepFindingDto> = emptyList(),
)

data class SleepFindingDto(
    val code: String = "",
    val severity: String = "",
    val measured: Double = 0.0,
    val threshold: Double = 0.0,
)
