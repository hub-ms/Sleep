package com.soundsleeper.app.util

import kotlin.math.roundToInt

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
    /**
     * 기간을 대략적인 일수로 환산한다. **정렬·비교 전용**이고 달력상 정확한 날짜 계산이 아니다
     * (한 달을 30일, 한 해를 365일로 친다). 파싱할 수 없으면 -1 — 알 수 없는 항목이 정렬에서
     * 맨 뒤로 가도록.
     */
    fun approximateDays(iso: String?): Int {
        val parsed = parse(iso) ?: return -1
        return parsed.years * 365 + parsed.months * 30 + parsed.weeks * 7 + parsed.days
    }

    /**
     * 연간 플랜이 월간 대비 몇 % 싼지. 월 환산 가격으로 견준다.
     *
     * 0 이하(= 연간이 더 비싸거나 같음)면 null 을 돌려줘 뱃지를 붙이지 않는다 — 가격 정책이
     * 바뀌었을 때 "0% 할인" 같은 민망한 뱃지가 뜨지 않게 한다.
     */
    fun savingsPercent(monthlyMicrosPerMonth: Long, yearlyMicrosPerYear: Long): Int? {
        if (monthlyMicrosPerMonth <= 0L || yearlyMicrosPerYear <= 0L) return null
        val yearlyPerMonth = yearlyMicrosPerYear / 12.0
        // toInt() 로 자르면 안 된다. 정확히 20% 인 가격도 부동소수 오차로 19.999… 가 되어
        // 19% 로 깎여 표시된다(월 5,000 / 연 48,000 이 실제로 그렇다).
        val percent = ((1.0 - yearlyPerMonth / monthlyMicrosPerMonth) * 100).roundToInt()
        return percent.takeIf { it > 0 }
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
