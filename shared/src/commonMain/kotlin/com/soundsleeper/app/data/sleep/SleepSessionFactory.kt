package com.soundsleeper.app.data.sleep

import com.soundsleeper.app.domain.model.EnvironmentFeature
import com.soundsleeper.app.domain.model.SleepAnalysis
import com.soundsleeper.app.domain.model.SleepSession
import com.soundsleeper.app.domain.model.SleepStage
import com.soundsleeper.app.domain.model.Stats
import com.soundsleeper.app.enum_.SleepStageType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** 분석이 끝난 여러 조각(타임라인, 분포, 환경 통계)을 하나의 저장 가능한 [SleepSession]으로 조립하는 팩토리. */
class SleepSessionFactory {
    /**
     * 측정 종료 시 [SleepSessionRepositoryImpl.analyzeSleepSession]이 호출한다.
     * wakeTime은 마지막 예측의 타임스탬프를 기상 시각으로 간주해 채운다.
     *
     * 🐛 버그 수정(리포트가 전부 0으로 보이던 문제): [duration], [sleepEfficiency], [wakeCount]는
     * 예전에 이 팩토리가 각각 "전부 0인 Duration", 0, 0으로 **하드코딩**하고 있었습니다. 그래서
     * 측정이 정상적으로 끝나 DB에 저장돼도 리포트의 점수·단계별 시간·단계 분포 그래프·기상
     * 횟수가 모두 0/빈 값으로 표시됐습니다. 이제 호출부가
     * [SleepStatisticsCalculator.calculateDuration] / [SleepStatisticsCalculator.calculateSleepEfficiency] /
     * [SleepStatisticsCalculator.countWakeEpisodes]로 타임라인에서 계산한 실제 값을 넘깁니다.
     */
    fun create(
        sessionId: String,
        sessionDate: LocalDate,
        analysisList: List<SleepAnalysis>,
        stageTimeline: List<SleepStage>,
        stagesDistribution: Map<SleepStageType, Float>,
        duration: SleepSession.Duration,
        sleepEfficiency: Int,
        wakeCount: Int,
        environmentFeatures: List<EnvironmentFeature>,
        sessionNoiseStats: Stats,
        sessionNoiseDanger: Boolean,
        now: Long
    ): SleepSession {
        return SleepSession(
            sessionId = sessionId,
            date = sessionDate,
            wakeTime = Instant.fromEpochMilliseconds(analysisList.last().timestamp).toLocalDateTime(TimeZone.currentSystemDefault()),
            stageTimeline = stageTimeline,
            stagesDistribution = stagesDistribution,
            sleepEfficiency = sleepEfficiency,
            environment = SleepSession.Environment(
                history = environmentFeatures.map { it.snapshot },
                stats = EnvironmentFeature.Statistics(noise = sessionNoiseStats),
                flags = EnvironmentFeature.Flag(
                    isNoiseDanger = sessionNoiseDanger
                )
            ),
            csvData = SleepSession.CsvData(
                sensorCsv = "",
                environmentCsv = ""
            ),
            duration = duration,
            wakeCount = wakeCount,
            timestamp = SleepSession.Timestamp(createdAt = now, updatedAt = now),
        )
    }
}