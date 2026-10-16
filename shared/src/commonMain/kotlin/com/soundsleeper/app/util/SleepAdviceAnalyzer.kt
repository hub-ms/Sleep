package com.soundsleeper.app.util

import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.advice_frequent_awakening
import com.soundsleeper.app.resources.advice_good_night
import com.soundsleeper.app.resources.advice_insufficient_data
import com.soundsleeper.app.resources.advice_long_sleep_latency
import com.soundsleeper.app.resources.advice_low_deep_sleep
import com.soundsleeper.app.resources.advice_low_sleep_efficiency
import com.soundsleeper.app.resources.advice_noisy_environment
import com.soundsleeper.app.resources.advice_short_total_sleep
import com.soundsleeper.app.ui.report.ReportContract
import org.jetbrains.compose.resources.getString
object SleepAdviceAnalyzer {
    enum class FindingCode {
        LOW_SLEEP_EFFICIENCY,
        LONG_SLEEP_LATENCY,
        FREQUENT_AWAKENING,
        NOISY_ENVIRONMENT,
        SHORT_TOTAL_SLEEP,
        LOW_DEEP_SLEEP,
        GOOD_NIGHT,
        INSUFFICIENT_DATA,
    }

    enum class Severity { INFO, WARNING, CRITICAL }

    data class Finding(
        val code: FindingCode,
        val severity: Severity,
        /** 실제 측정값. LLM 프롬프트에 그대로 넘기고, 오프라인 문구에도 쓴다. */
        val measured: Double,
        /** 비교 기준치. */
        val threshold: Double,
        /** 네트워크가 없거나 호출이 실패했을 때 그대로 보여줄 문구(한국어 고정). */
        val fallbackText: String,
    ) {
        /**
         * [fallbackText] 는 테스트와 서버 전송 로직의 안정성을 위해 한국어로 고정해 둔다.
         * 실제 화면에 보여줄 번역된 문구는 code 와 measured 값으로부터 여기서 다시 만든다.
         */
        suspend fun localizedFallbackText(): String = when (code) {
            FindingCode.LOW_SLEEP_EFFICIENCY ->
                getString(Res.string.advice_low_sleep_efficiency, measured.toInt())
            FindingCode.LONG_SLEEP_LATENCY ->
                getString(Res.string.advice_long_sleep_latency, measured.toInt())
            FindingCode.FREQUENT_AWAKENING ->
                getString(Res.string.advice_frequent_awakening, measured.toInt())
            FindingCode.NOISY_ENVIRONMENT ->
                getString(Res.string.advice_noisy_environment, measured.toInt())
            FindingCode.SHORT_TOTAL_SLEEP ->
                getString(Res.string.advice_short_total_sleep, (measured / 60).toInt(), (measured % 60).toInt())
            FindingCode.LOW_DEEP_SLEEP ->
                getString(Res.string.advice_low_deep_sleep, measured.toInt())
            FindingCode.INSUFFICIENT_DATA ->
                getString(Res.string.advice_insufficient_data)
            FindingCode.GOOD_NIGHT ->
                getString(Res.string.advice_good_night)
        }
    }

    // ── 기준치 ────────────────────────────────────────────────
    private const val MIN_SLEEP_EFFICIENCY_PERCENT = 85.0
    private const val MAX_SLEEP_LATENCY_MINUTES = 30.0
    private const val MAX_WAKE_COUNT = 3.0
    private const val MAX_NIGHT_NOISE_DB = 45.0
    private const val MIN_TOTAL_SLEEP_MINUTES = 7 * 60.0
    private const val MIN_DEEP_SLEEP_RATIO = 0.13

    fun analyze(data: ReportContract.ReportData): List<Finding> {
        val findings = mutableListOf<Finding>()

        val totalMinutes = data.awakeMinutes + data.lightMinutes + data.deepMinutes + data.remMinutes
        val efficiency = if (totalMinutes > 0) data.sleepMinutes / totalMinutes * 100 else 0.0

        if (totalMinutes > 0 && efficiency < MIN_SLEEP_EFFICIENCY_PERCENT) {
            findings += Finding(
                code = FindingCode.LOW_SLEEP_EFFICIENCY,
                severity = if (efficiency < 75) Severity.CRITICAL else Severity.WARNING,
                measured = efficiency,
                threshold = MIN_SLEEP_EFFICIENCY_PERCENT,
                fallbackText = "침대에 누워 있던 시간 중 실제로 잠든 비율이 ${efficiency.toInt()}%예요. " +
                    "85% 이상이 권장 범위입니다. 잠이 오지 않을 때는 잠시 침대에서 벗어났다가 " +
                    "졸릴 때 다시 눕는 편이 도움이 됩니다."
            )
        }

        if (data.sleepLatencyMinutes > MAX_SLEEP_LATENCY_MINUTES) {
            findings += Finding(
                code = FindingCode.LONG_SLEEP_LATENCY,
                severity = Severity.WARNING,
                measured = data.sleepLatencyMinutes,
                threshold = MAX_SLEEP_LATENCY_MINUTES,
                fallbackText = "잠드는 데 ${data.sleepLatencyMinutes.toInt()}분이 걸렸어요. " +
                    "30분 이상 걸리는 날이 반복되면 잠들기 1시간 전 화면 밝기를 낮추고 " +
                    "카페인을 피하는 것이 좋습니다."
            )
        }

        if (data.wakeCount > MAX_WAKE_COUNT) {
            findings += Finding(
                code = FindingCode.FREQUENT_AWAKENING,
                severity = Severity.WARNING,
                measured = data.wakeCount.toDouble(),
                threshold = MAX_WAKE_COUNT,
                fallbackText = "밤중에 ${data.wakeCount}번 깼어요. 자주 깬다면 " +
                    "잠들기 전 수분 섭취와 방 온도를 함께 살펴보세요."
            )
        }

        if (data.avgNoise > MAX_NIGHT_NOISE_DB) {
            findings += Finding(
                code = FindingCode.NOISY_ENVIRONMENT,
                severity = Severity.WARNING,
                measured = data.avgNoise.toDouble(),
                threshold = MAX_NIGHT_NOISE_DB,
                fallbackText = "밤사이 평균 소음이 ${data.avgNoise.toInt()}dB로 권장치(45dB)보다 높았어요. " +
                    "창문을 닫거나 백색소음으로 소리를 덮으면 깨는 횟수를 줄일 수 있습니다."
            )
        }

        if (data.sleepMinutes in 1.0..<MIN_TOTAL_SLEEP_MINUTES) {
            findings += Finding(
                code = FindingCode.SHORT_TOTAL_SLEEP,
                severity = if (data.sleepMinutes < 5 * 60) Severity.CRITICAL else Severity.WARNING,
                measured = data.sleepMinutes,
                threshold = MIN_TOTAL_SLEEP_MINUTES,
                fallbackText = "총 수면 시간이 ${(data.sleepMinutes / 60).toInt()}시간 " +
                    "${(data.sleepMinutes % 60).toInt()}분이에요. 성인 권장 수면은 7시간 이상입니다."
            )
        }

        val deepRatio = if (data.sleepMinutes > 0) data.deepMinutes / data.sleepMinutes else 0.0
        if (data.sleepMinutes > 0 && deepRatio < MIN_DEEP_SLEEP_RATIO) {
            findings += Finding(
                code = FindingCode.LOW_DEEP_SLEEP,
                severity = Severity.INFO,
                measured = deepRatio * 100,
                threshold = MIN_DEEP_SLEEP_RATIO * 100,
                fallbackText = "깊은 잠 비율이 ${(deepRatio * 100).toInt()}%로 낮은 편이에요. " +
                    "규칙적인 취침 시각과 낮 시간의 가벼운 운동이 깊은 잠을 늘리는 데 도움이 됩니다."
            )
        }

        // 측정값이 없으면 "지적사항 없음"이 아니라 "판정 불가"다.
        if (findings.isEmpty() && data.sleepMinutes <= 0.0 && totalMinutes <= 0.0) {
            return listOf(
                Finding(
                    code = FindingCode.INSUFFICIENT_DATA,
                    severity = Severity.WARNING,
                    measured = 0.0,
                    threshold = 0.0,
                    fallbackText = "이번 밤은 수면 단계를 분석할 만큼 데이터가 모이지 않았어요. " +
                        "측정을 시작한 뒤 휴대폰을 침대 위에 그대로 두면 더 정확하게 기록됩니다.",
                )
            )
        }

        if (findings.isEmpty()) {
            findings += Finding(
                code = FindingCode.GOOD_NIGHT,
                severity = Severity.INFO,
                measured = efficiency,
                threshold = MIN_SLEEP_EFFICIENCY_PERCENT,
                fallbackText = "특별히 고칠 점이 보이지 않는 밤이었어요. " +
                    "지금의 취침 습관을 그대로 이어가 보세요."
            )
        }

        // 심각한 것부터 보여준다. 화면에 다 담지 못할 때 잘려 나가는 쪽이 덜 중요한 항목이어야 한다.
        return findings.sortedByDescending { it.severity.ordinal }
    }
}