package com.soundsleeper.app.data.sleep

import com.soundsleeper.app.domain.model.SleepStage
import com.soundsleeper.app.enum_.SleepStageType
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * 리포트의 점수/단계별 시간/단계 분포/기상 횟수가 **0이 아닌 실제 값**으로 계산되는지 고정한다.
 *
 * 과거에 SleepSessionFactory가 sleepEfficiency/Duration/wakeCount를 전부 0으로 하드코딩하고,
 * SleepSessionRepositoryImpl이 그 0값 Duration을 calculateStagesDistribution에 넘겨
 * (`total <= 0` → 빈 맵) 측정이 정상적으로 끝나도 리포트가 통째로 0으로 보였다. 눈으로는
 * "측정이 실패했다"와 구별되지 않는 증상이라 테스트로 묶어 둔다.
 */
class SleepStatisticsCalculatorTest {

    private val calculator = SleepStatisticsCalculator()
    private val start = LocalDateTime(2026, 10, 7, 23, 0)

    private fun stage(type: SleepStageType, minutes: Int) =
        SleepStage(sessionId = "s1", type = type, startTime = start, duration = minutes.minutes)

    /** 입면 10분 → Light 60 → Deep 30 → 각성 5 → REM 25 (= 잠든 115분, 침대 130분) */
    private val timeline = listOf(
        stage(SleepStageType.AWAKE, 10),
        stage(SleepStageType.LIGHT, 60),
        stage(SleepStageType.DEEP, 30),
        stage(SleepStageType.AWAKE, 5),
        stage(SleepStageType.REM, 25),
    )

    @Test
    fun leadingAwakeBecomesSleepLatencyAndIsNotDoubleCounted() {
        val d = calculator.calculateDuration(timeline)

        // 선두 AWAKE 10분은 잠복기로만, 입면 이후 AWAKE 5분은 awakeMinutes로만 — 중복 금지.
        assertEquals(10.0, d.sleepLatencyMinutes)
        assertEquals(5.0, d.awakeMinutes)
        assertEquals(60.0, d.lightMinutes)
        assertEquals(30.0, d.deepMinutes)
        assertEquals(25.0, d.remMinutes)

        // SleepSessionUtil.toReportData()의 timeInBed 계산과 같은 식.
        val timeInBed = d.awakeMinutes + d.sleepLatencyMinutes +
            d.lightMinutes + d.deepMinutes + d.remMinutes
        assertEquals(130.0, timeInBed)
    }

    @Test
    fun efficiencyIsAsleepOverTimeInBed() {
        val d = calculator.calculateDuration(timeline)
        // 잠든 115분 / 침대 130분 = 88.46% -> 88
        assertEquals(88, calculator.calculateSleepEfficiency(d))
    }

    @Test
    fun wakeEpisodesExcludeTheLeadingSleepLatency() {
        // 입면 전 AWAKE는 각성이 아니므로 중간 각성 1회만 세야 한다.
        assertEquals(1, calculator.countWakeEpisodes(timeline))
    }

    @Test
    fun distributionIsNotEmptyForARealTimeline() {
        val d = calculator.calculateDuration(timeline)
        val dist = calculator.calculateStagesDistribution(d)

        assertTrue(dist.isNotEmpty(), "실제 타임라인인데 단계 분포가 비었다 — 리포트 그래프가 비어 보인다")
        assertEquals(4, dist.size)
        assertEquals(1.0f, dist.values.sum(), absoluteTolerance = 1e-5f)
        // 분포의 분모는 awake + light + deep + rem (잠복기 제외) = 120분
        assertEquals(60f / 120f, dist[SleepStageType.LIGHT]!!, absoluteTolerance = 1e-5f)
    }

    @Test
    fun emptyTimelineStaysAtZeroInsteadOfCrashing() {
        val d = calculator.calculateDuration(emptyList())
        assertEquals(0.0, d.lightMinutes)
        assertEquals(0, calculator.calculateSleepEfficiency(d))
        assertEquals(0, calculator.countWakeEpisodes(emptyList()))
        assertTrue(calculator.calculateStagesDistribution(d).isEmpty())
    }

    @Test
    fun allAwakeTimelineHasZeroEfficiencyAndNoWakeEpisodes() {
        // mock 모드로 1분만 측정한 경우(입면 잠복기만 나옴)에 해당하는 경계값.
        val onlyLatency = listOf(stage(SleepStageType.AWAKE, 1))
        val d = calculator.calculateDuration(onlyLatency)
        assertEquals(1.0, d.sleepLatencyMinutes)
        assertEquals(0.0, d.awakeMinutes)
        assertEquals(0, calculator.calculateSleepEfficiency(d))
        assertEquals(0, calculator.countWakeEpisodes(onlyLatency))
    }
}
