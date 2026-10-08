package com.soundsleeper.app.domain.advice

import com.soundsleeper.app.ui.report.ReportContract
import com.soundsleeper.app.util.SleepAdviceAnalyzer
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [com.soundsleeper.app.util.SleepAdviceAnalyzer] 규칙 엔진 테스트.
 *
 * 각 FindingCode 마다 임계값 바로 위/아래를 함께 검증한다. 임계값 한쪽만 테스트하면
 * 비교 연산자의 방향(> vs >=)이 뒤집혀도 통과해 버린다.
 */
class SleepAdviceAnalyzerTest {

    /** 모든 지표가 "건강한" 밤. 각 테스트에서 필요한 필드만 바꿔 쓴다. */
    private fun healthyNight(
        sleepLatencyMinutes: Double = 10.0,
        awakeMinutes: Double = 20.0,
        lightMinutes: Double = 240.0,
        deepMinutes: Double = 110.0,
        remMinutes: Double = 110.0,
        sleepMinutes: Double = 460.0,
        wakeCount: Int = 1,
        avgNoise: Float = 32f,
    ) = ReportContract.ReportData(
        sessionId = "test",
        environmentHistory = emptyList(),
        bedTime = LocalDateTime(2026, 9, 30, 23, 0),
        wakeTime = LocalDateTime(2026, 10, 1, 7, 0),
        sleepLatencyMinutes = sleepLatencyMinutes,
        sleepScore = 80,
        awakeMinutes = awakeMinutes,
        lightMinutes = lightMinutes,
        deepMinutes = deepMinutes,
        remMinutes = remMinutes,
        sleepMinutes = sleepMinutes,
        targetMinutes = 480.0,
        stageTimeline = emptyList(),
        wakeCount = wakeCount,
        avgNoise = avgNoise,
        isNoiseDanger = false,
        dailyBedTimes = emptyMap(),
        dailyWakeTimes = emptyMap(),
        dailySleepMinutes = emptyMap(),
        dailyScores = emptyMap(),
        dailySleepLatencyMinutes = emptyMap(),
        dailyWakeCounts = emptyMap(),
        totalWakeCount = wakeCount,
        dailyAvgNoises = emptyMap(),
    )

    private fun codes(data: ReportContract.ReportData) =
        SleepAdviceAnalyzer.analyze(data).map { it.code }

    // ── 회귀 방지: 지표가 비어 있을 때 "좋은 밤"이라고 말하면 안 된다 ──────────

    /**
     * 측정 지표가 전부 0인 리포트는 "분석할 데이터가 없는" 상태다. 그런데 규칙 엔진의
     * 모든 검사가 `totalMinutes > 0` / `sleepMinutes > 0` / `> 임계값` 조건에 걸려
     * 건너뛰어지면서 findings 가 비고, 마지막 폴백인 GOOD_NIGHT 가 선택된다.
     *
     * 즉 사용자는 아무것도 측정되지 않은 밤에 "특별히 고칠 점이 보이지 않는 밤이었어요"
     * 라는 안내를 받는다. 데이터 없음을 좋은 결과로 오보하는 것이므로, 지표 복구
     * (SleepSessionRepositoryImpl 의 0값 하드코딩 제거) 이후에도 이 조합에서
     * GOOD_NIGHT 가 나오지 않도록 고정한다.
     */
    @Test
    fun emptyMetricsMustNotReportGoodNight() {
        val empty = healthyNight(
            sleepLatencyMinutes = 0.0,
            awakeMinutes = 0.0,
            lightMinutes = 0.0,
            deepMinutes = 0.0,
            remMinutes = 0.0,
            sleepMinutes = 0.0,
            wakeCount = 0,
            avgNoise = 0f,
        )
        assertFalse(
            SleepAdviceAnalyzer.FindingCode.GOOD_NIGHT in codes(empty),
            "지표가 전부 0인데 GOOD_NIGHT 를 반환했다. 측정 실패를 좋은 수면으로 오보하는 상태다.",
        )
        assertEquals(
            listOf(SleepAdviceAnalyzer.FindingCode.INSUFFICIENT_DATA),
            codes(empty),
            "측정값이 없을 때는 판정 불가를 명시해야 한다",
        )
    }

    // ── 정상 경로 ────────────────────────────────────────────────

    @Test
    fun healthyNightReportsGoodNight() {
        assertEquals(listOf(SleepAdviceAnalyzer.FindingCode.GOOD_NIGHT), codes(healthyNight()))
    }

    // ── 임계값별 경계 ─────────────────────────────────────────────

    @Test
    fun sleepLatencyThresholdIsExclusiveAtThirtyMinutes() {
        assertFalse(
            SleepAdviceAnalyzer.FindingCode.LONG_SLEEP_LATENCY in codes(healthyNight(sleepLatencyMinutes = 30.0)),
            "30분은 기준치와 같으므로 경고가 아니어야 한다",
        )
        assertTrue(
            SleepAdviceAnalyzer.FindingCode.LONG_SLEEP_LATENCY in codes(healthyNight(sleepLatencyMinutes = 30.1)),
        )
    }

    @Test
    fun wakeCountThresholdIsExclusiveAtThree() {
        assertFalse(SleepAdviceAnalyzer.FindingCode.FREQUENT_AWAKENING in codes(healthyNight(wakeCount = 3)))
        assertTrue(SleepAdviceAnalyzer.FindingCode.FREQUENT_AWAKENING in codes(healthyNight(wakeCount = 4)))
    }

    @Test
    fun noiseThresholdIsExclusiveAtFortyFiveDb() {
        assertFalse(SleepAdviceAnalyzer.FindingCode.NOISY_ENVIRONMENT in codes(healthyNight(avgNoise = 45f)))
        assertTrue(SleepAdviceAnalyzer.FindingCode.NOISY_ENVIRONMENT in codes(healthyNight(avgNoise = 45.5f)))
    }

    @Test
    fun shortTotalSleepFiresBelowSevenHours() {
        // 419분 = 6시간 59분 → 경고, 420분(7시간) → 경고 없음
        assertTrue(SleepAdviceAnalyzer.FindingCode.SHORT_TOTAL_SLEEP in codes(healthyNight(sleepMinutes = 419.0)))
        assertFalse(SleepAdviceAnalyzer.FindingCode.SHORT_TOTAL_SLEEP in codes(healthyNight(sleepMinutes = 420.0)))
    }

    @Test
    fun shortTotalSleepBecomesCriticalBelowFiveHours() {
        val findings = SleepAdviceAnalyzer.analyze(healthyNight(sleepMinutes = 240.0))
        val short = findings.single { it.code == SleepAdviceAnalyzer.FindingCode.SHORT_TOTAL_SLEEP }
        assertEquals(SleepAdviceAnalyzer.Severity.CRITICAL, short.severity)
    }

    @Test
    fun lowSleepEfficiencyFiresAndEscalates() {
        // 누워 있던 480분 중 350분만 수면 → 약 72.9% → CRITICAL(75% 미만)
        val findings = SleepAdviceAnalyzer.analyze(
            healthyNight(awakeMinutes = 130.0, lightMinutes = 180.0, deepMinutes = 90.0,
                remMinutes = 80.0, sleepMinutes = 350.0),
        )
        val eff = findings.single { it.code == SleepAdviceAnalyzer.FindingCode.LOW_SLEEP_EFFICIENCY }
        assertEquals(SleepAdviceAnalyzer.Severity.CRITICAL, eff.severity)
        assertTrue(eff.measured < 75.0, "measured=${eff.measured}")
    }

    @Test
    fun lowDeepSleepFiresBelowThirteenPercent() {
        // 깊은 잠 40분 / 수면 460분 ≈ 8.7%
        assertTrue(
            SleepAdviceAnalyzer.FindingCode.LOW_DEEP_SLEEP in
                codes(healthyNight(deepMinutes = 40.0, lightMinutes = 310.0)),
        )
    }

    // ── 정렬 계약 ────────────────────────────────────────────────

    /** 화면이 잘릴 때 남아야 하는 쪽이 더 심각한 항목이어야 한다. */
    @Test
    fun findingsAreSortedBySeverityDescending() {
        val findings = SleepAdviceAnalyzer.analyze(
            healthyNight(
                sleepLatencyMinutes = 50.0,   // WARNING
                awakeMinutes = 130.0,          // → 효율 저하 CRITICAL
                lightMinutes = 180.0,
                deepMinutes = 20.0,            // → 깊은잠 INFO
                remMinutes = 80.0,
                sleepMinutes = 280.0,          // → 총수면 CRITICAL
                wakeCount = 6,                 // WARNING
                avgNoise = 60f,                // WARNING
            ),
        )
        assertTrue(findings.size >= 4, "여러 항목이 동시에 잡혀야 정렬을 검증할 수 있다")
        val ordinals = findings.map { it.severity.ordinal }
        assertEquals(ordinals.sortedDescending(), ordinals, "심각도 내림차순이 아니다: ${findings.map { it.code to it.severity }}")
    }

    /** GOOD_NIGHT 는 다른 지적사항과 함께 나오면 안 된다(상호배타). */
    @Test
    fun goodNightIsExclusive() {
        val findings = SleepAdviceAnalyzer.analyze(healthyNight(wakeCount = 9))
        assertTrue(findings.isNotEmpty())
        assertFalse(
            SleepAdviceAnalyzer.FindingCode.GOOD_NIGHT in findings.map { it.code },
            "지적사항이 있는데 GOOD_NIGHT 가 함께 반환됐다",
        )
    }
}
