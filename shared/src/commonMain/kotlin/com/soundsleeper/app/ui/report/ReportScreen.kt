package com.soundsleeper.app.ui.report

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundsleeper.app.enum_.EnvironmentCategory
import com.soundsleeper.app.enum_.ReportRangeTab
import com.soundsleeper.app.enum_.SleepStageType
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.report_ai_advice_disclaimer
import com.soundsleeper.app.resources.report_ai_advice_loading
import com.soundsleeper.app.resources.report_ai_advice_title
import com.soundsleeper.app.resources.report_calendar_collapse
import com.soundsleeper.app.resources.report_calendar_expand
import com.soundsleeper.app.resources.report_calendar_next_month
import com.soundsleeper.app.resources.report_calendar_next_week
import com.soundsleeper.app.resources.report_calendar_prev_month
import com.soundsleeper.app.resources.report_calendar_prev_week
import com.soundsleeper.app.resources.report_delete_confirm_message
import com.soundsleeper.app.resources.report_delete_report
import com.soundsleeper.app.resources.report_demo_description
import com.soundsleeper.app.resources.report_demo_title
import com.soundsleeper.app.resources.report_gated_description
import com.soundsleeper.app.resources.report_metric_avg_noise
import com.soundsleeper.app.resources.report_metric_sleep_duration
import com.soundsleeper.app.resources.report_metric_sleep_latency
import com.soundsleeper.app.resources.report_metric_sleep_score
import com.soundsleeper.app.resources.report_no_record
import com.soundsleeper.app.resources.report_preparing_description
import com.soundsleeper.app.resources.report_preparing_title
import com.soundsleeper.app.resources.report_section_noise
import com.soundsleeper.app.resources.report_section_sleep_stages
import com.soundsleeper.app.resources.report_self_comparison_insufficient
import com.soundsleeper.app.resources.report_self_comparison_title
import com.soundsleeper.app.resources.report_session_unavailable_description
import com.soundsleeper.app.resources.report_session_unavailable_title
import com.soundsleeper.app.resources.report_snoring_analysis_body
import com.soundsleeper.app.resources.report_snoring_analysis_title
import com.soundsleeper.app.resources.report_stage_awake
import com.soundsleeper.app.resources.report_stage_deep
import com.soundsleeper.app.resources.report_stage_light
import com.soundsleeper.app.resources.report_stage_rem
import com.soundsleeper.app.resources.report_unit_hour
import com.soundsleeper.app.resources.report_unit_minute
import com.soundsleeper.app.resources.report_unit_point
import com.soundsleeper.app.resources.report_upgrade_cta
import com.soundsleeper.app.resources.report_wake_cause_brief
import com.soundsleeper.app.resources.report_wake_cause_noise_spike
import com.soundsleeper.app.resources.report_weekday_fri
import com.soundsleeper.app.resources.report_weekday_mon
import com.soundsleeper.app.resources.report_weekday_sat
import com.soundsleeper.app.resources.report_weekday_sun
import com.soundsleeper.app.resources.report_weekday_thu
import com.soundsleeper.app.resources.report_weekday_tue
import com.soundsleeper.app.resources.report_weekday_wed
import com.soundsleeper.app.resources.common_cancel
import com.soundsleeper.app.resources.common_delete
import com.soundsleeper.app.resources.ic_report
import com.soundsleeper.app.ui.component.ChartLegend
import com.soundsleeper.app.ui.component.EnvironmentChart
import com.soundsleeper.app.ui.component.EnvironmentValues
import com.soundsleeper.app.ui.component.SelectableChipGroup
import com.soundsleeper.app.ui.component.toStatus
import com.soundsleeper.app.ui.theme.SleepTheme
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.SleepTheme.secondary
import com.soundsleeper.app.ui.theme.bodyHighlight
import com.soundsleeper.app.ui.theme.bodyText
import com.soundsleeper.app.ui.theme.caption
import com.soundsleeper.app.ui.theme.getColorForScore
import com.soundsleeper.app.ui.theme.getColorForStage
import com.soundsleeper.app.ui.theme.sectionTitle
import com.soundsleeper.app.ui.tracking.TrackingContract
import com.soundsleeper.app.util.ChartUtil
import com.soundsleeper.app.util.ChartUtil.scoreToY
import com.soundsleeper.app.util.ChartUtil.timeToY
import com.soundsleeper.app.util.DateTimeUtil.formatCalendarMonth
import com.soundsleeper.app.util.DateTimeUtil.formatCalendarWeek
import com.soundsleeper.app.util.DateTimeUtil.formatDate
import com.soundsleeper.app.util.DateTimeUtil.formatDateLabel
import com.soundsleeper.app.util.DateTimeUtil.formatSleepDurationFromMillis
import com.soundsleeper.app.util.DateTimeUtil.to24TimeString
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

data class LegendItem(
    val label: String,
    val color: Color,
    val duration: Long,
    val percent: Int
)

/**
 * 일간/주간 카드에 나열되는 지표의 정체성(key)과 화면에 보일 문구(labelRes)를 분리한다.
 * 예전에는 ComparisonItem.label 이 "수면시간" 같은 화면 문구 그 자체였고, 카드는 그 문구를
 * itemMap["수면시간"] 처럼 조회 키로도 재사용했다. 영어 로케일에서는 문구가 "Sleep Duration"이
 * 되어 조회가 통째로 깨진다.
 */
enum class ReportMetric(val labelRes: StringResource) {
    SLEEP_DURATION(Res.string.report_metric_sleep_duration),
    SLEEP_SCORE(Res.string.report_metric_sleep_score),
    SLEEP_LATENCY(Res.string.report_metric_sleep_latency),
    AVG_NOISE(Res.string.report_metric_avg_noise),
}

data class ComparisonItem(
    val metric: ReportMetric,
    val value: Int? = null,
    val valueText: AnnotatedString,
    val isIncrease: Boolean? = null,
)
val BAR_WIDTH = 16.dp
val X_AXIS_PADDING = 16.dp
val Y_AXIS_PADDING = 16.dp
val LABEL_WIDTH = 48.dp
val CHART_TOP_PADDING = 24.dp


val SleepStageType.stageNameRes: StringResource
    get() = when (this) {
        SleepStageType.AWAKE -> Res.string.report_stage_awake
        SleepStageType.LIGHT -> Res.string.report_stage_light
        SleepStageType.REM -> Res.string.report_stage_rem
        SleepStageType.DEEP -> Res.string.report_stage_deep
    }


interface ChartDataEntity {
    val date: LocalDate
    val labelText: String
}

data class ChartWeekDay(
    val isoDayNumber: Int,
    override val date: LocalDate,
    override val labelText: String
) : ChartDataEntity

@Composable
fun rememberSleepTimeStyles(): Triple<SpanStyle, SpanStyle, TextStyle> {
    val sectionStyle = MaterialTheme.typography.sectionTitle.toSpanStyle().copy(
        Color.White,
        fontWeight = FontWeight.Bold
    )
    val bodyStyle = MaterialTheme.typography.bodyText.toSpanStyle().copy(
        Color.White
    )
    val labelStyle = MaterialTheme.typography.caption.copy(
        Color.White,
    )

    return Triple(sectionStyle, bodyStyle, labelStyle)
}

/**
 * [unitLabels] 는 번역된 단위 문구("시간"/"분"/"점" 또는 "h"/"m"/" pts")를 호출부가
 * composable 컨텍스트에서 미리 읽어 넘긴 것이다. 이 함수 자체는 @Composable 이 아니므로
 * stringResource 를 직접 부를 수 없다.
 */
fun String.toAnnotatedString(
    baseSectionStyle: SpanStyle, baseBodyStyle: SpanStyle, unitLabels: List<String>
): AnnotatedString {
    val text = this
    return buildAnnotatedString {
        withStyle(style = baseSectionStyle) { append(text) }
        unitLabels.forEach { label ->
            if (label.isBlank()) return@forEach
            var index = text.indexOf(label)
            while (index != -1) {
                addStyle(style = baseBodyStyle, start = index, end = index + label.length)
                index = text.indexOf(label, index + 1)
            }
        }
    }
}

/**
 * 수면 사이클의 마지막 화면 전체. [ReportViewModel.state]를 구독해 일별/주간 카드, 캘린더,
 * 소음/수면 그래프, AI 조언 등 리포트의 모든 섹션을 조립해 그린다.
 */
@Composable
fun ReportContent(
    trackingState: TrackingContract.State,
    reportState: ReportContract.State,
    isUserPremium: Boolean = false,
    onToggleCalendarExpanded: (Boolean) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onPrevClicked: (DateTimeUnit.DateBased) -> Unit,
    onNextClicked: (DateTimeUnit.DateBased) -> Unit,
    onReportDeleteClicked: (String) -> Unit,
    onConfirmDelete: (String) -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onUpgradeClicked: () -> Unit = {},
    onRequestAdvice: () -> Unit = {},
) {

    // 보고 있는 세션이 바뀔 때만 다시 부른다. 매 리컴포지션마다 부르면 캐시가 있어도
    // 불필요한 작업이 반복된다.
    LaunchedEffect(reportState.reportData?.sessionId, reportState.isPreview) {
        onRequestAdvice()
    }

    val (baseSectionStyle, baseBodyStyle, labelStyle) = rememberSleepTimeStyles()
    val hourUnit = stringResource(Res.string.report_unit_hour)
    val minuteUnit = stringResource(Res.string.report_unit_minute)
    val pointUnit = stringResource(Res.string.report_unit_point)
    val unitLabels = listOf(hourUnit, minuteUnit, pointUnit)

    val weekStartDate = remember(reportState.date) {
        reportState.date.minus(
            (reportState.date.dayOfWeek.isoDayNumber - 1).toLong(),
            DateTimeUnit.DAY
        )
    }
    val xLabels = remember(weekStartDate) {
        buildCalendarLabels(startDate = weekStartDate)
    }
    var rangeItem by remember { mutableStateOf(ReportRangeTab.TODAY) }

    // 소음 정상 범위와 위험(비정상) 범위를 모두 포함하도록 y축 기준 범위를 넉넉히 잡습니다.
    // 기존에는 실측된 최소/최대값만 사용해서 축 범위가 지나치게 좁아졌고,
    // 그 결과 정상/위험 기준선이나 튀는 값이 그래프 밖으로 잘려 보이는 문제가 있었습니다.
    val values = reportState.reportData?.let { data ->
        val nList = data.environmentHistory.map { it.noise }.filter { it > 0 }

        val nObservedMax = nList.maxOrNull() ?: 0f
        val nObservedMin = nList.minOrNull() ?: 0f

        EnvironmentValues(
            noiseAvg = data.avgNoise,
            noiseMax = maxOf(nObservedMax, NOISE_CHART_MAX),
            noiseMin = minOf(nObservedMin, NOISE_CHART_MIN),
            isNoiseDanger = data.isNoiseDanger,
            history = data.environmentHistory
        )
    } ?: EnvironmentValues()


    val targetDate = reportState.date
    val prevTargetDate = targetDate.minus(1, DateTimeUnit.DAY)

    val bedTime = if (reportState.isPreview) reportState.reportData?.dailyBedTimes[targetDate]
        ?: reportState.reportData?.bedTime ?: trackingState.trackingStartTime
    else reportState.reportData?.bedTime ?: trackingState.trackingStartTime
    val prevBedTime = reportState.prevDayReportData?.dailyBedTimes?.get(prevTargetDate)
        ?: reportState.prevDayReportData?.bedTime
        ?: reportState.reportData?.dailyBedTimes?.get(prevTargetDate)
        ?: bedTime

    val wakeTime =
        reportState.reportData?.dailyWakeTimes?.get(targetDate) ?: reportState.reportData?.wakeTime
        ?: trackingState.trackingEndTime
    val prevWakeTime = reportState.prevDayReportData?.dailyWakeTimes?.get(prevTargetDate)
        ?: reportState.prevDayReportData?.wakeTime
        ?: reportState.reportData?.dailyWakeTimes?.get(prevTargetDate)
        ?: wakeTime

    val sleepDurationMillis = remember(reportState.reportData) {
        (reportState.reportData?.sleepMinutes ?: 0.0).toLong() * 60 * 1000
    }
    val prevSleepDurationMillis = remember(prevBedTime, prevWakeTime) {
        (reportState.prevDayReportData?.sleepMinutes ?: 0).toLong() * 60 * 1000
    }

    val rawScore =
        reportState.reportData?.dailyScores?.get(targetDate) ?: reportState.reportData?.sleepScore
        ?: 0
    val prevScore = reportState.prevDayReportData?.dailyScores?.get(prevTargetDate)
        ?: reportState.prevDayReportData?.sleepScore
        ?: reportState.reportData?.dailyScores?.get(prevTargetDate)
        ?: 0

    val latencyMinutes = reportState.reportData?.dailySleepLatencyMinutes?.get(targetDate)
        ?: reportState.reportData?.sleepLatencyMinutes
        ?: 0.0
    val prevLatencyMinutes =
        reportState.prevDayReportData?.dailySleepLatencyMinutes?.get(prevTargetDate)
            ?: reportState.prevDayReportData?.sleepLatencyMinutes
            ?: 0.0
    val latencyMillis = (latencyMinutes * 60000).toLong()
    val prevLatencyMillis = (prevLatencyMinutes * 60000).toLong()


    val bedTimeText = (bedTime.to24TimeString())
    val wakeTimeText = (wakeTime.to24TimeString())
    val durationText = formatSleepDurationFromMillis(sleepDurationMillis, hourUnit, minuteUnit)
        .toAnnotatedString(baseSectionStyle, baseBodyStyle, unitLabels)
    val scoreText = "${rawScore}$pointUnit".toAnnotatedString(baseSectionStyle, baseBodyStyle, unitLabels)
    val latencyText = formatSleepDurationFromMillis(latencyMillis, hourUnit, minuteUnit)
        .toAnnotatedString(baseSectionStyle, baseBodyStyle, unitLabels)
    val avgDurationMillis = remember(reportState.weeklyChartData) {
        (reportState.weeklyChartData?.averageSleepMinutes ?: 0).toLong() * 60 * 1000
    }
    val prevAvgDurationMillis = remember(reportState.weeklyChartData) {
        (reportState.prevWeeklyChartData?.averageSleepMinutes ?: 0).toLong() * 60 * 1000
    }
    val avgScore = reportState.weeklyChartData?.averageScore
        ?: reportState.weeklyChartData?.sleepScore
        ?: reportState.reportData?.sleepScore
        ?: 0
    val prevAvgScore = reportState.prevWeeklyChartData?.averageScore
        ?: reportState.prevWeeklyChartData?.sleepScore
        ?: reportState.prevDayReportData?.sleepScore
        ?: 0
    val avgLatencyMinutes = reportState.weeklyChartData?.averageLatencyMinutes
        ?: reportState.weeklyChartData?.sleepLatencyMinutes
        ?: reportState.reportData?.sleepLatencyMinutes
        ?: 0.0
    val prevAvgLatencyMinutes = reportState.prevWeeklyChartData?.averageLatencyMinutes
        ?: reportState.prevWeeklyChartData?.sleepLatencyMinutes
        ?: reportState.prevDayReportData?.sleepLatencyMinutes
        ?: 0.0
    val avgLatencyMillis = (avgLatencyMinutes * 60000).toLong()
    val prevAvgLatencyMillis = (prevAvgLatencyMinutes * 60000).toLong()


    val avgDurationText = formatSleepDurationFromMillis(avgDurationMillis, hourUnit, minuteUnit)
        .toAnnotatedString(baseSectionStyle, baseBodyStyle, unitLabels)
    val avgScoreText = "${avgScore}$pointUnit".toAnnotatedString(baseSectionStyle, baseBodyStyle, unitLabels)
    val avgLatencyText = formatSleepDurationFromMillis(avgLatencyMillis, hourUnit, minuteUnit)
        .toAnnotatedString(baseSectionStyle, baseBodyStyle, unitLabels)

    val noiseAvgText = buildAnnotatedString {
        withStyle(baseSectionStyle) { append(values.noiseAvg.roundToInt().toString()) }
        withStyle(baseBodyStyle) { append("dB") }
    }
    val dailyItems = listOf(
        ComparisonItem(
            metric = ReportMetric.SLEEP_DURATION,
            valueText = durationText,
            isIncrease = sleepDurationMillis > prevSleepDurationMillis
        ),
        ComparisonItem(
            metric = ReportMetric.SLEEP_SCORE,
            valueText = scoreText,
            isIncrease = rawScore > prevScore
        ),
        ComparisonItem(
            metric = ReportMetric.SLEEP_LATENCY,
            valueText = latencyText,
            isIncrease = latencyMillis > prevLatencyMillis
        ),
        ComparisonItem(
            metric = ReportMetric.AVG_NOISE,
            valueText = noiseAvgText,
        ),
    )
    val weeklyItems = listOf(
        ComparisonItem(
            metric = ReportMetric.SLEEP_DURATION,
            valueText = avgDurationText,
            isIncrease = avgDurationMillis > prevAvgDurationMillis
        ),
        ComparisonItem(
            metric = ReportMetric.SLEEP_SCORE,
            valueText = avgScoreText,
            isIncrease = avgScore > prevAvgScore
        ),
        ComparisonItem(
            metric = ReportMetric.SLEEP_LATENCY,
            valueText = avgLatencyText,
            isIncrease = avgLatencyMillis > prevAvgLatencyMillis
        ),
    )
    val textMeasurer = rememberTextMeasurer()
    val showPreviewOverlay = !reportState.hasAnySession && !reportState.isAwaitingSession
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ReportHeader(
                reportState = reportState,

                rangeItem = rangeItem,
                onSelectItem = {
                    rangeItem = it
                },
            )
            if (rangeItem == ReportRangeTab.TODAY) {
                Calendar(
                    modifier = Modifier.calendarExpandDrag(
                        isExpanded = reportState.isCalendarExpanded,
                        onExpandedChange = onToggleCalendarExpanded,
                    ),
                    reportState = reportState,
                    selectedDate = reportState.date,
                    onDateSelected = { date ->
                        onDateSelected(date)
                    },
                    onPrevClicked = { onPrevClicked(it) },
                    onNextClicked = { onNextClicked(it) },
                    onToggleExpanded = onToggleCalendarExpanded,
                )
            }
            val calendarScrollConnection = rememberCalendarExpandConnection(
                isExpanded = reportState.isCalendarExpanded,
                onExpandedChange = onToggleCalendarExpanded,
            )
            val cardScrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(calendarScrollConnection),

                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    when (rangeItem) {
                        ReportRangeTab.TODAY -> DailyReportCard(
                            reportState = reportState,
                            sleepDurationMillis = sleepDurationMillis,
                            targetDate = targetDate,
                            reportData = reportState.reportData,
                            bedTimeText = bedTimeText,
                            wakeTimeText = wakeTimeText,
                            values = values,
                            labelStyle = labelStyle,
                            dailyItems = dailyItems,
                            cardScrollState = cardScrollState,
                        )
                        else -> WeeklyReportCard(
                            reportState = reportState,
                            labelStyle = labelStyle,
                            xLabels = xLabels,
                            textMeasurer = textMeasurer,
                            items = weeklyItems,
                            cardScrollState = cardScrollState,
                        )
                    }

                }

                // 코골이/AI 조언/과거 비교는 하루치 분석이라 "오늘" 탭에서만 의미가 있다.
                // 데모 데이터 위에 분석을 얹으면 가짜 결론을 보여주는 셈이라 실제 기록일 때만 그린다.
                if (rangeItem == ReportRangeTab.TODAY && !reportState.isPreview) {
                    PremiumReportSections(
                        reportData = reportState.reportData,
                        recentAverage = reportState.recentAverage,
                        recentSampleCount = reportState.recentSampleCount,
                        adviceState = reportState.sleepAdvice,
                        isUserPremium = isUserPremium,
                        onUpgradeClicked = onUpgradeClicked,
                    )
                }

                if (!reportState.isPreview) {
                    IconButton(
                        modifier = Modifier.size(36.dp),
                        onClick = {
                            reportState.reportData?.let {
                                onReportDeleteClicked(it.sessionId)
                            }
                        }
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(Res.drawable.ic_report),
                            contentDescription = stringResource(Res.string.report_delete_report),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            when {
                reportState.isAwaitingSession -> PreviewNoticeBar(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(Res.string.report_preparing_title),
                    description = stringResource(Res.string.report_preparing_description),
                )
                // 열려던 세션이 끝내 없었던 경우. 데모 데이터를 자기 기록으로 오해하지 않도록
                // 왜 비어 있는지를 먼저 알린다.
                reportState.sessionUnavailable -> PreviewNoticeBar(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(Res.string.report_session_unavailable_title),
                    description = stringResource(Res.string.report_session_unavailable_description),
                )
                showPreviewOverlay -> PreviewNoticeBar(modifier = Modifier.fillMaxWidth())
            }
        }
        if (reportState.showDeleteDialog && reportState.pendingDeleteSessionId != null) {
            AlertDialog(
                onDismissRequest = onDismissDeleteDialog,
                title = { Text(stringResource(Res.string.report_delete_report)) },
                text = { Text(stringResource(Res.string.report_delete_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = { onConfirmDelete(reportState.pendingDeleteSessionId) }
                    ) {
                        Text(stringResource(Res.string.common_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDeleteDialog) {
                        Text(stringResource(Res.string.common_cancel))
                    }
                }
            )
        }
    }
}

/** 프리미엄이 아닌 사용자에게 특정 리포트 섹션을 잠긴 상태(블러/안내)로 감싸 보여주는 래퍼. */
@Composable
fun GatedReportSection(
    title: String,
    onUpgradeClicked: () -> Unit,
) {

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = SleepTheme.background,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "🔒", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyHighlight,
                color = Color.White
            )
            Text(
                text = stringResource(Res.string.report_gated_description),
                style = MaterialTheme.typography.caption,
                color = Color.White
            )
            Surface(
                modifier = Modifier.clickable { onUpgradeClicked() },
                shape = RoundedCornerShape(50.dp),
                color = primary
            ) {
                Text(
                    text = stringResource(Res.string.report_upgrade_cta),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 데모 데이터임을 알리는 하단 안내 배너.
 *
 * 예전에는 이 배너와 별개로 화면 전체에 어두운 스크림(PreviewOverlay)을 깔아 "실제 데이터가
 * 아님"을 표현했다. 그런데 스크림은 차트와 숫자까지 같이 흐리게 만들어 앱을 둘러보러 온
 * 사용자에게 보여줄 것을 오히려 가렸다. 지금은 이 배너 하나로만 알린다.
 */
/** 실제 측정 기록이 없어 데모 데이터를 보여주고 있음을 알리는 상단 안내 바. */
@Composable
fun PreviewNoticeBar(
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.report_demo_title),
    description: String = stringResource(Res.string.report_demo_description),
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SleepTheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyHighlight,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.caption,
                textAlign = TextAlign.Center,
                color = Color.White,
            )
        }
    }
}

/**
 * 리포트 카드 스크롤과 달력 펼침/접힘을 연결한다.
 *
 * - 달력이 펼쳐진 상태에서 카드를 위로 끌면, 카드가 스크롤되기 전에 달력이 먼저 접힌다.
 * - 카드가 이미 최상단인데 아래로 더 끌면(= 자식이 소비하지 못한 델타가 남으면) 달력이 펼쳐진다.
 *
 * 상태 전달이 인텐트 채널을 거쳐 비동기로 돌아오기 때문에, 임계값을 넘긴 뒤에도 같은 제스처의
 * 남은 델타가 계속 들어와 토글이 연달아 발사될 수 있다. 기존 calendarExpandDrag 가 `handled`
 * 래치를 두는 이유와 같다. 여기서도 래치를 두고 제스처가 끝날 때 초기화한다.
 */
@Composable
private fun rememberCalendarExpandConnection(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
): NestedScrollConnection {
    val thresholdPx = with(LocalDensity.current) { CALENDAR_EXPAND_THRESHOLD.toPx() }
    // 연결 객체는 한 번만 만들고 최신 값은 여기서 읽는다. remember(isExpanded) 로 매번
    // 새로 만들면 제스처 도중 객체가 갈려 누적값과 래치가 날아간다.
    val expandedState = rememberUpdatedState(isExpanded)
    val onChange = rememberUpdatedState(onExpandedChange)

    return remember(thresholdPx) {
        object : NestedScrollConnection {
            private var accumulated = 0f
            private var latched = false

            private fun reset() {
                accumulated = 0f
                latched = false
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (latched || !expandedState.value || available.y >= 0f) return Offset.Zero
                accumulated += available.y
                if (accumulated <= -thresholdPx) {
                    latched = true
                    accumulated = 0f
                    onChange.value(false)
                }
                // 접히는 동안에는 카드가 같이 움직이지 않도록 델타를 삼킨다.
                return available
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (latched || expandedState.value || available.y <= 0f) return Offset.Zero
                accumulated += available.y
                if (accumulated >= thresholdPx) {
                    latched = true
                    accumulated = 0f
                    onChange.value(true)
                }
                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                reset()
                return Velocity.Zero
            }
        }
    }
}

/**
 * 달력 펼침/접힘 전환 임계값.
 *
 * 터치 슬롭보다 충분히 커서 날짜를 누르다 손이 살짝 흔들려도 전환되지 않는다.
 * 달력을 직접 끄는 제스처와 리포트 카드 스크롤 연동이 같은 값을 써야 조작감이 일관된다.
 */
private val CALENDAR_EXPAND_THRESHOLD = 40.dp

/**
 * 달력 표면이나 바로 아래 손잡이를 세로로 끌어 월간↔주간 보기를 전환한다.
 *
 * 임계값(40dp)은 터치 슬롭보다 충분히 커서, 날짜를 누르다 손이 살짝 흔들려도 전환되지 않는다.
 * 손을 떼는 시점이 아니라 임계값을 넘는 순간 바로 전환해서 달력이 손가락을 따라 열리는 느낌을 준다.
 */
@Composable
private fun Modifier.calendarExpandDrag(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
): Modifier {
    val thresholdPx = with(LocalDensity.current) { CALENDAR_EXPAND_THRESHOLD.toPx() }
    var accumulated by remember { mutableFloatStateOf(0f) }
    var handled by remember { mutableStateOf(false) }

    return this.pointerInput(isExpanded) {
        detectVerticalDragGestures(
            onDragStart = {
                accumulated = 0f
                handled = false
            },
            onDragEnd = { accumulated = 0f },
            onDragCancel = { accumulated = 0f },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                accumulated += dragAmount
                // 한 번의 드래그로 두 번 전환되지 않도록 래치를 건다.
                if (!handled) {
                    if (!isExpanded && accumulated >= thresholdPx) {
                        handled = true
                        onExpandedChange(true)
                    } else if (isExpanded && accumulated <= -thresholdPx) {
                        handled = true
                        onExpandedChange(false)
                    }
                }
            }
        )
    }
}

/**
 * 달력을 좌우로 끌어 이전/다음으로 이동한다.
 *
 * 세로 드래그(월간↔주간 전환)와 같은 임계값(40dp)·래치 구조를 쓴다. 두 제스처는 각각 자기 축의
 * 터치 슬롭을 먼저 넘긴 쪽이 이벤트를 소비하기 때문에 서로 가로채지 않는다.
 * 이동할 기록이 없는 방향(canPrev/canNext=false)은 예전 화살표 버튼의 비활성 상태와 똑같이 무시한다.
 */
@Composable
private fun Modifier.calendarMonthSwipe(
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
): Modifier {
    val thresholdPx = with(LocalDensity.current) { 40.dp.toPx() }
    var accumulated by remember { mutableFloatStateOf(0f) }
    var handled by remember { mutableStateOf(false) }

    return this.pointerInput(canPrev, canNext) {
        detectHorizontalDragGestures(
            onDragStart = {
                accumulated = 0f
                handled = false
            },
            onDragEnd = { accumulated = 0f },
            onDragCancel = { accumulated = 0f },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                accumulated += dragAmount
                // 한 번의 드래그로 두 칸을 건너뛰지 않도록 래치를 건다.
                if (!handled) {
                    if (accumulated <= -thresholdPx && canNext) {
                        handled = true
                        onNext()
                    } else if (accumulated >= thresholdPx && canPrev) {
                        handled = true
                        onPrev()
                    }
                }
            }
        )
    }
}

/** 날짜 선택용 캘린더. 접힌 상태(주간)와 펼친 상태(월간)를 드래그/스와이프로 전환할 수 있다. */
@Composable
fun Calendar(
    reportState: ReportContract.State,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onPrevClicked: (DateTimeUnit.DateBased) -> Unit,
    onNextClicked: (DateTimeUnit.DateBased) -> Unit,
    modifier: Modifier = Modifier,
    // 홈 화면은 항상 월간으로 보여주기 위해 true 를 고정으로 넘긴다.
    // 홈과 리포트가 ReportViewModel 을 공유하기 때문에, 공용 플래그(isCalendarExpanded)를
    // 홈에서 켜버리면 리포트 탭 달력까지 같이 펼쳐진다. 그래서 표시 여부만 따로 받는다.
    isExpanded: Boolean = reportState.isCalendarExpanded,
    // 손잡이를 없앤 뒤 스크린리더로 월간/주간을 바꿀 방법이 사라지지 않도록,
    // 접근성 커스텀 액션으로 토글을 남긴다.
    onToggleExpanded: ((Boolean) -> Unit)? = null,
) {
    val currentDayOfWeekNum = selectedDate.dayOfWeek.isoDayNumber
    val weekStartDate = selectedDate.minus((currentDayOfWeekNum - 1).toLong(), DateTimeUnit.DAY)
    val weekDates = (0 until 7).map { weekStartDate.plus(it.toLong(), DateTimeUnit.DAY) }


    val firstDayOfMonth = LocalDate(selectedDate.year, selectedDate.month, 1)
    val firstDayOfWeekNum = firstDayOfMonth.dayOfWeek.isoDayNumber
    val calendarStartDate =
        firstDayOfMonth.minus((firstDayOfWeekNum - 1).toLong(), DateTimeUnit.DAY)
    // 🐛 버그 수정: 35칸으로 고정돼 있어서 6주에 걸치는 달(1일이 주 후반에 시작하면서 30~31일까지
    // 있는 달)은 마지막 주가 통째로 잘려 화면에 나오지 않았다. 필요한 주 수를 계산해 35칸 또는 42칸을 그린다.
    val daysInMonth = firstDayOfMonth
        .plus(1, DateTimeUnit.MONTH)
        .minus(1, DateTimeUnit.DAY)
        .day
    val monthCellCount = (((firstDayOfWeekNum - 1) + daysInMonth + 6) / 7) * 7
    val monthDates =
        (0 until monthCellCount).map { calendarStartDate.plus(it.toLong(), DateTimeUnit.DAY) }


    val calendarTitleText =
        if (isExpanded) formatCalendarMonth(reportState.date) else formatCalendarWeek(
            reportState.date
        )

    val unit = if (isExpanded) DateTimeUnit.MONTH else DateTimeUnit.WEEK
    val prevDescription = if (isExpanded) stringResource(Res.string.report_calendar_prev_month)
        else stringResource(Res.string.report_calendar_prev_week)
    val nextDescription = if (isExpanded) stringResource(Res.string.report_calendar_next_month)
        else stringResource(Res.string.report_calendar_next_week)
    val collapseDescription = stringResource(Res.string.report_calendar_collapse)
    val expandDescription = stringResource(Res.string.report_calendar_expand)

    val prevTargetDate =
        if (isExpanded) reportState.date.minus(1, DateTimeUnit.MONTH)
        else reportState.date.minus(1, DateTimeUnit.WEEK)
    val nextTargetDate =
        if (isExpanded) reportState.date.plus(1, DateTimeUnit.MONTH)
        else reportState.date.plus(1, DateTimeUnit.WEEK)

    val hasSessionInPrev = remember(
        prevTargetDate,
        isExpanded,
        reportState.sessionDates,
        reportState.isPreview
    ) {
        if (reportState.isPreview) true
        else {
            val (start, end) = getTargetRange(prevTargetDate, isExpanded)
            reportState.sessionDates.any { it in start..end }
        }
    }
    val hasSessionInNext = remember(
        nextTargetDate,
        isExpanded,
        reportState.sessionDates,
        reportState.isPreview
    ) {
        if (reportState.isPreview) true
        else {
            val (start, end) = getTargetRange(nextTargetDate, isExpanded)
            reportState.sessionDates.any { it in start..end }
        }
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .animateContentSize()
            .calendarMonthSwipe(
                canPrev = hasSessionInPrev,
                canNext = hasSessionInNext,
                onPrev = { onPrevClicked(unit) },
                onNext = { onNextClicked(unit) }
            )
            // 화살표 버튼이 없어지면 스크린리더로 달을 바꿀 방법이 사라진다.
            // 스와이프를 대신할 커스텀 액션을 달아 둔다.
            .semantics {
                customActions = listOfNotNull(
                    if (hasSessionInPrev) {
                        CustomAccessibilityAction(prevDescription) { onPrevClicked(unit); true }
                    } else null,
                    if (hasSessionInNext) {
                        CustomAccessibilityAction(nextDescription) { onNextClicked(unit); true }
                    } else null,
                    onToggleExpanded?.let { toggle ->
                        CustomAccessibilityAction(
                            if (isExpanded) collapseDescription else expandDescription
                        ) { toggle(!isExpanded); true }
                    }
                )
            }
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primary,
                        primary.copy(0.4f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 월 이동은 화살표 버튼 대신 달력 좌우 스와이프로 한다(위 calendarMonthSwipe).
            // 제목만 남으므로 가운데로 정렬한다.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = calendarTitleText,
                    style = MaterialTheme.typography.bodyText,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val weekdayLabels = listOf(
                    stringResource(Res.string.report_weekday_mon),
                    stringResource(Res.string.report_weekday_tue),
                    stringResource(Res.string.report_weekday_wed),
                    stringResource(Res.string.report_weekday_thu),
                    stringResource(Res.string.report_weekday_fri),
                    stringResource(Res.string.report_weekday_sat),
                    stringResource(Res.string.report_weekday_sun),
                )
                weekdayLabels.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.bodyText,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
            val weeks = monthDates.chunked(7)
            if (isExpanded) {
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    weeks.forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            week.forEach { date ->
                                CalendarDayCell(
                                    modifier = Modifier
                                        .height(44.dp)
                                        .weight(1f),
                                    reportState = reportState,
                                    selectedDate = selectedDate,
                                    date = date,
                                    hasSession = reportState.sessionDates.contains(date),
                                    onDateSelected = {
                                        onDateSelected(it)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekDates.forEach { date ->
                        CalendarDayCell(
                            modifier = Modifier
                                .height(44.dp)
                                .weight(1f),
                            reportState = reportState,
                            selectedDate = selectedDate,
                            date = date,
                            hasSession = reportState.sessionDates.contains(date),
                            onDateSelected = onDateSelected
                        )
                    }
                }
            }
        }
    }
}

/**
 * 날짜 칸 하나. 숫자를 덮던 36dp 반투명 원을 걷어내고, 점수는 숫자 아래 7dp 점으로만 표시한다.
 */
/** 캘린더의 날짜 한 칸. 선택 여부/오늘 여부/그 날의 점수(색상)를 함께 표시한다. */
@Composable
fun CalendarDayCell(
    modifier: Modifier,
    selectedDate: LocalDate,
    date: LocalDate,
    reportState: ReportContract.State,
    hasSession: Boolean,
    onDateSelected: (LocalDate) -> Unit
) {
    val hasValidData = reportState.isPreview || hasSession
    val isSelected = date == selectedDate
    val currentDayScore = if (hasValidData) reportState.reportData?.dailyScores?.get(date) ?: 0 else 0
    val scoreColor = SleepTheme.score
    Box(
        modifier = if (hasValidData) modifier.clickable { onDateSelected(date) } else modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .then(
                        if (isSelected) Modifier.border(2.5.dp, scoreColor.getColorForScore(currentDayScore), CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.day.toString(),
                    style = MaterialTheme.typography.bodyText,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    // 🐛 버그 수정: 예전에는 기록이 없는 날에 아무것도 그리지 않아서
                    // 달력 격자가 군데군데 빈칸으로 깨져 보였다. 이제는 흐리게라도 숫자를 남긴다.
                    color = if (hasValidData) Color.White
                    else Color.White.copy(alpha = 0.35f)
                )
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (hasValidData) scoreColor.getColorForScore(currentDayScore) else Color.Transparent)
            )
        }
    }
}

/** 점수 구간별 색. 예전의 Color.Red/Color.Magenta 하드코딩 대신 기존 팔레트를 쓴다. */


/** 리포트 상단의 날짜/캘린더 토글 헤더. */
@Composable
private fun ReportHeader(
    reportState: ReportContract.State,
    rangeItem: ReportRangeTab,
    onSelectItem: (ReportRangeTab) -> Unit,
) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = formatDate(reportState.date),
            style = MaterialTheme.typography.bodyText,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        if(!reportState.isCalendarExpanded) {
            SelectableChipGroup(
                items = ReportRangeTab.entries,
                selectedItem = rangeItem,
                onSelectItem = onSelectItem,
            ) {
                stringResource(it.resId)
            }
        }
    }
}
/** 선택한 하루의 수면 요약 카드(점수, 단계별 시간, 취침/기상 시각 등)를 그린다. */
@Composable
fun DailyReportCard(
    reportState: ReportContract.State,
    sleepDurationMillis: Long,
    targetDate: LocalDate?,
    reportData: ReportContract.ReportData?,
    bedTimeText: String,
    wakeTimeText: String,
    values: EnvironmentValues,
    labelStyle: TextStyle,
    dailyItems: List<ComparisonItem>,
    cardScrollState: ScrollState
) {
    val stageTypes = remember {
        listOf(
            SleepStageType.AWAKE,
            SleepStageType.LIGHT,
            SleepStageType.DEEP,
            SleepStageType.REM
        )
    }
    if (reportData == null) return
    val stageBreakdown = rememberStageBreakdown(stageTypes, reportData)
    val itemMap = dailyItems.associateBy { it.metric }


    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(
                color = SleepTheme.background,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .verticalScroll(cardScrollState),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(itemMap[ReportMetric.SLEEP_DURATION])
                SummaryItem(itemMap[ReportMetric.SLEEP_SCORE])
                SummaryItem(itemMap[ReportMetric.SLEEP_LATENCY])
            }
            GraphHeader(
                title = stringResource(Res.string.report_section_sleep_stages),
            )
            // 범례는 도넛 옆에 둔다. 예전에는 타임라인과 가로를 반씩 나눠 가졌는데, 그러면
            // 타임라인의 플롯 영역이 소음 그래프의 절반도 안 돼 밤 전체가 뭉개져 보였다.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SleepStageDonut(
                    breakdown = stageBreakdown,
                    centerMillis = sleepDurationMillis
                )
                ChartLegend(
                    modifier = Modifier.weight(1f),
                    items = stageBreakdown,
                )
            }
            SleepTimeLineChart(
                modifier = Modifier.fillMaxWidth(),
                reportState = reportState,
                stageTypes = stageTypes,
                targetDate = targetDate,
            )
            GraphHeader(
                title = stringResource(Res.string.report_section_noise),
                unit = "dB",
                // 요약 그리드에 따로 두는 대신 그래프 바로 위 오른쪽 끝에 붙인다.
                trailingContent = {
                    itemMap[ReportMetric.AVG_NOISE]?.let { item ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(item.metric.labelRes),
                                style = MaterialTheme.typography.caption,
                                color = Color.White
                            )
                            Text(text = item.valueText)
                        }
                    }
                }
            )
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                EnvironmentChart(
                    category = EnvironmentCategory.NOISE,
                    values = values,
                    color = EnvironmentCategory.NOISE.toStatus(values).primaryColor,
                    labelStyle = labelStyle
                )
            }
            // EnvironmentChart의 실제 플롯 영역(왼쪽 LABEL_WIDTH+Y_AXIS_PADDING, 오른쪽 Y_AXIS_PADDING
            // 만큼 여백)과 가로 길이를 맞춰, 취침/기상 시각 텍스트가 그래프의 시작/끝 x좌표와 정렬되게 한다.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = LABEL_WIDTH + Y_AXIS_PADDING, end = Y_AXIS_PADDING),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bedTimeText,
                    style = labelStyle,
                    color = Color.White
                )
                Text(
                    text = wakeTimeText,
                    style = labelStyle,
                    color = Color.White
                )
            }
        }
    }
}
/** 이번 주 평균을 지난 주와 비교해 보여주는 주간 요약 카드. */
@Composable
fun WeeklyReportCard(
    reportState: ReportContract.State,
    labelStyle: TextStyle,
    xLabels: List<ChartDataEntity>,
    textMeasurer: TextMeasurer,
    items: List<ComparisonItem> = emptyList(),
    cardScrollState: ScrollState
) {
    val itemMap = items.associateBy { it.metric }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(
                color = SleepTheme.background,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                // 마지막 그래프 아래가 8dp 뿐이라 x축 라벨이 카드 테두리에 붙어 있었다.
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp)
                .verticalScroll(cardScrollState),
            // SpaceBetween 은 verticalScroll 안에서는 나눌 여백이 없어 Top 과 같다.
            // 그래프 세 개가 서로 붙어 있던 것도 이 때문이다.
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 주간 평균 요약. "오늘" 탭과 같은 SummaryItem 을 써서 두 탭의 숫자 배치를 맞춘다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(itemMap[ReportMetric.SLEEP_DURATION])
                SummaryItem(itemMap[ReportMetric.SLEEP_SCORE])
                SummaryItem(itemMap[ReportMetric.SLEEP_LATENCY])
            }

            TimeChart(
                reportState = reportState,
                selectedMetric = ReportMetric.SLEEP_DURATION,
                xLabels = xLabels,
                textMeasurer = textMeasurer,
                labelStyle = labelStyle,
            )

            TimeChart(
                reportState = reportState,
                selectedMetric = ReportMetric.SLEEP_SCORE,
                xLabels = xLabels,
                textMeasurer = textMeasurer,
                labelStyle = labelStyle,
            )
            TimeChart(
                reportState = reportState,
                selectedMetric = ReportMetric.SLEEP_LATENCY,
                xLabels = xLabels,
                textMeasurer = textMeasurer,
                labelStyle = labelStyle,
            )
        }
    }
}

/** 그래프 영역의 제목/범례 헤더. */
@Composable
private fun GraphHeader(
    title: String,
    unit: String? = null,
    // 그래프 오른쪽 끝에 붙는 값(예: 평균 소음).
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyHighlight,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            unit?.let {
                Text(
                    text = "($it)",
                    style = MaterialTheme.typography.bodyHighlight,
                    color = Color.White
                )
            }
        }
        trailingContent?.invoke()
    }
}

/** 비교 카드에 쓰이는 지표 하나(값 + 증감 표시)를 그리는 작은 타일. */
@Composable
private fun SummaryItem(item: ComparisonItem?) {
    if (item == null) return
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(item.metric.labelRes),
            style = MaterialTheme.typography.caption,
            color = Color.White
        )
        Text(
            text = item.valueText,
        )
    }
}

// 소음 y축에 항상 표시할 정상+비정상(위험) 참고 범위입니다.
// 실측값이 이 범위보다 좁으면 이 범위로, 이 범위보다 넓으면 실측값 기준으로 넓어집니다.
private const val NOISE_CHART_MIN = 0f
private const val NOISE_CHART_MAX = 90f

private const val NOISE_SPIKE_RATIO = 1.5f

// 💡 소음측정 정확도 개선: environmentHistory의 한 원소는 30초 버킷 하나에 해당합니다.
// 단발성 스파이크(알림음, 한 번의 뒤척임 등) 하나만으로 "소음 상승"이라 단정하지 않도록,
// 깨어난 구간에 30초 버킷이 2개 이상 있을 때는 임계값을 넘는 버킷이 연속으로 이어질 때만
// 원인으로 인정합니다. 구간이 버킷 1개뿐이라면(짧은 각성) 더 잘게 쪼갤 해상도가 없으므로
// 해당 버킷 단독 값으로 판단합니다.
private const val MIN_SUSTAINED_SPIKE_BUCKETS = 2
private fun <T> resolveWakeCause(
    environmentHistory: List<T>,
    startRatio: Float,
    endRatio: Float,
    avgNoise: Float,
    noiseOf: (T) -> Float,
    briefLabel: String,
    noiseSpikeLabel: String,
): String {
    if (environmentHistory.isEmpty()) return briefLabel

    val size = environmentHistory.size
    val startIdx = (startRatio * size).toInt().coerceIn(0, size - 1)
    val endIdx = (endRatio * size).toInt().coerceIn(startIdx, size - 1)
    val window = environmentHistory.subList(startIdx, endIdx + 1)
    if (window.isEmpty()) return briefLabel

    val spikeThreshold = if (avgNoise > 0f) avgNoise * NOISE_SPIKE_RATIO else null

    spikeThreshold?.let {
        val noiseSpike = if (window.size == 1) {
            noiseOf(window[0]) >= spikeThreshold
        } else {
            var currentRun = 0
            var maxRun = 0
            for (item in window) {
                if (noiseOf(item) >= spikeThreshold) {
                    currentRun++
                    maxRun = maxOf(maxRun, currentRun)
                } else {
                    currentRun = 0
                }
            }
            maxRun >= MIN_SUSTAINED_SPIKE_BUCKETS
        }
        return if (noiseSpike) noiseSpikeLabel else briefLabel
    }
    return briefLabel

}

@Composable
fun SleepTimeLineChart(
    modifier: Modifier = Modifier,
    reportState: ReportContract.State,
    stageTypes: List<SleepStageType>,
    targetDate: LocalDate?,
) {

    val stageColors = SleepTheme.stage
    var tooltipInfo by remember { mutableStateOf<Pair<Offset, String>?>(null) }
    // 거터를 공유해서 플롯 영역을 맞추고 있고, 그 거터가 지금까지 비어 있었다.
    val textMeasurer = rememberTextMeasurer()
    val stageLabelStyle = MaterialTheme.typography.caption.copy(
        color = Color.White
    )
    val stageNames = stageTypes.associateWith { stringResource(it.stageNameRes) }
    val wakeCauseBrief = stringResource(Res.string.report_wake_cause_brief)
    val wakeCauseNoiseSpike = stringResource(Res.string.report_wake_cause_noise_spike)
    val minuteUnit = stringResource(Res.string.report_unit_minute)

    val environmentHistory = reportState.reportData?.environmentHistory ?: emptyList()
    val avgNoise = reportState.reportData?.avgNoise ?: 0f

    val stageTimeLine = if (reportState.isPreview) {
        val bedTime = reportState.reportData?.dailyBedTimes?.get(targetDate)
        val wakeTime = reportState.reportData?.dailyWakeTimes?.get(targetDate)
        if (bedTime != null && wakeTime != null) {
            var currentInstant = bedTime.toInstant(TimeZone.currentSystemDefault())
            reportState.reportData.stageTimeline.map { stage ->
                val start = currentInstant.toLocalDateTime(TimeZone.currentSystemDefault())
                currentInstant = currentInstant.plus(stage.duration)
                stage.copy(startTime = start)
            }
        } else reportState.reportData?.stageTimeline ?: emptyList()
    } else reportState.reportData?.stageTimeline ?: emptyList()

    val mergedTimeline = remember(stageTimeLine) {
        if (stageTimeLine.isEmpty()) emptyList()
        else {
            val mergedList = mutableListOf<MergedStage<SleepStageType>>()
            var currentType = stageTimeLine.first().type
            var accumulatedDuration = stageTimeLine.first().duration.inWholeMilliseconds

            for (i in 1 until stageTimeLine.size) {
                val item = stageTimeLine[i]
                if (item.type == currentType) {
                    accumulatedDuration += item.duration.inWholeMilliseconds
                } else {
                    mergedList.add(MergedStage(currentType, accumulatedDuration))
                    currentType = item.type
                    accumulatedDuration = item.duration.inWholeMilliseconds
                }
            }
            mergedList.add(MergedStage(currentType, accumulatedDuration))
            mergedList
        }
    }

    val totalDurationMillis =
        mergedTimeline.sumOf { it.durationMillis }.coerceAtLeast(1L)

    val timelineHeight = 16.dp * stageTypes.size
    Box(modifier = modifier.height(timelineHeight)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(mergedTimeline) {
                    detectTapGestures { offset ->
                        // 아래 그래프(심박수/소음)와 가로 축을 맞추기 위해 사용한
                        // LABEL_WIDTH + Y_AXIS_PADDING 만큼의 왼쪽 여백을 탭 좌표 계산에도 반영합니다.
                        val chartStartX = LABEL_WIDTH.toPx() + Y_AXIS_PADDING.toPx()
                        val chartEndX = size.width - Y_AXIS_PADDING.toPx()
                        val chartWidth = (chartEndX - chartStartX).coerceAtLeast(1f)

                        var accumulatedTime = 0L
                        mergedTimeline.forEach { stage ->
                            val xStart =
                                chartStartX + (accumulatedTime.toFloat() / totalDurationMillis) * chartWidth
                            val xEnd =
                                chartStartX + ((accumulatedTime + stage.durationMillis).toFloat() / totalDurationMillis) * chartWidth
                            if (offset.x in xStart..xEnd) {
                                if (stage.type == SleepStageType.AWAKE) {
                                    val durationMin = stage.durationMillis / 60000
                                    val startRatio = accumulatedTime.toFloat() / totalDurationMillis
                                    val endRatio =
                                        (accumulatedTime + stage.durationMillis).toFloat() / totalDurationMillis
                                    val cause = resolveWakeCause(
                                        environmentHistory = environmentHistory,
                                        startRatio = startRatio,
                                        endRatio = endRatio,
                                        avgNoise = avgNoise,
                                        noiseOf = { it.noise },
                                        briefLabel = wakeCauseBrief,
                                        noiseSpikeLabel = wakeCauseNoiseSpike,
                                    )
                                    tooltipInfo = offset to "$cause (${durationMin}$minuteUnit)"
                                } else {
                                    tooltipInfo = null
                                }
                                return@detectTapGestures
                            }
                            accumulatedTime += stage.durationMillis
                        }
                        tooltipInfo = null
                    }
                }
        ) {
            val chartStartX = LABEL_WIDTH.toPx() + Y_AXIS_PADDING.toPx()
            val chartEndX = size.width - Y_AXIS_PADDING.toPx()
            val chartWidth = (chartEndX - chartStartX).coerceAtLeast(1f)

            val rowHeightPx = 16.dp.toPx()
            val barCornerRadius = 4.dp.toPx()

            // 1. 레인마다 이름과 옅은 바탕을 깔아 두는 영역
            stageTypes.forEachIndexed { index, type ->
                val yOffset = rowHeightPx * index

                val baseStageColor = stageColors.getColorForStage(type)

                drawRoundRect(
                    color = baseStageColor.copy(alpha = 0.10f),
                    topLeft = Offset(chartStartX, yOffset),
                    size = Size(chartWidth, rowHeightPx),
                    cornerRadius = CornerRadius(barCornerRadius, barCornerRadius)
                )

                val measured = textMeasurer.measure(stageNames.getValue(type), stageLabelStyle)
                drawText(
                    measured,
                    topLeft = Offset(
                        x = (chartStartX - measured.size.width - Y_AXIS_PADDING.toPx()).coerceAtLeast(0f),
                        y = yOffset + (rowHeightPx - measured.size.height) / 2f
                    )
                )
            }

            // 2. 실제 타임라인 데이터를 기반으로 수면 단계 바(Bar)를 그리는 영역
            if (mergedTimeline.isNotEmpty()) {
                var accumulatedTime = 0L

                mergedTimeline.forEach { stage ->
                    val xStart = chartStartX + (accumulatedTime.toFloat() / totalDurationMillis) * chartWidth
                    val xEnd = chartStartX + ((accumulatedTime + stage.durationMillis).toFloat() / totalDurationMillis) * chartWidth
                    val barWidth = (xEnd - xStart).coerceAtLeast(1f)

                    if (barWidth > 0f) {
                        val color = stageColors.getColorForStage(stage.type)

                        val stageIdx = stageTypes.indexOf(stage.type).coerceAtLeast(0)
                        val yOffset = rowHeightPx * stageIdx

                        drawRoundRect(
                            color = color,
                            topLeft = Offset(xStart, yOffset),
                            size = Size(barWidth, rowHeightPx),
                            cornerRadius = CornerRadius(barCornerRadius, barCornerRadius)
                        )
                    }
                    accumulatedTime += stage.durationMillis
                }
            }
        }

        // 툴팁 표시
        tooltipInfo?.let { (infoOffset, text) ->
            Surface(
                modifier = Modifier
                    .offset(x = (infoOffset.x / 2f).dp, y = (infoOffset.y / 2f).dp - 40.dp),
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    modifier = Modifier.padding(4.dp),
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}

data class MergedStage<T>(
    val type: T,
    var durationMillis: Long
)

/** 요일별 취침/기상 시각을 비교해 보여주는 차트. */
@Composable
fun TimeChart(
    reportState: ReportContract.State,
    selectedMetric: ReportMetric,
    xLabels: List<ChartDataEntity>,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val finalReportData = reportState.weeklyChartData ?: return

    val currentBedTimes = remember(xLabels, finalReportData) {
        xLabels.mapNotNull { finalReportData.dailyBedTimes[it.date] }
    }
    val currentWakeTimes = remember(xLabels, finalReportData) {
        xLabels.mapNotNull { finalReportData.dailyWakeTimes[it.date] }
    }
    val currentLatencyMinutes = remember(xLabels, finalReportData) {
        xLabels.mapNotNull { finalReportData.dailySleepLatencyMinutes[it.date] }
    }

    val yLabels =
        remember(selectedMetric, currentBedTimes, currentWakeTimes, currentLatencyMinutes) {
            when (selectedMetric) {
                ReportMetric.SLEEP_DURATION -> ChartUtil.generateYLabels(inputTimes = currentBedTimes + currentWakeTimes)
                ReportMetric.SLEEP_SCORE -> listOf("0", "25", "50", "75", "100")
                ReportMetric.SLEEP_LATENCY -> ChartUtil.generateYLabels(inputLatencyMinutes = currentLatencyMinutes)
                else -> listOf("0", "25", "50", "75", "100")
            }
        }
    var selectedDate by remember(xLabels) { mutableStateOf(reportState.date) }

    val selectedBedTime = finalReportData.dailyBedTimes[selectedDate]
    val selectedWakeTime = finalReportData.dailyWakeTimes[selectedDate]
    val selectedScore = finalReportData.dailyScores[selectedDate]
    val selectedLatency = finalReportData.dailySleepLatencyMinutes[selectedDate]
    val noRecordText = stringResource(Res.string.report_no_record)
    val pointUnit = stringResource(Res.string.report_unit_point)
    val minuteUnit = stringResource(Res.string.report_unit_minute)

    Column(modifier = Modifier.fillMaxWidth()) {
        // 예전에는 그래프 위에 고른 날짜의 값만 가운데 떠 있어서, 세 그래프가 각각 무엇을
        // 그린 것인지 알 수 없었다("오늘" 탭에는 GraphHeader 로 제목이 붙어 있다).
        // 같은 헤더를 재사용해 왼쪽은 제목, 오른쪽은 값으로 둔다.
        val valueText = when (selectedMetric) {
            ReportMetric.SLEEP_DURATION -> {
                if (selectedBedTime != null && selectedWakeTime != null) {
                    "${selectedBedTime.to24TimeString()} ~ ${selectedWakeTime.to24TimeString()}"
                } else noRecordText
            }

            ReportMetric.SLEEP_SCORE -> selectedScore?.let { "${it}$pointUnit" } ?: noRecordText
            ReportMetric.SLEEP_LATENCY -> selectedLatency?.let { "${it.toInt()}$minuteUnit" } ?: noRecordText
            else -> noRecordText
        }
        // 맨 위 y축 라벨이 Canvas 꼭대기에 중심을 두고 그려져서, 제목 바로 아래에 붙는다.
        // 캔버스 안에서 띄우면 모든 눈금 간격이 달라지므로 바깥에서 한 칸 띄운다.
        GraphHeader(
            title = stringResource(selectedMetric.labelRes),
            trailingContent = {
                Text(
                    text = valueText,
                    style = labelStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Black),
                    color = primary
                )
            }
        )
        Spacer(Modifier.height(10.dp))

        WeeklyBarChart(
            xLabels = xLabels,
            yLabels = yLabels,
            selectedDate = selectedDate,
            onDateSelected = { date -> selectedDate = date },
            textMeasurer = textMeasurer,
            labelStyle = labelStyle,
            calculateY = { chartHeight, date, isBedTime ->
                when (selectedMetric) {
                    ReportMetric.SLEEP_DURATION -> {
                        if (isBedTime != null) {
                            val targetTime = if (isBedTime) finalReportData.dailyBedTimes[date]
                            else finalReportData.dailyWakeTimes[date]
                            targetTime?.let {
                                timeToY(
                                    chartHeight,
                                    it,
                                    currentBedTimes + currentWakeTimes
                                )
                            }
                        } else null
                    }

                    ReportMetric.SLEEP_SCORE -> {
                        if (isBedTime == null) {
                            finalReportData.dailyScores[date]?.let { scoreToY(chartHeight, it) }
                        } else null
                    }

                    ReportMetric.SLEEP_LATENCY -> {
                        if (isBedTime == null) {
                            finalReportData.dailySleepLatencyMinutes[date]?.let { latency ->
                                val rangePair =
                                    ChartUtil.calculateLatencyMinutesRange(currentLatencyMinutes)
                                val minB = rangePair.first
                                val maxB = rangePair.second
                                val range = (maxB - minB).coerceAtLeast(1)
                                val ratio = (latency.toInt() - minB).toFloat() / range.toFloat()
                                chartHeight * (1f - ratio)
                            }
                        } else null
                    }

                    else -> null
                }
            }
        )
    }
}

/** 요일별 수면 시간/점수를 막대 그래프로 비교해 보여준다. */
@Composable
private fun WeeklyBarChart(
    xLabels: List<ChartDataEntity>,
    yLabels: List<String>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    calculateY: ((chartHeight: Float, date: LocalDate, isBedTime: Boolean?) -> Float?)? = null,
) {
    val primaryColor = primary
    val secondaryColor = secondary
    val highlightColor = MaterialTheme.colorScheme.tertiary

    var stepXState by remember { mutableFloatStateOf(0f) }
    var chartStartXState by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp + X_AXIS_PADDING)
            .pointerInput(xLabels, stepXState, chartStartXState) {
                detectTapGestures { offset ->
                    if (stepXState <= 0f || xLabels.isEmpty()) return@detectTapGestures
                    // 탭 위치에서 가장 가까운 날짜의 인덱스를 계산
                    val rawIndex = ((offset.x - chartStartXState) / stepXState).roundToInt()
                    val clampedIndex = rawIndex.coerceIn(0, xLabels.lastIndex)
                    onDateSelected(xLabels[clampedIndex].date)
                }
            }
    ) {
        val chartHeight = size.height - X_AXIS_PADDING.toPx()
        val stepY = chartHeight / (yLabels.size - 1).coerceAtLeast(1)

        val chartStartX = LABEL_WIDTH.toPx() + Y_AXIS_PADDING.toPx()
        val chartEndX = size.width - Y_AXIS_PADDING.toPx()
        val gridLineWidth = chartEndX - chartStartX
        val denominator = (xLabels.size - 1).coerceAtLeast(1)
        val stepX = gridLineWidth / denominator

        // 제스처 핸들러가 참조할 값 갱신
        stepXState = stepX
        chartStartXState = chartStartX

        yLabels.forEachIndexed { i, label ->
            val y = chartHeight - i * stepY
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(
                measured,
                topLeft = Offset(
                    x = chartStartX - measured.size.width - Y_AXIS_PADDING.toPx(),
                    y = y - measured.size.height / 2f
                )
            )
            drawLine(
                color = Color.White,
                start = Offset(chartStartX, y),
                end = Offset(chartEndX, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        var totalBedTimeY = 0f
        var bedTimeCount = 0
        var totalWakeTimeY = 0f
        var wakeTimeCount = 0

        xLabels.forEachIndexed { i, entity ->
            val x = chartStartX + (i * stepX)
            val isSelected = entity.date == selectedDate

            val labelMeasured = textMeasurer.measure(
                entity.labelText,
                if (isSelected) labelStyle.copy(fontWeight = FontWeight.Bold) else labelStyle
            )
            drawText(
                labelMeasured,
                topLeft = Offset(
                    x = x - labelMeasured.size.width / 2f,
                    chartHeight + 4.dp.toPx()
                )
            )

            // 선택된 날짜 컬럼에 은은한 배경 하이라이트 (터치 피드백 — 캔버스 말풍선 아님)
            if (isSelected) {
                drawRect(
                    color = highlightColor.copy(alpha = 0.12f),
                    topLeft = Offset(x - stepX / 2f, 0f),
                    size = Size(stepX, chartHeight)
                )
            }

            calculateY?.let { getY ->
                val bedTimeY = getY(chartHeight, entity.date, true)
                val wakeTimeY = getY(chartHeight, entity.date, false)
                if (bedTimeY != null && wakeTimeY != null) {
                    val topY = minOf(bedTimeY, wakeTimeY)
                    val bottomY = maxOf(bedTimeY, wakeTimeY)
                    val barHeight = (bottomY - topY).coerceAtLeast(1f)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(secondaryColor, primaryColor),
                            startY = wakeTimeY,
                            endY = bedTimeY
                        ),
                        topLeft = Offset(x - BAR_WIDTH.toPx() / 2f, topY),
                        size = Size(BAR_WIDTH.toPx(), barHeight),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        alpha = if (isSelected) 1f else 0.85f
                    )
                    totalBedTimeY += bedTimeY
                    bedTimeCount++
                    totalWakeTimeY += wakeTimeY
                    wakeTimeCount++
                }

                val scoreY = getY(chartHeight, entity.date, null)
                if (scoreY != null) {
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(x - BAR_WIDTH.toPx() / 2f, scoreY),
                        size = Size(BAR_WIDTH.toPx(), chartHeight - scoreY),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        alpha = if (isSelected) 1f else 0.85f
                    )
                }
            }
        }

        if (bedTimeCount > 0 && wakeTimeCount > 0) {
            val finalAverageBedY = totalBedTimeY / bedTimeCount
            val finalAverageWakeY = totalWakeTimeY / wakeTimeCount
            drawLine(
                color = primaryColor,
                start = Offset(chartStartX, finalAverageBedY),
                end = Offset(chartEndX, finalAverageBedY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
            drawLine(
                color = secondaryColor,
                start = Offset(chartStartX, finalAverageWakeY),
                end = Offset(chartEndX, finalAverageWakeY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
        }
    }
}

/** startDate가 속한 주의 7일치 요일 라벨(차트 x축 등에 쓰임)을 만든다. */
fun buildCalendarLabels(
    startDate: LocalDate
): List<ChartDataEntity> {
    return (0 until 7).map { i ->
        val date = startDate.plus(DatePeriod(days = i))
        ChartWeekDay(
            date.dayOfWeek.isoDayNumber,
            date,
            formatDateLabel(date)
        )
    }
}

/**
 * 캘린더가 펼쳐져 있으면(월간) 그 달의 1일~말일을, 접혀 있으면(주간) targetDate가 속한
 * 월요일~일요일 범위를 반환한다. 캘린더에 표시할 날짜 범위를 결정하는 함수.
 */
private fun getTargetRange(targetDate: LocalDate, isExpanded: Boolean): Pair<LocalDate, LocalDate> {
    return if (isExpanded) {
        val start = LocalDate(targetDate.year, targetDate.month, 1)
        val end = start.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        Pair(start, end)
    } else {
        val dayOfWeekNum = targetDate.dayOfWeek.isoDayNumber
        val start = targetDate.minus((dayOfWeekNum - 1).toLong(), DateTimeUnit.DAY)
        val end = start.plus(6, DateTimeUnit.DAY)
        Pair(start, end)
    }
}

// ── 소음 그래프 아래 프리미엄 섹션 ──────────────────────────────

/**
 * 코골이 분석.
 *
 * ⚠ 현재 코골이를 판정할 데이터가 앱 어디에도 없다. 소음은 크기(dB) 하나뿐이고 타임스탬프도
 * 주파수 정보도 없어서, 지금 있는 것만으로 "코골이 몇 회"를 만들어내면 근거 없는 숫자가 된다.
 * ml 파이프라인에 코골이 신호가 추가되기 전까지는 준비 중임을 알리는 데서 멈춘다.
 */
/** 코골이 분석 섹션(현재 플레이스홀더/안내 영역). */
@Composable
private fun SnoringAnalysisSection() {
    ReportInfoCard(title = stringResource(Res.string.report_snoring_analysis_title)) {
        Text(
            text = stringResource(Res.string.report_snoring_analysis_body),
            style = MaterialTheme.typography.caption,
            color = Color.White
        )
    }
}

/**
 * AI 수면 개선 조언.
 *
 * 지금은 규칙 엔진 결과를 그대로 보여준다. 백엔드 LLM 연동(문장 다듬기)이 붙으면 그 결과로
 * 교체하되, 호출이 실패하면 지금과 같은 이 문구로 되돌아온다. 조언 영역이 통째로 사라지는
 * 일은 없어야 한다.
 */
/** AI 수면 조언 섹션. [ReportViewModel.loadSleepAdvice]가 갱신하는 Idle/Loading/Fallback/Loaded 상태를 각각 다르게 그린다. */
@Composable
private fun SleepAdviceSection(adviceState: ReportContract.SleepAdviceUiState) {
    ReportInfoCard(title = stringResource(Res.string.report_ai_advice_title)) {
        val text = when (adviceState) {
            is ReportContract.SleepAdviceUiState.Loaded -> adviceState.text
            is ReportContract.SleepAdviceUiState.Fallback -> adviceState.text
            // 규칙 판정은 기기에서 즉시 끝나므로 Loading 이 화면에 오래 머무를 일은 없다.
            ReportContract.SleepAdviceUiState.Loading,
            ReportContract.SleepAdviceUiState.Idle -> stringResource(Res.string.report_ai_advice_loading)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.caption,
            color = Color.White
        )
        Text(
            text = stringResource(Res.string.report_ai_advice_disclaimer),
            style = MaterialTheme.typography.caption,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

/** 비교에 의미가 생기려면 최소 이만큼은 쌓여 있어야 한다. */
private const val MIN_COMPARISON_SESSIONS = 3

/**
 * 나의 최근 평균과 오늘을 비교한다.
 *
 * 항목 이름은 "평균 사용자와 비교"였지만 실제로는 본인의 과거 평균과 견준다(결정 사항).
 * 화면 문구도 그에 맞춰 "나의 최근 수면과 비교"로 적어 사용자를 오해시키지 않는다.
 */
/** "나의 최근 수면과 비교" 섹션 — 오늘 수치를 최근 N일 평균과 나란히 보여준다. */
@Composable
private fun SelfComparisonSection(
    today: ReportContract.ReportData,
    recentAverage: ReportContract.ReportData?,
    sampleCount: Int,
) {
    ReportInfoCard(title = stringResource(Res.string.report_self_comparison_title)) {
        if (recentAverage == null || sampleCount < MIN_COMPARISON_SESSIONS) {
            Text(
                text = stringResource(Res.string.report_self_comparison_insufficient, MIN_COMPARISON_SESSIONS),
                style = MaterialTheme.typography.caption,
                color = Color.White
            )
            return@ReportInfoCard
        }

        val hourUnit = stringResource(Res.string.report_unit_hour)
        val minuteUnit = stringResource(Res.string.report_unit_minute)
        val pointUnit = stringResource(Res.string.report_unit_point)

        // 평균 계산에 쓸 기록이 없으면 각 값은 null 이다. 그런 항목은 아예 그리지 않는다.
        ComparisonRow(
            label = stringResource(Res.string.report_metric_sleep_duration),
            todayValue = today.sleepMinutes,
            averageValue = recentAverage.averageSleepMinutes?.toDouble(),
            format = { "${(it / 60).toInt()}$hourUnit ${(it % 60).toInt()}$minuteUnit" }
        )
        ComparisonRow(
            label = stringResource(Res.string.report_metric_sleep_score),
            todayValue = today.sleepScore.toDouble(),
            averageValue = recentAverage.averageScore?.toDouble(),
            format = { "${it.toInt()}$pointUnit" }
        )
        ComparisonRow(
            label = stringResource(Res.string.report_metric_sleep_latency),
            todayValue = today.sleepLatencyMinutes,
            averageValue = recentAverage.averageLatencyMinutes,
            format = { "${it.toInt()}$minuteUnit" }
        )
    }
}

/** 비교 섹션에서 지표 한 줄(오늘 값 vs 평균 값)을 그린다. */
@Composable
private fun ComparisonRow(
    label: String,
    todayValue: Double,
    averageValue: Double?,
    format: (Double) -> String,
) {
    if (averageValue == null) return
    val diff = todayValue - averageValue
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = Color.White
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = format(todayValue),
                style = MaterialTheme.typography.bodyHighlight,
                color = Color.White
            )
            Text(
                text = if (diff >= 0) "▲ ${format(diff)}" else "▼ ${format(-diff)}",
                style = MaterialTheme.typography.caption,
                color = Color.White
            )
        }
    }
}

/** 프리미엄 섹션들이 공유하는 카드 껍데기. */
/** 리포트 수치가 어떻게 계산되는지 설명하는 안내 카드. */
@Composable
private fun ReportInfoCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = SleepTheme.background,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyHighlight,
                color = Color.White
            )
            content()
        }
    }
}

/**
 * 소음 그래프 아래 프리미엄 영역 묶음.
 *
 * 프리미엄이 아니면 각 영역을 [GatedReportSection] 잠금 카드로 대체한다. 잠금 카드는
 * 예전부터 정의만 되어 있고 아무 데서도 쓰이지 않던 컴포저블인데, 정확히 이 용도로
 * 만들어 둔 것으로 보여 그대로 살려 쓴다.
 */
@Composable
internal fun PremiumReportSections(
    reportData: ReportContract.ReportData?,
    recentAverage: ReportContract.ReportData?,
    recentSampleCount: Int,
    adviceState: ReportContract.SleepAdviceUiState,
    isUserPremium: Boolean,
    onUpgradeClicked: () -> Unit,
) {
    if (reportData == null) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isUserPremium) {
            SnoringAnalysisSection()
            SleepAdviceSection(adviceState)
            SelfComparisonSection(reportData, recentAverage, recentSampleCount)
        } else {
            GatedReportSection(title = stringResource(Res.string.report_snoring_analysis_title), onUpgradeClicked = onUpgradeClicked)
            GatedReportSection(title = stringResource(Res.string.report_ai_advice_title), onUpgradeClicked = onUpgradeClicked)
            GatedReportSection(title = stringResource(Res.string.report_self_comparison_title), onUpgradeClicked = onUpgradeClicked)
        }
    }
}
