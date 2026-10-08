package com.soundsleeper.app.util

/**
 * Play Billing 이 주는 ISO-8601 기간 문자열(P7D, P1M, P1Y …)을 다루는 도구.
 *
 * kotlinx-datetime 에는 ISO-8601 기간 파서가 없고, 여기서 필요한 범위는 Play 가 실제로 쓰는
 * 형태(P + 숫자 + Y/M/W/D)뿐이라 직접 파싱한다. 시간 단위(T 이하)는 구독 주기에 쓰이지 않는다.
 */
object BillingPeriod {

    data class Parsed(
        val years: Int,
        val months: Int,
        val weeks: Int,
        val days: Int
    )

    fun parse(iso: String?): Parsed? {
        if (iso.isNullOrBlank() || !iso.startsWith("P")) return null

        var years = 0
        var months = 0
        var weeks = 0
        var days = 0
        var digits = StringBuilder()
        var matchedAny = false

        for (ch in iso.drop(1)) {
            when {
                ch.isDigit() -> digits.append(ch)
                else -> {
                    val value = digits.toString().toIntOrNull() ?: return null
                    digits = StringBuilder()
                    when (ch) {
                        'Y' -> years = value
                        'M' -> months = value
                        'W' -> weeks = value
                        'D' -> days = value
                        // 시간 단위가 섞여 있으면 이 도구가 다룰 범위가 아니다.
                        else -> return null
                    }
                    matchedAny = true
                }
            }
        }
        return if (matchedAny) Parsed(years, months, weeks, days) else null
    }
    // yearUnit/monthUnit/dayUnit 기본값은 한국어다. DateTimeUtil.formatSleepDurationFromMillis 와
    // 같은 이유로, 번역된 단위가 필요한 호출부만 stringResource 로 읽은 값을 넘긴다.
    fun toLocalizedLabel(
        iso: String?,
        yearUnit: String = "년",
        monthUnit: String = "개월",
        dayUnit: String = "일",
    ): String? {
        val parsed = parse(iso) ?: return null
        return when {
            parsed.years > 0 -> "${parsed.years}$yearUnit"
            parsed.months > 0 -> "${parsed.months}$monthUnit"
            parsed.weeks > 0 -> "${parsed.weeks * 7}$dayUnit"
            parsed.days > 0 -> "${parsed.days}$dayUnit"
            else -> null
        }
    }
}
