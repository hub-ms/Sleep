package com.soundsleeper.app.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 페이월의 플랜 정렬과 할인율 뱃지가 이 두 함수에 걸려 있다.
 *
 * 월간/연간은 Play 가 내려주는 productId 가 같아서(`dive_premium`) 결제 주기로만 구분된다.
 * [BillingPeriod.approximateDays] 가 두 주기의 순서를 뒤집으면 카드 순서와 "연간에만 붙는"
 * 할인율 뱃지가 함께 엉뚱한 카드로 간다. [BillingPeriod.savingsPercent] 는 가격 정책이
 * 바뀌었을 때 "0% 아낌" 같은 뱃지를 띄우지 않는 것까지가 계약이다.
 */
class BillingPeriodTest {

    @Test
    fun approximateDaysOrdersYearlyAboveMonthly() {
        assertEquals(365, BillingPeriod.approximateDays("P1Y"))
        assertEquals(30, BillingPeriod.approximateDays("P1M"))
        assertEquals(7, BillingPeriod.approximateDays("P1W"))
        assertEquals(3, BillingPeriod.approximateDays("P3D"))
    }

    /** 파싱 실패는 -1 이라 정렬에서 맨 뒤로 간다 — 알 수 없는 주기가 연간을 밀어내지 않는다. */
    @Test
    fun approximateDaysReturnsMinusOneForUnparseable() {
        assertEquals(-1, BillingPeriod.approximateDays(null))
        assertEquals(-1, BillingPeriod.approximateDays(""))
        assertEquals(-1, BillingPeriod.approximateDays("1Y"))
        assertEquals(-1, BillingPeriod.approximateDays("PT30M"))
    }

    @Test
    fun savingsPercentComparesPerMonthPrice() {
        // 월 5,000원 vs 연 48,000원(= 월 4,000원) → 20% 절약
        assertEquals(
            20,
            BillingPeriod.savingsPercent(
                monthlyMicrosPerMonth = 5_000_000_000L,
                yearlyMicrosPerYear = 48_000_000_000L,
            )
        )
    }

    /** 연간이 더 비싸거나 같으면 뱃지를 붙이지 않는다. */
    @Test
    fun savingsPercentIsNullWhenYearlyIsNotCheaper() {
        assertNull(
            BillingPeriod.savingsPercent(
                monthlyMicrosPerMonth = 5_000_000_000L,
                yearlyMicrosPerYear = 60_000_000_000L,
            )
        )
        assertNull(
            BillingPeriod.savingsPercent(
                monthlyMicrosPerMonth = 5_000_000_000L,
                yearlyMicrosPerYear = 72_000_000_000L,
            )
        )
    }

    @Test
    fun savingsPercentIsNullWhenAPriceIsMissing() {
        assertNull(BillingPeriod.savingsPercent(0L, 48_000_000_000L))
        assertNull(BillingPeriod.savingsPercent(5_000_000_000L, 0L))
    }

    @Test
    fun toLocalizedLabelPicksLargestUnit() {
        assertEquals("7일", BillingPeriod.toLocalizedLabel("P7D"))
        assertEquals("1개월", BillingPeriod.toLocalizedLabel("P1M"))
        assertEquals("1년", BillingPeriod.toLocalizedLabel("P1Y"))
        assertEquals("14일", BillingPeriod.toLocalizedLabel("P2W"))
        assertNull(BillingPeriod.toLocalizedLabel(null))
    }
}
