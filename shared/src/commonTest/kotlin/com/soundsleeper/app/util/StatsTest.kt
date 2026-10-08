package com.soundsleeper.app.util

import com.soundsleeper.app.domain.model.Stats
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 표본 표준편차(n-1)를 쓰는지 고정한다.
 *
 * 모표준편차(n)로 바뀌면 소음 통계가 전부 과소평가되고, 리포트의 소음 위험 판정
 * 임계값이 조용히 느슨해진다. 값이 비슷해서 눈으로는 알아채기 어렵다.
 */
class StatsTest {

    // 표본표준편차 교과서 예제: 평균 5.0, 표본 stddev ≈ 2.13809
    private val sample = listOf(2f, 4f, 4f, 4f, 5f, 5f, 7f, 9f)

    @Test
    fun statsFromUsesSampleStandardDeviation() {
        val s = Stats.from(sample)
        assertEquals(5.0f, s.avg, absoluteTolerance = 1e-4f)
        assertEquals(2.13809f, s.stddev, absoluteTolerance = 1e-4f)
        assertEquals(2f, s.min)
        assertEquals(9f, s.max)
    }

    @Test
    fun statsUtilAgreesWithStatsFrom() {
        val a = Stats.from(sample)
        val b = StatsUtil.computeStats(sample)
        assertEquals(a.avg, b.avg, absoluteTolerance = 1e-4f)
        assertEquals(a.stddev, b.std, absoluteTolerance = 1e-4f)
        assertEquals(a.min, b.min)
        assertEquals(a.max, b.max)
    }

    @Test
    fun emptyListYieldsDefaults() {
        assertEquals(Stats(), Stats.from(emptyList()))
        assertEquals(0, StatsUtil.computeStats(emptyList()).count)
    }

    /** n=1 은 표본 분산이 정의되지 않는다. NaN 이 아니라 0 을 쓰는지 고정한다. */
    @Test
    fun singleElementYieldsZeroDeviationNotNaN() {
        val s = Stats.from(listOf(42f))
        assertEquals(0f, s.stddev)
        assertEquals(42f, s.avg)

        val r = StatsUtil.computeStats(listOf(42f))
        assertEquals(0f, r.std)
        assertEquals(1, r.count)
        assertEquals(42f, r.last)
    }
}
