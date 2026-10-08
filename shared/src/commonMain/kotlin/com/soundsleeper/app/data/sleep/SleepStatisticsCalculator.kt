package com.soundsleeper.app.data.sleep

import com.soundsleeper.app.domain.model.SleepSession
import com.soundsleeper.app.domain.model.SleepStage
import com.soundsleeper.app.enum_.SleepStageType
import kotlin.math.roundToInt

/** 세션의 단계별(Wake/Light/Deep/REM) 체류 시간을 리포트에 쓸 비율로 변환하는 계산기. */
class SleepStatisticsCalculator {
    /** 각 단계의 분(minute)을 전체 합으로 나눠 0~1 비율 맵으로 변환한다. 합이 0이면 빈 맵을 반환한다. */
    fun calculateStagesDistribution(
        duration: SleepSession.Duration
    ): Map<SleepStageType, Float> {
        val total = duration.awakeMinutes + duration.lightMinutes + duration.deepMinutes + duration.remMinutes
        if (total <= 0) return emptyMap()
        return mapOf(
            SleepStageType.AWAKE to (duration.awakeMinutes / total).toFloat(),
            SleepStageType.LIGHT to (duration.lightMinutes / total).toFloat(),
            SleepStageType.DEEP to (duration.deepMinutes / total).toFloat(),
            SleepStageType.REM to (duration.remMinutes / total).toFloat(),
        )
    }

    /**
     * 수면 단계 타임라인에서 실제 [SleepSession.Duration]을 계산한다.
     *
     * 🐛 버그 수정(리포트가 전부 0으로 보이던 문제): 예전에는 [SleepSessionFactory]가
     * awake/light/deep/rem/latency를 모두 0으로 하드코딩했고,
     * [SleepSessionRepositoryImpl.analyzeSleepSession]은 그 0값 Duration을 그대로
     * [calculateStagesDistribution]에 넘겼습니다. 그러면 `total <= 0`이라 분포까지 **빈 맵**이
     * 되어, 측정이 정상적으로 끝나 DB에 저장돼도 리포트의 점수·단계별 시간·그래프가 전부
     * 0/빈 값으로 표시됐습니다. 구간별 실제 길이는 이미 [SleepStageTimelineGenerator]가
     * [SleepStage.duration]에 채워 두므로, 여기서 그대로 합산하면 됩니다.
     *
     * **선두 AWAKE는 입면 잠복기로 분리**합니다 — 첫 비-AWAKE 구간 이전의 AWAKE 합만
     * [SleepSession.Duration.sleepLatencyMinutes]로 넣고, 나머지 AWAKE만
     * [SleepSession.Duration.awakeMinutes]에 넣습니다. 둘에 같은 시간을 이중으로 넣으면
     * `SleepSessionUtil.toReportData()`가 `awakeMinutes + sleepLatencyMinutes + sleepMinutes`로
     * 계산하는 침대에 있던 시간(timeInBed)과 기상 시각이 부풀려집니다.
     */
    fun calculateDuration(
        stageTimeline: List<SleepStage>,
        targetMinutes: Double = DEFAULT_TARGET_MINUTES
    ): SleepSession.Duration {
        var latency = 0.0
        var awake = 0.0
        var light = 0.0
        var deep = 0.0
        var rem = 0.0
        var sleepStarted = false

        for (stage in stageTimeline) {
            val minutes = stage.duration.inWholeMilliseconds / 60_000.0
            when (stage.type) {
                SleepStageType.AWAKE -> if (sleepStarted) awake += minutes else latency += minutes
                SleepStageType.LIGHT -> { light += minutes; sleepStarted = true }
                SleepStageType.DEEP -> { deep += minutes; sleepStarted = true }
                SleepStageType.REM -> { rem += minutes; sleepStarted = true }
            }
        }

        return SleepSession.Duration(
            awakeMinutes = awake,
            lightMinutes = light,
            deepMinutes = deep,
            remMinutes = rem,
            targetMinutes = targetMinutes,
            sleepLatencyMinutes = latency,
        )
    }

    /**
     * 수면 효율(리포트의 "수면 점수") = 실제로 잠든 시간 / 침대에 있던 시간 × 100.
     * 분모가 0이면(타임라인이 비었거나 전부 길이 0) 0을 반환한다.
     */
    fun calculateSleepEfficiency(duration: SleepSession.Duration): Int {
        val asleep = duration.lightMinutes + duration.deepMinutes + duration.remMinutes
        val timeInBed = asleep + duration.awakeMinutes + duration.sleepLatencyMinutes
        if (timeInBed <= 0) return 0
        return (asleep / timeInBed * 100).roundToInt().coerceIn(0, 100)
    }

    /**
     * 중간 각성 횟수 — **입면 이후**의 AWAKE 구간 개수. 선두 AWAKE(입면 잠복기)는
     * 아직 잠들지 않은 상태이므로 각성으로 세지 않는다([calculateDuration]과 같은 기준).
     */
    fun countWakeEpisodes(stageTimeline: List<SleepStage>): Int {
        var sleepStarted = false
        var count = 0
        for (stage in stageTimeline) {
            if (stage.type == SleepStageType.AWAKE) {
                if (sleepStarted) count++
            } else {
                sleepStarted = true
            }
        }
        return count
    }

    companion object {
        /** 목표 수면시간 기본값(분). 사용자 설정 연동은 아직 없어 8시간 고정이다. */
        const val DEFAULT_TARGET_MINUTES = 480.0
    }
}
