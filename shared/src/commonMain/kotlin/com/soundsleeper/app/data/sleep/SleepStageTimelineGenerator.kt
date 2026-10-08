package com.soundsleeper.app.data.sleep

import com.soundsleeper.app.domain.model.SleepAnalysis
import com.soundsleeper.app.domain.model.SleepStage
import com.soundsleeper.app.enum_.PredictionStageType
import com.soundsleeper.app.enum_.SleepStageType
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * 30초 단위의 개별 수면 단계 예측들을 리포트에 보여줄 "구간(segment)" 타임라인으로 합치는 클래스.
 * 리포트 화면의 수면 그래프가 30초짜리 점 수백 개가 아니라 Wake/Light/Deep/REM 구간 몇 개로
 * 보이는 것은 이 클래스가 연속된 같은 단계를 하나로 묶어주기 때문이다.
 */
class SleepStageTimelineGenerator {
    /**
     * 시간순 예측 리스트를 받아 연속된 동일 단계를 하나의 [SleepStage] 구간으로 병합한다.
     * N1/N2는 LIGHT로, N3는 DEEP으로 합쳐지며(모델이 애초에 4클래스로 분류하므로 N1/N2 구분은
     * 모델 출력에 없다), 각 윈도우의 실제 길이(또는 다음 샘플까지의 간격)를 구간 길이에 반영한다.
     */
    fun generate(
        analysisList: List<SleepAnalysis>,
        sessionId: String
    ): List<SleepStage> = buildList {
        if (analysisList.isEmpty()) return@buildList

        var currentType: SleepStageType? = null
        var currentStart: LocalDateTime? = null
        var currentDurationMs = 0L

        val defaultWindowMs = 30_000L
        val maxAllowedGapMs = 5 * 60 * 1000L

        // 지금까지 모은 현재 구간(currentType/currentStart/currentDurationMs)을 하나의 SleepStage로 확정해 추가한다.
        fun flush() {
            val type = currentType ?: return
            val start = currentStart ?: return
            add(
                SleepStage(
                    sessionId = sessionId, // 👈 생성된 sessionId를 그대로 사용
                    type = type,
                    startTime = start,
                    duration = currentDurationMs.milliseconds
                )
            )
        }

        analysisList.forEachIndexed { index, analysis ->
            val nextType = when (analysis.predictionStageType) {
                PredictionStageType.AWAKE -> SleepStageType.AWAKE
                PredictionStageType.N1, PredictionStageType.N2 -> SleepStageType.LIGHT
                PredictionStageType.N3 -> SleepStageType.DEEP
                PredictionStageType.REM -> SleepStageType.REM
            }

            val windowDuration = when {
                analysis.windowDurationMs > 0L -> analysis.windowDurationMs

                index < analysisList.lastIndex -> {
                    val currentMs = analysis.timestamp
                    val nextMs = analysisList[index + 1].timestamp
                    val gap = nextMs - currentMs

                    when {
                        gap in 1L..maxAllowedGapMs -> gap
                        else -> defaultWindowMs
                    }
                }
                else -> defaultWindowMs
            }

            when (currentType) {
                null -> {
                    currentType = nextType
                    currentStart = Instant.fromEpochMilliseconds(analysis.timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
                    currentDurationMs = windowDuration
                }
                nextType -> {
                    currentDurationMs += windowDuration
                }
                else -> {
                    flush()
                    currentType = nextType
                    currentStart = Instant.fromEpochMilliseconds(analysis.timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
                    currentDurationMs = windowDuration
                }
            }
        }
        flush()
    }
}