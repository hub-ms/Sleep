package com.soundsleeper.app.ui.report

import com.soundsleeper.app.domain.model.EnvironmentFeature
import com.soundsleeper.app.domain.model.SleepStage
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.roundToInt

object ReportContract {
    data class State(
        val date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
        val isCalendarExpanded: Boolean = false,
        val isPreview: Boolean = true,
        /**
         * 이 사용자가 수면 측정을 한 번이라도 한 적이 있는지.
         * isPreview 는 "선택한 날짜 주변 창에 세션이 없음"이고 sessionDates 는 프리뷰일 때
         * 데모 날짜로 채워지므로, 둘 다 이 판별에는 쓸 수 없어 따로 둔다.
         */
        val hasAnySession: Boolean = false,
        /**
         * 방금 끝난 측정의 세션을 기다리는 중.
         *
         * 저장은 측정 종료 후 백그라운드에서 분석까지 끝내고 커밋되므로 리포트 화면이 먼저
         * 열릴 수 있다. 그 사이에 "세션 없음"으로 단정해 데모 데이터를 띄워버리면, 잠시 뒤
         * 저장이 끝나도 다시 조회하는 곳이 없어 측정 결과가 영영 안 보인다.
         */
        val isAwaitingSession: Boolean = false,
        /** 열라고 지시받은 세션을 끝내 찾지 못함(측정 시간 부족, 분석 실패 등). */
        val sessionUnavailable: Boolean = false,
        val sessionDates: Set<LocalDate> = emptySet(),
        val reportData: ReportData? = null,
        val weeklyChartData: ReportData? = null,
        val prevDayReportData: ReportData? = null,
        val prevWeeklyChartData: ReportData? = null,
        val showDeleteDialog: Boolean = false,
        val pendingDeleteSessionId: String? = null,
        /**
         * 최근 N일 평균. "나의 최근 수면과 비교" 영역에서 오늘과 견주는 기준이다.
         * 타 사용자 집계가 아니라 본인 과거 기록이라 서버가 필요 없다.
         */
        val recentAverage: ReportData? = null,
        /** 위 평균에 실제로 들어간 측정 일수. 표본이 너무 적으면 비교를 보여주지 않는다. */
        val recentSampleCount: Int = 0,
        val sleepAdvice: SleepAdviceUiState = SleepAdviceUiState.Idle,
    )

    /**
     * AI 수면 개선 조언의 표시 상태.
     *
     * Fallback 은 실패가 아니라 "규칙 엔진 문구를 쓰는 중"이라는 뜻이다. 판정 자체는 이미
     * 기기에서 끝나 있으므로 서버를 못 불러도 보여줄 내용은 있다. 조언 영역이 통째로
     * 사라지거나 무한 로딩에 머무는 상태는 만들지 않는다.
     */
    sealed interface SleepAdviceUiState {
        object Idle : SleepAdviceUiState
        object Loading : SleepAdviceUiState
        data class Loaded(val text: String) : SleepAdviceUiState
        data class Fallback(val text: String) : SleepAdviceUiState
    }

    data class ReportData(
        val sessionId: String,
        val environmentHistory: List<EnvironmentFeature.Snapshot>,

        val bedTime: LocalDateTime,
        val wakeTime: LocalDateTime,
        val sleepLatencyMinutes: Double,
        val sleepScore: Int,

        val awakeMinutes: Double,
        val lightMinutes: Double,
        val deepMinutes: Double,
        val remMinutes: Double,
        val sleepMinutes: Double,
        val targetMinutes: Double,
        val stageTimeline: List<SleepStage>,
        val wakeCount: Int,

        val avgNoise: Float,
        val isNoiseDanger: Boolean,

        val dailyBedTimes: Map<LocalDate, LocalDateTime>,
        val dailyWakeTimes: Map<LocalDate, LocalDateTime>,
        val dailySleepMinutes: Map<LocalDate, Double>,
        val dailyScores: Map<LocalDate, Int>,
        val dailySleepLatencyMinutes: Map<LocalDate, Double>,
        val dailyWakeCounts: Map<LocalDate, Int>,

        val totalWakeCount: Int,

        val dailyAvgNoises: Map<LocalDate, Float>,
    ) {
        private val baseHour = 18
        /** 기간 내 일별 수면 시간(분)의 평균. 기록이 없으면 null. */
        val averageSleepMinutes: Int? get() {
            if (dailySleepMinutes.isEmpty()) return null
            return dailySleepMinutes.values.average().roundToInt()
        }
        /** 기간 내 일별 수면 지연(잠드는 데 걸린 시간, 분)의 평균. 기록이 없으면 null. */
        val averageLatencyMinutes: Double? get() {
            if (dailySleepLatencyMinutes.isEmpty()) return null
            return dailySleepLatencyMinutes.values.average()
        }

        /** 기간 내 일별 수면 점수의 평균. 기록이 없으면 null. */
        val averageScore: Int? get() {
            if (dailyScores.isEmpty()) return null
            return dailyScores.values.average().toInt()
        }

        /**
         * 기간 내 "일별 평균 소음"의 평균(dB).
         * 스칼라 avgNoise 는 세션 단위 평균이라 기록이 없는 날의 가중치가 달라진다.
         * 나머지 평균들과 같이 daily* 맵을 기준으로 계산해 단위를 맞춘다.
         */
        val averageNoise: Float? get() {
            if (dailyAvgNoises.isEmpty()) return null
            return dailyAvgNoises.values.average().toFloat()
        }
    }

    sealed class Intent {
        data class SelectDate(val date: LocalDate) : Intent()
        data class PrevClicked(/*val date: LocalDate, */val unit: DateTimeUnit.DateBased) : Intent()
        data class NextClicked(/*val date: LocalDate, */val unit: DateTimeUnit.DateBased) : Intent()
        data class DeleteReport(val sessionId: String) : Intent()
        data class ConfirmDelete(val sessionId: String) : Intent()
        object DismissDeleteDialog : Intent()
        data class ToggleCalendar(val isExpanded: Boolean) : Intent()
    }
}
