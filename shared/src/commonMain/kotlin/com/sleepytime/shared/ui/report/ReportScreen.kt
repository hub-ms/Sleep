package com.sleepytime.shared.ui.report

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepStage
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.enum_.MetricType
import com.sleepytime.shared.enum_.ReportTab
import com.sleepytime.shared.enum_.SleepScoreLevel
import com.sleepytime.shared.enum_.SleepStageType
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_calendar
import com.sleepytime.shared.resources.ic_caret_left
import com.sleepytime.shared.resources.ic_caret_right
import com.sleepytime.shared.resources.report_analysis_deep_duration
import com.sleepytime.shared.resources.report_analysis_rem_duration
import com.sleepytime.shared.resources.report_analysis_sleep_continuity
import com.sleepytime.shared.resources.report_analysis_sleep_latency
import com.sleepytime.shared.resources.report_analysis_wake_count
import com.sleepytime.shared.ui.auth.AuthContract
import com.sleepytime.shared.ui.component.ChartLegend
import com.sleepytime.shared.ui.component.EnvironmentDataRow
import com.sleepytime.shared.ui.component.EnvironmentDisplayMode
import com.sleepytime.shared.ui.theme.SleepAppTheme
import com.sleepytime.shared.ui.theme.bodyHighlight
import com.sleepytime.shared.ui.theme.bodyText
import com.sleepytime.shared.ui.theme.caption
import com.sleepytime.shared.ui.theme.sectionTitle
import com.sleepytime.shared.ui.tracking.TrackingContract
import com.sleepytime.shared.util.DateTimeUtil.formatCalendarMonth
import com.sleepytime.shared.util.DateTimeUtil.formatDate
import com.sleepytime.shared.util.DateTimeUtil.formatDateLabel
import com.sleepytime.shared.util.DateTimeUtil.formatSleepDuration
import com.sleepytime.shared.util.DateTimeUtil.formatSleepDurationFromMillis
import com.sleepytime.shared.util.DateTimeUtil.formatWeekDayLabel
import com.sleepytime.shared.util.DateTimeUtil.to24TimeString
import com.sleepytime.shared.util.toSleepScoreStatus
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock.System
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.apply
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

data class SleepScoreStatus(
    val statusText: String,
    val primaryColor: Color,
    val recommendation: String,
    val level: SleepScoreLevel
)

data class LegendItem(
    val label: String,
    val color: Color,
)

//data class SleepMetric(
//    val label: String,
//    val score: Float,
//    val color: Color,
//    val dimColor: Color,
//)

data class CalendarRange(
    val startDate: LocalDate, val totalDays: Int
)

val stageTypes = listOf(
    SleepStageType.AWAKE,
    SleepStageType.LIGHT,
    SleepStageType.REM,
    SleepStageType.DEEP
)

val sleepStageColors = mapOf(
    stageTypes[0] to SleepStageColors.AWAKE,
    stageTypes[1] to SleepStageColors.LIGHT,
    stageTypes[2] to SleepStageColors.REM,
    stageTypes[3] to SleepStageColors.DEEP,
)
val WEEKLY_BAR_WIDTH = 12.dp
val MONTHLY_BAR_WIDTH = 4.dp
val Y_AXIS_WIDTH = 48.dp
val X_AXIS_PADDING = 32.dp
val Y_AXIS_PADDING = 8.dp
val LABEL_PADDING = 4.dp

val SleepStageType.stageName: String
    get() = when (this) {
        SleepStageType.AWAKE -> "깨어남"
        SleepStageType.LIGHT -> "얕은 수면"
        SleepStageType.REM -> "렘수면"
        SleepStageType.DEEP -> "깊은 수면"
    }
val MetricType.labelRes: StringResource
    get() = when(this) {
        MetricType.WAKE_COUNT -> Res.string.report_analysis_wake_count
        MetricType.CONTINUITY -> Res.string.report_analysis_sleep_continuity
        MetricType.DEEP_SLEEP -> Res.string.report_analysis_deep_duration
        MetricType.REM_SLEEP -> Res.string.report_analysis_rem_duration
        MetricType.LATENCY -> Res.string.report_analysis_sleep_latency
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

data class ChartDate(
    val isoDayNumber: Int,
    override val date: LocalDate,
    override val labelText: String
) : ChartDataEntity


@Composable
fun rememberSleepTimeStyles(): Pair<SpanStyle, SpanStyle> {
    val sectionStyle = MaterialTheme.typography.sectionTitle.toSpanStyle()
    val bodyStyle = MaterialTheme.typography.caption.toSpanStyle()
    return Pair(sectionStyle, bodyStyle)
}

fun String.toAnnotatedString(
    baseSectionStyle: SpanStyle, baseBodyStyle: SpanStyle
): AnnotatedString {
    val text = this
    return buildAnnotatedString {
        withStyle(style = baseSectionStyle) { append(text) }
        val labels = listOf("시간", "분", "점")
        labels.forEach { label ->
            var index = text.indexOf(label)
            while (index != -1) {
                addStyle(style = baseBodyStyle, start = index, end = index + label.length)
                index = text.indexOf(label, index + 1)
            }
        }
    }
}

@Composable
fun ReportContent(
    user: User?,
    authState: AuthContract.State,
    trackingState: TrackingContract.State,
    reportState: ReportContract.State,
    sleepStageHistory: List<SleepStage>,
    isCalendarShow: Boolean,
    onCalendarToggleClicked: (Boolean) -> Unit,
    onReportModeSelected: (ReportTab) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onNavigateToSleepEnvironment: (List<EnvironmentFeature.Snapshot>, Float, Float, Float, Float) -> Unit,
    onPrevMonthClicked: (LocalDate) -> Unit,
    onNextMonthClicked: (LocalDate) -> Unit
) {
    var activeTooltipDate by remember { mutableStateOf<LocalDate?>(null) }
    val (baseSectionStyle, baseBodyStyle) = rememberSleepTimeStyles()

    val scrollState = rememberScrollState()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
    val calendarRange =
        remember(reportState.date, reportState.selectedTab) { getCalendarRange(reportState) }
    val isWeekly = reportState.selectedTab == ReportTab.WEEKLY
    val xLabels = remember(reportState.date, reportState.selectedTab) {
        buildCalendarLabels(
            startDate = calendarRange.startDate,
            totalDays = calendarRange.totalDays,
            isWeekly = isWeekly
        )
    }
    val today = System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date


    Box(modifier = Modifier.fillMaxSize()) {
        var selectedDate by remember { mutableStateOf(reportState.date) }

        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState).padding(16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ReportHeader(
                reportState = reportState,
                today = today,
                isCalendarShow = isCalendarShow,
                onCalendarShowChanged = {
                    onCalendarToggleClicked(it)
                }
            )
            MainTabRow(
                menus = listOf(ReportTab.DAILY, ReportTab.WEEKLY, ReportTab.MONTHLY),
                selectedIndex = reportState.selectedTab.ordinal,
                onReportModeSelected = onReportModeSelected
            )
            Calendar(
                reportState = reportState,
                today = today,
                isCalendarShow = isCalendarShow,
                selectedDate = selectedDate,
                onDateSelected = { date ->
                    onDateSelected(date)
                    activeTooltipDate = date
                    selectedDate = date
                },
                sessionDates = reportState.sessionDates,
                onPrevMonthClicked = { onPrevMonthClicked(reportState.date) },
                onNextMonthClicked = { onNextMonthClicked(reportState.date) },
            )
            when (reportState.selectedTab) {
                ReportTab.DAILY -> DailyContent(
                    trackingState = trackingState,
                    reportState = reportState,
                    labelStyle = labelStyle,
                    activeTooltipDate = activeTooltipDate,
                    baseSectionStyle = baseSectionStyle,
                    baseBodyStyle = baseBodyStyle,
                    onNavigateToSleepEnvironment = onNavigateToSleepEnvironment
                )
                ReportTab.WEEKLY -> WeeklyContent(
                    trackingState = trackingState,
                    reportState = reportState,
                    xLabels = xLabels,
                    activeTooltipDate = activeTooltipDate,
                    textMeasurer = textMeasurer,
                    baseSectionStyle = baseSectionStyle,
                    baseBodyStyle = baseBodyStyle,
                    labelStyle = labelStyle,
                    onReportModeSelected = onReportModeSelected
                )

                else -> MonthlyContent(
                    trackingState = trackingState,
                    reportState = reportState,
                    xLabels = xLabels,
                    activeTooltipDate = activeTooltipDate,
                    textMeasurer = textMeasurer,
                    baseSectionStyle = baseSectionStyle,
                    baseBodyStyle = baseBodyStyle,
                    labelStyle = labelStyle,
                    onReportModeSelected = onReportModeSelected
                )
            }
        }
//        if (reportState.isPreview) {
//            PreviewOverlay()
//        }
    }
}

@Composable
fun PreviewOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .blur(0.dp) // 내부 텍스트는 번지지 않도록 Box 레벨에서 처리 분리 가이드
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "예시 리포트입니다",
                style = MaterialTheme.typography.bodyHighlight,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "홈 화면에서 수면 시작 버튼을 눌러\n실제 수면 측정을 해보세요",
                style = MaterialTheme.typography.caption,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun Calendar(
    reportState: ReportContract.State,
    today: LocalDate,
    isCalendarShow: Boolean,
    selectedDate: LocalDate,
    sessionDates: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onPrevMonthClicked: () -> Unit,
    onNextMonthClicked: () -> Unit
) {
    var activeTooltipDate by remember { mutableStateOf<LocalDate?>(null) }



    val firstDayOfMonth = LocalDate(selectedDate.year, selectedDate.month, 1)
    val firstDayOfWeekNum = firstDayOfMonth.dayOfWeek.isoDayNumber
    val calendarStartDate =
        firstDayOfMonth.minus((firstDayOfWeekNum - 1).toLong(), DateTimeUnit.DAY)
    val monthDates = (0 until 35).map { calendarStartDate.plus(it.toLong(), DateTimeUnit.DAY) }

    if (isCalendarShow) {
        val weeks = monthDates.chunked(7)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    modifier = Modifier.size(36.dp),
                    onClick = { onPrevMonthClicked() }
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(Res.drawable.ic_caret_left),
                        contentDescription = "이전 달로 이동",
                        tint = Color.White
                    )
                }
                Text(
                    text = formatCalendarMonth(reportState.date),
                    style = MaterialTheme.typography.bodyText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    modifier = Modifier.size(36.dp),
                    onClick = { onNextMonthClicked() }
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(Res.drawable.ic_caret_right),
                        contentDescription = "다음 달로 이동",
                        tint = Color.White
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("월", "화", "수", "목", "금", "토", "일").forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.bodyText,
                            color = Color.White
                        )
                    }
                }
            }
            weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    week.forEach { date ->
                        CalendarDayCell(
                            modifier = Modifier
                                .height(60.dp)
                                .weight(1f),
                            date = date,
                            selectedDate = selectedDate,
                            today = today,
                            reportState = reportState,
                            sessionDates = sessionDates,
                            isCurrentMonth = (date.month == selectedDate.month),
                            onDateSelected = onDateSelected
                        )
                    }
                }
            }
        }
    } /*else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "아래 화살표를 눌러보세요.",
                style = MaterialTheme.typography.caption,
                color = Color.White
            )
            IconButton(
                modifier = Modifier.size(36.dp),
                onClick = { onCalendarExpandChanged(true) }
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(Res.drawable.ic_caret_down),
                    contentDescription = "달력 확장",
                    tint = Color.White
                )
            }
        }
    }*/
}

@Composable
fun CalendarDayCell(
    modifier: Modifier = Modifier,
    date: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    reportState: ReportContract.State,
    sessionDates: Set<LocalDate>,
    isCurrentMonth: Boolean = true,
    onDateSelected: (LocalDate) -> Unit
) {
    val isSelected = date == selectedDate
    val isToday = date == today
    val hasSession = reportState.sessionDates.contains(date)
    val showDot = reportState.isPreview || hasSession

    val currentDayScore = if (reportState.isPreview) reportState.reportData.dailyScores[date] ?: 0 else reportState.reportData.sleepScore
    val dotColor = if (reportState.isPreview) {
        currentDayScore.toSleepScoreStatus().primaryColor
    } else {
        if (hasSession) currentDayScore.toSleepScoreStatus().primaryColor
        else Color.Transparent
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(
                    if (isSelected) Modifier.border(
                        2.dp, MaterialTheme.colorScheme.primary, CircleShape
                    ) else Modifier
                )
                .background(
                    color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = CircleShape
                )
                .clickable(
                    enabled = reportState.isPreview || hasSession,
                    onClick = { onDateSelected(date) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrentMonth || hasSession) Color.White else Color.White.copy(0.2f)
            )
        }
        if (showDot) {
            Box(modifier = Modifier.size(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = dotColor, shape = CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ReportHeader(
    reportState: ReportContract.State,
    today: LocalDate,
    isCalendarShow: Boolean,
    onCalendarShowChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatDate(today),
            style = MaterialTheme.typography.bodyText,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .clickable { onCalendarShowChanged(!isCalendarShow) }
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(36.dp),
                painter = painterResource(Res.drawable.ic_calendar),
                contentDescription = "날짜 선택 화면으로 이동",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun DailyContent(
    trackingState: TrackingContract.State,
    reportState: ReportContract.State,
    labelStyle: TextStyle,
    activeTooltipDate: LocalDate?,
    baseSectionStyle: SpanStyle,
    baseBodyStyle: SpanStyle,
    onNavigateToSleepEnvironment: (List<EnvironmentFeature.Snapshot>, Float, Float, Float, Float) -> Unit
) {
    var selectedStage by remember { mutableStateOf<SleepStageType?>(null) }
    val finalReportData = reportState.reportData
    val targetDate = activeTooltipDate ?: reportState.date

    val hasData = if (reportState.isPreview) {
        finalReportData.dailyScores.containsKey(targetDate)
    } else {
        true
    }
    if (!hasData) {
        NoSleepDataForDatePlaceholder()
        return
    }


    val latencyMinutes = if (reportState.isPreview) finalReportData.dailyLatencyMinutes[targetDate]?.toLong()
    else finalReportData.sleepLatencyMinutes.toLong()

    val bedTime = if (reportState.isPreview) finalReportData.dailyBedTimes[targetDate]
    else finalReportData.bedTime

    val wakeTime = if (reportState.isPreview) finalReportData.dailyWakeTimes[targetDate]
    else finalReportData.wakeTime

    val rawScore = if (reportState.isPreview) finalReportData.dailyScores[targetDate] else finalReportData.sleepScore

    val sleepStageItems = listOf(
        Triple(
            SleepStageType.AWAKE,
            finalReportData.awakeMinutes.roundToLong(),
            sleepStageColors[SleepStageType.AWAKE]!!
        ),
        Triple(
            SleepStageType.LIGHT,
            finalReportData.lightMinutes.roundToLong(),
            sleepStageColors[SleepStageType.LIGHT]!!
        ),
        Triple(
            SleepStageType.DEEP,
            finalReportData.deepMinutes.roundToLong(),
            sleepStageColors[SleepStageType.DEEP]!!
        ),
        Triple(
            SleepStageType.REM,
            finalReportData.remMinutes.roundToLong(),
            sleepStageColors[SleepStageType.REM]!!
        )
    )
    Napier.d("""
            awake raw: ${finalReportData.awakeMinutes}
            light raw: ${finalReportData.lightMinutes}
            deep raw:  ${finalReportData.deepMinutes}
            rem raw:   ${finalReportData.remMinutes}
        """.trimIndent()
    )
    Napier.d("sleepStageItems:$sleepStageItems")

    val sleepDurationAnnotatedText =
        formatSleepDuration(bedTime, wakeTime).toAnnotatedString(
            baseSectionStyle.copy(color = Color.White), baseBodyStyle.copy(color = Color.White)
        )
    val sleepLatencyAnnotatedText =
        formatSleepDurationFromMillis(latencyMinutes).toAnnotatedString(
            baseSectionStyle.copy(color = Color.White), baseBodyStyle.copy(color = Color.White)
        )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard(
                    modifier = Modifier.fillMaxWidth(),
                    value = sleepLatencyAnnotatedText,
                    label = "잠들기까지"
                )
                SummaryCard(
                    modifier = Modifier.fillMaxWidth(),
                    value = sleepDurationAnnotatedText,
                    label = "총 수면 시간"
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                ScoreDonutChart(
                    rawScore = rawScore,
                    baseSectionStyle = baseSectionStyle,
                    baseBodyStyle = baseBodyStyle,
                )
            }
        }
        StageBarChart(
            sleepStageItems = sleepStageItems,
            selectedStage = selectedStage,
            onSelected = { stage ->
                selectedStage = if (selectedStage == stage) null else stage
            }
        )
        ChartLegend(
            items = listOf(
                LegendItem(
                    label = stageTypes[0].stageName,
                    color = SleepStageColors.AWAKE
                ),
                LegendItem(
                    label = stageTypes[1].stageName,
                    color = SleepStageColors.LIGHT
                ),
                LegendItem(
                    label = stageTypes[2].stageName,
                    color = SleepStageColors.REM
                ),
                LegendItem(
                    label = stageTypes[3].stageName,
                    color = SleepStageColors.DEEP
                )
            ),
            selectedIndex = stageTypes.indexOf(selectedStage),
            onSelected = { index ->
                val clickedStage = stageTypes[index]
                selectedStage = if (selectedStage == clickedStage) null else clickedStage
            }
        )
        GraphCard(
            reportState = reportState,
            labelStyle = labelStyle,
            startTime = bedTime,
            endTime = wakeTime,
            activeTooltipDate = activeTooltipDate
        )
        EnvironmentInsightCard(
            reportState = reportState,
            onNavigateToSleepEnvironment = onNavigateToSleepEnvironment
        )
    }
}

@Composable
fun MainTabRow(
    menus: List<ReportTab>,
    selectedIndex: Int,
    onReportModeSelected: (ReportTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        menus.forEachIndexed { index, menu ->
            val isSelected = index == selectedIndex
            val menuName = when (menu) {
                ReportTab.DAILY -> "1일"
                ReportTab.WEEKLY -> "1주일"
                ReportTab.MONTHLY -> "1개월"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onReportModeSelected(menu) }
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        RoundedCornerShape(50.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = menuName,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyHighlight.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun ScoreDonutChart(
    rawScore: Int?,
    baseSectionStyle: SpanStyle,
    baseBodyStyle: SpanStyle,
) {
    val scoreGraphColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.size(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val arcSize = Size(size.width * 0.8f, size.height * 0.8f)
            val topLeftOffset = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)

            val scoreFloat = rawScore?.toFloat() ?: 0f
            val sweepAngle = (scoreFloat / 100f) * 360f
            val strokeWidth = 16.dp.toPx()

            val canvasCenterX = size.width / 2f
            val canvasCenterY = size.height / 2f

            drawCircle(
                color = Color.White.copy(0.2f),
                radius = arcSize.width / 2f,
                center = Offset(canvasCenterX, canvasCenterY),
                style = Stroke(width = strokeWidth)
            )
            drawArc(
                color = scoreGraphColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeftOffset,
                size = arcSize,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "수면 점수",
                style = MaterialTheme.typography.caption,
                color = Color.White
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(style = baseSectionStyle.copy(color = Color.White)) {
                        append(
                            (rawScore ?: 0).toString()
                        )
                    }
                    withStyle(style = baseBodyStyle.copy(color = Color.White)) { append("/100") }
                }
            )
//                if (selectedIndex == -1) {
//                    Text(
//                        text = "수면시간",
//                        style = MaterialTheme.typography.caption,
//                        color = Color.White
//                    )
//                    Text(text = sleepDurationAnnotatedText)
//                } else {
//                    val (baseSectionStyle, baseBodyStyle) = rememberSleepTimeStyles()
//                    val (label, minutes, _) = sleepStageItems[selectedIndex]
//                    Text(
//                        text = label.stageName,
//                        style = MaterialTheme.typography.caption,
//                        color = Color.White
//                    )
//                    Text(
//                        text = formatSleepStageTime(minutes).toAnnotatedString(
//                            baseSectionStyle.copy(
//                                color = Color.White
//                            ), baseBodyStyle.copy(color = Color.White)
//                        )
//                    )
//                }
        }
    }
}
@Composable
fun StageBarChart(
    sleepStageItems: List<Triple<SleepStageType, Long, Color>>,
    selectedStage: SleepStageType?,
    onSelected: (SleepStageType?) -> Unit
) {
    val totalMinutes = sleepStageItems.sumOf { it.second }.coerceAtLeast(1L)
    Napier.d("totalMinutes:$totalMinutes")
    val barHeight = 24.dp

    val animatedOffsets = sleepStageItems.map { (stageType, _, _) ->
        val isSelected = stageType == selectedStage

        animateFloatAsState(
            targetValue = if (isSelected) -30f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "BounceAnimation"
        )
    }

    Canvas(
        modifier = Modifier
            .clickable { onSelected(selectedStage)}
            .fillMaxWidth()
            .height(barHeight * 2)
    ) {
        var curX = 0f
        val baseLineY = barHeight.toPx()
        sleepStageItems.forEachIndexed { i, (_, minutes, color) ->
            val segW = (minutes.toFloat() / totalMinutes.toFloat()) * size.width
            val isFirst = i == 0
            val isLast = i == sleepStageItems.lastIndex

            val yOffset = animatedOffsets[i].value

            val path = Path().apply {
                val x = curX
                val y = baseLineY + yOffset
                val h = barHeight.toPx()
                val r = minOf(h / 2f, segW / 2f)

                if (isFirst) moveTo(x + r, y) else moveTo(x, y)
                if (isLast) {
                    lineTo(x + segW - r, y)
                    arcTo(
                        rect = Rect(x + segW - 2 * r, y, x + segW, y + h),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                } else {
                    lineTo(x + segW, y)
                    lineTo(x + segW, y + h)
                }

                if (isFirst) {
                    lineTo(x + r, y + h)
                    arcTo(
                        rect = Rect(x, y, x + 2 * r, y + h),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                } else {
                    lineTo(x, y + h)
                    lineTo(x, y)
                }
                close()
            }


            drawPath(path = path, color = color)

            curX += segW
        }
    }
}


@Composable
fun GraphCard(
    reportState: ReportContract.State,
    labelStyle: TextStyle,
    startTime: LocalDateTime?,
    endTime: LocalDateTime?,
    activeTooltipDate: LocalDate? = null
) {
    val textMeasurer = rememberTextMeasurer()
    val targetDate = activeTooltipDate ?: reportState.date

    val currentMetrics = remember(reportState.isPreview, targetDate, reportState.reportData) {
        if (reportState.isPreview)  DemoReportFactory.createPreviewData(0L, targetDate).sleepMetrics
        else reportState.reportData.sleepMetrics
    }



    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "그래프로 보는 나의 잠",
                style = MaterialTheme.typography.bodyText.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            SleepRadarChart(
                reportState = reportState,
                targetDate = targetDate,
                sleepMetrics = currentMetrics,
                textMeasurer = textMeasurer,
                labelStyle = labelStyle
            )
            SleepTimeLineChart(
                reportState = reportState,
                startTime = startTime,
                endTime = endTime,
                targetDate = targetDate,
                activeTooltipDate = activeTooltipDate,
                textMeasurer = textMeasurer,
                labelStyle = labelStyle
            )
        }
    }
}

@Composable
fun SleepRadarChart(
    reportState: ReportContract.State,
    targetDate: LocalDate?,
    sleepMetrics: SleepMetrics,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle
) {
    val scoreList = sleepMetrics.scoreList()

    val overallScore = if (reportState.isPreview)
        reportState.reportData.dailyScores[targetDate] ?: 0
    else reportState.reportData.sleepScore

    val animProgress = remember { Animatable(0f) }
    val innerColor = MaterialTheme.colorScheme.primary.copy(0.2f)
    val lineColor = MaterialTheme.colorScheme.primary

    val labelTextList = scoreList.map { (type, _) ->
        stringResource(type.labelRes)
    }
    LaunchedEffect(targetDate, overallScore) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, tween(800, easing = EaseOutCubic))
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(180.dp)
                .padding(16.dp)
        ) {
            val r = size.minDimension / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f

            val metrics = reportState.reportData.sleepMetrics.scoreList()
            val count = metrics.size
            if (count == 0) return@Canvas

            val levels = listOf(0.2f, 0.4f, 0.6f, 0.8f, 1.0f)
            levels.forEach { level ->
                val gridPath = Path()
                val currentR = r * level

                for (i in 0 until count) {
                    val angle = -90f + i * (360f / count)
                    val angleRad = angle * (PI / 180f)
                    val x = (cx + currentR * cos(angleRad)).toFloat()
                    val y = (cy + currentR * sin(angleRad)).toFloat()

                    if (i == 0) gridPath.moveTo(x, y) else gridPath.lineTo(x, y)
                }
                gridPath.close()

                drawPath(
                    path = gridPath,
                    color = Color.White.copy(0.2f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            metrics.forEachIndexed { i, (type, _) ->
                val angle = -90f + i * (360f / count)
                val angleRad = angle * (PI / 180f)

                // 축 선 그리기
                val axisX = (cx + r * cos(angleRad)).toFloat()
                val axisY = (cy + r * sin(angleRad)).toFloat()
                drawLine(
                    color = Color.White.copy(0.2f),
                    start = Offset(cx, cy),
                    end = Offset(axisX, axisY),
                    strokeWidth = 1.dp.toPx()
                )

                val labelText = labelTextList.getOrNull(i) ?: ""
                val textLayoutResult = textMeasurer.measure(labelText, labelStyle)
                val textWidth = textLayoutResult.size.width
                val textHeight = textLayoutResult.size.height

                // 차트 반지름(r)보다 약간 더 바깥쪽에 텍스트 중심점 배치 (8.dp 여백)
                val labelMargin = 8.dp.toPx()
                val labelX = (cx + (r + labelMargin) * cos(angleRad)).toFloat()
                val labelY = (cy + (r + labelMargin) * sin(angleRad)).toFloat()

                // 정렬 보정: 텍스트가 항상 오각형 바깥쪽을 향하도록 Offset 좌표 미세 조정
                val finalLeft = when {
                    cos(angleRad) > 0.1 -> labelX // 우측 지표
                    cos(angleRad) < -0.1 -> labelX - textWidth // 좌측 지표
                    else -> labelX - (textWidth / 2f) // 중앙(상/하) 지표
                }
                val finalTop = when {
                    sin(angleRad) > 0.1 -> labelY // 하단 지표
                    sin(angleRad) < -0.1 -> labelY - textHeight // 상단 지표
                    else -> labelY - (textHeight / 2f)
                }

                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(finalLeft, finalTop)
                )
            }

            val polyPath = Path()
            val points = mutableListOf<Offset>()

            metrics.forEachIndexed { i, (_, score) ->
                val angle = -90f + i * (360f / count)
                val angleRad = angle * PI / 180f

                val currentR = r * (score / 100f) * animProgress.value
                val x = (cx + currentR * cos(angleRad)).toFloat()
                val y = (cy + currentR * sin(angleRad)).toFloat()

                val point = Offset(x, y)
                points.add(point)

                if (i == 0) polyPath.moveTo(x, y) else polyPath.lineTo(x, y)
            }
            if (points.isNotEmpty()) polyPath.close()

            drawPath(
                path = polyPath,
                color = innerColor
            )

            drawPath(
                path = polyPath,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            points.forEach { pt ->
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}
@Composable
fun SleepTimeLineChart(
    reportState: ReportContract.State,
    startTime: LocalDateTime?,
    endTime: LocalDateTime?,
    targetDate: LocalDate?,
    activeTooltipDate: LocalDate?,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle
) {
    val sMeasured = textMeasurer.measure(startTime?.to24TimeString() ?: "--:--", labelStyle)
    val eMeasured = textMeasurer.measure(endTime?.to24TimeString() ?: "--:--", labelStyle)

    val stageTimeLine = if (reportState.isPreview && activeTooltipDate != null) {
        val bedTime  = reportState.reportData.dailyBedTimes[targetDate]
        val wakeTime = reportState.reportData.dailyWakeTimes[targetDate]
        if (bedTime != null && wakeTime != null) {
            reportState.reportData.stageTimeline.filter { stage ->
                stage.startTime in bedTime..<wakeTime
            }
        } else reportState.reportData.stageTimeline
    } else reportState.reportData.stageTimeline

    val totalDurationMillis =
        stageTimeLine.sumOf { it.duration.inWholeMilliseconds }.coerceAtLeast(1L)
    val timeLabelBgColor = MaterialTheme.colorScheme.primary.copy(0.4f)
    Canvas(modifier = Modifier.fillMaxWidth()) {
        val timelineWidth = size.width
        val chartStartX = 50.dp.toPx()
        val chartWidth = timelineWidth - chartStartX
        val rowHeightPx = 24.dp.toPx()

        stageTypes.forEachIndexed { index, type ->
            val measured = textMeasurer.measure(type.stageName, labelStyle)
            val yTop = index * rowHeightPx
            val stageColor = sleepStageColors[type] ?: Color.White
            drawLine(
                color = stageColor.copy(0.2f),
                start = Offset(chartStartX, yTop + rowHeightPx),
                end = Offset(timelineWidth, yTop + rowHeightPx),
                strokeWidth = 1.dp.toPx()
            )
            drawText(
                measured,
                topLeft = Offset(-30f, yTop + (rowHeightPx - measured.size.height) / 2f)
            )
        }
        if (stageTimeLine.isNotEmpty()) {
            var accumulatedTime = 0L
            stageTimeLine.forEach { stage ->
                val rowIndex = stageTypes.indexOf(stage.type).coerceAtLeast(0)
                val xStart = chartStartX + (accumulatedTime.toFloat() / totalDurationMillis) * chartWidth
                val xEnd = chartStartX + ((accumulatedTime + stage.duration.inWholeMilliseconds).toFloat() / totalDurationMillis) * chartWidth
                if (xEnd - xStart > 0f) drawRoundRect(
                    color = sleepStageColors[stage.type] ?: Color.White,
                    topLeft = Offset(xStart, rowIndex * rowHeightPx),
                    size = Size(xEnd - xStart, rowHeightPx),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                accumulatedTime += stage.duration.inWholeMilliseconds
            }
            val bgY = (rowHeightPx * 4) + 4.dp.toPx()
            val startColor =
                stageTimeLine.firstOrNull()?.let { sleepStageColors[it.type] }
            val endColor = stageTimeLine.lastOrNull()?.let { sleepStageColors[it.type] }

            drawRoundRect(
                color = timeLabelBgColor,
                topLeft = Offset(chartStartX, bgY),
                size = Size(
                    sMeasured.size.width + 12.dp.toPx(),
                    sMeasured.size.height + 6.dp.toPx()
                ),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawText(
                sMeasured,
                topLeft = Offset(chartStartX + 6.dp.toPx(), bgY + 3.dp.toPx())
            )
            drawRoundRect(
                color = timeLabelBgColor,
                topLeft = Offset(
                    timelineWidth - eMeasured.size.width - 12.dp.toPx(), bgY
                ),
                size = Size(
                    eMeasured.size.width + 12.dp.toPx(),
                    eMeasured.size.height + 6.dp.toPx()
                ),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawText(
                eMeasured, topLeft = Offset(
                    timelineWidth - eMeasured.size.width - 6.dp.toPx(),
                    bgY + 3.dp.toPx()
                )
            )
        }
    }
}
@Composable
fun EnvironmentInsightCard(
    reportState: ReportContract.State,
    onNavigateToSleepEnvironment: (List<EnvironmentFeature.Snapshot>, Float, Float, Float, Float) -> Unit
) {
    val finalReportData = remember(reportState.isPreview, reportState.date) {
        if (reportState.isPreview) {
            DemoReportFactory.createPreviewData(0L, reportState.date)
        } else {
            reportState.reportData
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onNavigateToSleepEnvironment(
                    finalReportData.environmentHistory,
                    finalReportData.avgHeartRate,
                    finalReportData.avgNoise,
                    finalReportData.avgTemperature,
                    finalReportData.avgHumidity
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "수면 환경 및 인사이트",
                style = MaterialTheme.typography.bodyText.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            EnvironmentDataRow(
                mode = EnvironmentDisplayMode.Report(reportData = finalReportData)
            )
        }
    }
}

@Composable
fun WeeklyContent(
    trackingState: TrackingContract.State,
    reportState: ReportContract.State,
    xLabels: List<ChartDataEntity>,
    activeTooltipDate: LocalDate?,
    textMeasurer: TextMeasurer,
    baseSectionStyle: SpanStyle,
    baseBodyStyle: SpanStyle,
    labelStyle: TextStyle,
    onReportModeSelected: (ReportTab) -> Unit
) {
    ReportSummaryContent(
        trackingState,
        reportState,
        xLabels,
        activeTooltipDate,
        labelStyle,
        textMeasurer,
        baseSectionStyle,
        baseBodyStyle,
        onReportModeSelected
    )
}

@Composable
fun MonthlyContent(
    trackingState: TrackingContract.State,
    reportState: ReportContract.State,
    xLabels: List<ChartDataEntity>,
    activeTooltipDate: LocalDate?,
    textMeasurer: TextMeasurer,
    baseSectionStyle: SpanStyle,
    baseBodyStyle: SpanStyle,
    labelStyle: TextStyle,
    onReportModeSelected: (ReportTab) -> Unit
) {
    ReportSummaryContent(
        trackingState,
        reportState,
        xLabels,
        activeTooltipDate,
        labelStyle,
        textMeasurer,
        baseSectionStyle,
        baseBodyStyle,
        onReportModeSelected
    )
}

@Composable
fun ReportSummaryContent(
    trackingState: TrackingContract.State,
    reportState: ReportContract.State,
    xLabels: List<ChartDataEntity>,
    activeTooltipDate: LocalDate?,
    labelStyle: TextStyle,
    textMeasurer: TextMeasurer,
    baseSectionStyle: SpanStyle,
    baseBodyStyle: SpanStyle,
    onReportModeSelected: (ReportTab) -> Unit
) {
    val data = reportState.reportData
    val avgBedTime = data.averageBedTime ?: trackingState.trackingStartTime
    val avgWakeTime = data.averageWakeTime ?: trackingState.trackingEndTime
    val avgScore = data.averageScore ?: data.sleepScore

    val today = System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val currentDayOfWeek = today.dayOfWeek.isoDayNumber
    today.minus((currentDayOfWeek - 1).toLong(), DateTimeUnit.DAY)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                value = formatSleepDuration(avgBedTime, avgWakeTime).toAnnotatedString(
                    baseSectionStyle.copy(color = Color.White),
                    baseBodyStyle.copy(color = Color.White)
                ),
                label = "평균 수면 시간"
            )
            SummaryCard(
                modifier = Modifier.weight(1f), value = "${avgScore}점".toAnnotatedString(
                    baseSectionStyle.copy(color = Color.White),
                    baseBodyStyle.copy(color = Color.White)
                ), label = "평균 수면 점수"
            )
        }
        TimeChart(reportState, activeTooltipDate, xLabels, textMeasurer, labelStyle)
        ScoreChart(reportState, activeTooltipDate, xLabels, textMeasurer, labelStyle)
    }
}

@Composable
fun SummaryCard(modifier: Modifier = Modifier, value: AnnotatedString, label: String) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.caption,
                color = Color.White
            )
            Text(text = value)
        }
    }
}

@Composable
fun TimeChart(
    reportState: ReportContract.State,
    activeTooltipDate: LocalDate?,
    xLabels: List<ChartDataEntity>,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle
) {
    val timechartColor = MaterialTheme.colorScheme.primary
    val finalReportData = reportState.reportData
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(0.2f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height(300.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "수면 시간",
                style = MaterialTheme.typography.bodyText.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp + X_AXIS_PADDING)
            ) {
                val timeLabels = listOf(
                    "18:00", "21:00", "00:00",
                    "03:00", "06:00", "09:00",
                    "12:00", "15:00", "18:00"
                )
                val chartHeight = size.height - X_AXIS_PADDING.toPx()
                val stepY = chartHeight / (timeLabels.size - 1)

                // 1. Y축 가이드라인 및 라벨 드로잉
                timeLabels.forEachIndexed { i, label ->
                    val y = chartHeight - i * stepY
                    drawLine(
                        color = Color.White.copy(0.4f),
                        start = Offset(Y_AXIS_WIDTH.toPx(), y),
                        end = Offset(size.width, y)
                    )
                    drawText(
                        textMeasurer.measure(label, labelStyle), topLeft = Offset(
                            Y_AXIS_WIDTH.toPx() - textMeasurer.measure(
                                label, labelStyle
                            ).size.width - Y_AXIS_PADDING.toPx(),
                            y - textMeasurer.measure(label, labelStyle).size.height / 2f
                        )
                    )
                }

                fun timeToY(t: LocalDateTime): Float {
                    val mins = if (t.hour >= 18) (t.hour - 18) * 60 + t.minute
                    else (t.hour + 6) * 60 + t.minute
                    return chartHeight * (1 - mins / (24f * 60f))
                }

                val denominator = (xLabels.size - 1).coerceAtLeast(1)
                val stepX = (size.width - Y_AXIS_WIDTH.toPx()) / denominator

                // 2. 반복문 내부: 오직 막대 그래프와 X축 날짜 라벨만 드로잉 (툴팁 로직 제거됨)
                xLabels.forEachIndexed { i, entity ->
                    val x = Y_AXIS_WIDTH.toPx() + (i * stepX)

                    val bedTime = finalReportData.dailyBedTimes[entity.date]
                    val wakeTime = finalReportData.dailyWakeTimes[entity.date]

                    if (bedTime != null && wakeTime != null) {
                        val bY = timeToY(bedTime)
                        val wY = timeToY(wakeTime)
                        val barWidth =
                            if (reportState.selectedTab == ReportTab.WEEKLY) WEEKLY_BAR_WIDTH.toPx() else MONTHLY_BAR_WIDTH.toPx()
                        drawRoundRect(
                            color = timechartColor,
                            topLeft = Offset(x - barWidth / 2f, minOf(bY, wY)),
                            size = Size(barWidth, kotlin.math.abs(bY - wY)),
                            cornerRadius = CornerRadius(16.dp.toPx())
                        )
                    }

                    if (reportState.selectedTab == ReportTab.WEEKLY || i % 5 == 0) {
                        drawText(
                            textMeasurer.measure(entity.labelText, labelStyle), topLeft = Offset(
                                x - textMeasurer.measure(
                                    entity.labelText, labelStyle
                                ).size.width / 2f, chartHeight + 4.dp.toPx()
                            )
                        )
                    }
                }

                // 3. [개선 완료] 반복문 바깥에서 단 한 번만 타겟 날짜를 찾아 툴팁을 최상단에 렌더링
                activeTooltipDate?.let { tooltipDate ->
                    val targetIndex = xLabels.indexOfFirst { it.date == tooltipDate }
                    if (targetIndex != -1) {
                        val bedTime = finalReportData.dailyBedTimes[tooltipDate]
                        val wakeTime = finalReportData.dailyWakeTimes[tooltipDate]

                        if (bedTime != null && wakeTime != null) {
                            val x = Y_AXIS_WIDTH.toPx() + (targetIndex * stepX)
                            val bedY = timeToY(bedTime)
                            val wakeY = timeToY(wakeTime)
                            val topY = minOf(bedY, wakeY)

                            drawTimeTooltip(
                                anchorX = x,
                                anchorY = topY,
                                bedTime = bedTime,
                                wakeTime = wakeTime,
                                textMeasurer = textMeasurer,
                                labelStyle = labelStyle,
                                chartWidth = size.width,
                                chartHeight = size.height
                            )
                        }
                    }
                }
            }
        }
    }
}

fun Float.clamp(min: Float, max: Float): Float = coerceIn(min, max)
private fun DrawScope.drawTimeTooltip(
    anchorX: Float,
    anchorY: Float,
    bedTime: LocalDateTime,
    wakeTime: LocalDateTime,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    chartWidth: Float,
    chartHeight: Float
) {
    val timeRange = "${bedTime.to24TimeString()}~${wakeTime.to24TimeString()}"

    val timeRangeMeasured = textMeasurer.measure(
        timeRange,
        labelStyle.copy(color = Color.Black.copy(0.8f))
    )

    val tooltipWidth = timeRangeMeasured.size.width + (LABEL_PADDING.toPx() * 2)
    val tooltipHeight = timeRangeMeasured.size.height + (LABEL_PADDING.toPx() * 2)

    val tooltipX = (anchorX - tooltipWidth / 2f).clamp(
        LABEL_PADDING.toPx(),
        chartWidth - tooltipWidth - LABEL_PADDING.toPx()
    )
    val tooltipY = (anchorY - tooltipHeight).clamp(
        LABEL_PADDING.toPx(),
        chartHeight - tooltipHeight - LABEL_PADDING.toPx()
    )

    drawRoundRect(
        color = Color.White.copy(0.8f),
        topLeft = Offset(tooltipX, tooltipY),
        size = Size(tooltipWidth, tooltipHeight),
        cornerRadius = CornerRadius(16.dp.toPx())
    )
    drawText(
        textLayoutResult = timeRangeMeasured,
        topLeft = Offset(tooltipX + LABEL_PADDING.toPx(), tooltipY + LABEL_PADDING.toPx())
    )
}

@Composable
private fun ScoreChart(
    reportState: ReportContract.State,
    activeTooltipDate: LocalDate?,
    xLabels: List<ChartDataEntity>,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle
) {
    val finalReportData = reportState.reportData
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(0.2f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height(300.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "수면 점수",
                style = MaterialTheme.typography.bodyText.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Canvas(modifier = Modifier.fillMaxWidth().height(240.dp + X_AXIS_PADDING)) {
                val scoreLabels = listOf("0", "20", "40", "60", "80", "100")
                val chartHeight = size.height - X_AXIS_PADDING.toPx()
                val stepY = chartHeight / (scoreLabels.size - 1)

                // 1. Y축 가이드라인 및 점수 라벨 드로잉
                scoreLabels.forEachIndexed { i, label ->
                    val y = chartHeight - i * stepY
                    drawLine(
                        color = Color.White.copy(0.4f),
                        start = Offset(Y_AXIS_WIDTH.toPx(), y),
                        end = Offset(size.width, y)
                    )
                    drawText(
                        textMeasurer.measure(label, labelStyle), topLeft = Offset(
                            Y_AXIS_WIDTH.toPx() - textMeasurer.measure(
                                label, labelStyle
                            ).size.width - Y_AXIS_PADDING.toPx(),
                            y - textMeasurer.measure(label, labelStyle).size.height / 2f
                        )
                    )
                }

                val denominator = (xLabels.size - 1).coerceAtLeast(1)
                val stepX = (size.width - Y_AXIS_WIDTH.toPx()) / denominator

                fun scoreToY(score: Int): Float {
                    val ratio = (score / 100f)
                    return chartHeight * (1 - ratio).coerceIn(0f, 1f)
                }

                // 2. 반복문 내부: 오직 점수 막대바와 X축 라벨만 드로잉 (툴팁 로직 제거됨)
                xLabels.forEachIndexed { i, dayInfo ->
                    val x = Y_AXIS_WIDTH.toPx() + (i * stepX)
                    val score = finalReportData.dailyScores[dayInfo.date]
                    if (score != null) {
                        val barWidth =
                            if (reportState.selectedTab == ReportTab.WEEKLY) WEEKLY_BAR_WIDTH.toPx() else MONTHLY_BAR_WIDTH.toPx()
                        val barHeight = chartHeight * (score / 100f)
                        drawRoundRect(
                            color = score.toSleepScoreStatus().primaryColor,
                            topLeft = Offset(x - barWidth / 2f, chartHeight - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(16.dp.toPx())
                        )
                    }

                    if (reportState.selectedTab == ReportTab.WEEKLY || i % 5 == 0) {
                        drawText(
                            textMeasurer.measure(dayInfo.labelText, labelStyle), topLeft = Offset(
                                x - textMeasurer.measure(
                                    dayInfo.labelText, labelStyle
                                ).size.width / 2f, chartHeight + 4.dp.toPx()
                            )
                        )
                    }
                }

                // 3. [개선 완료] 반복문이 완전히 끝난 후 활성화된 날짜의 단일 툴팁만 최상단에 1회 드로잉
                activeTooltipDate?.let { tooltipDate ->
                    val targetIndex = xLabels.indexOfFirst { it.date == tooltipDate }
                    if (targetIndex != -1) {
                        val score = finalReportData.dailyScores[tooltipDate]
                        if (score != null) {
                            val x = Y_AXIS_WIDTH.toPx() + (targetIndex * stepX)
                            val scoreY = scoreToY(score)

                            drawScoreTooltip(
                                x = x,
                                y = scoreY,
                                score = score,
                                textMeasurer = textMeasurer,
                                labelStyle = labelStyle,
                                chartWidth = size.width,
                                chartHeight = size.height
                            )
                        }
                    }
                }
            }
        }
    }
}

fun DrawScope.drawScoreTooltip(
    x: Float,
    y: Float,
    score: Int,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    chartWidth: Float,
    chartHeight: Float
) {
    val scoreMeasured = textMeasurer.measure(
        score.toString(),
        labelStyle.copy(color = Color.Black.copy(0.8f))
    )
    val tooltipWidth = scoreMeasured.size.width + (LABEL_PADDING.toPx() * 2)
    val tooltipHeight = scoreMeasured.size.height + (LABEL_PADDING.toPx() * 2)

    val tooltipX =
        (x - tooltipWidth / 2f).coerceIn(8.dp.toPx(), chartWidth - tooltipWidth - 8.dp.toPx())
    val tooltipY = (y - tooltipHeight - 10.dp.toPx()).coerceIn(
        8.dp.toPx(),
        chartHeight - tooltipHeight - 8.dp.toPx()
    )

    drawRoundRect(
        color = Color.White.copy(0.8f),
        topLeft = Offset(tooltipX, tooltipY),
        size = Size(tooltipWidth, tooltipHeight),
        cornerRadius = CornerRadius(12.dp.toPx())
    )
    drawText(
        textLayoutResult = scoreMeasured,
        topLeft = Offset(tooltipX + LABEL_PADDING.toPx(), tooltipY + LABEL_PADDING.toPx())
    )
}

fun getCalendarRange(reportState: ReportContract.State): CalendarRange {
    return when (reportState.selectedTab) {
        ReportTab.MONTHLY -> {
            val first = LocalDate(reportState.date.year, reportState.date.month, 1)
            val next = if (reportState.date.monthNumber == 12) LocalDate(
                reportState.date.year + 1, 1, 1
            ) else LocalDate(reportState.date.year, reportState.date.monthNumber + 1, 1)
            CalendarRange(first, next.minus(DatePeriod(days = 1)).dayOfMonth)
        }

        else -> CalendarRange(
            reportState.date.minus(DatePeriod(days = reportState.date.dayOfWeek.isoDayNumber - 1)),
            7
        )
    }
}

fun buildCalendarLabels(
    startDate: LocalDate, totalDays: Int, isWeekly: Boolean
): List<ChartDataEntity> {
    return (0 until totalDays).map { i ->
        val date = startDate.plus(DatePeriod(days = i))
        if (isWeekly) ChartWeekDay(
            date.dayOfWeek.isoDayNumber,
            date,
            formatWeekDayLabel(date)
        ) else ChartDate(
            date.dayOfWeek.isoDayNumber,
            date,
            formatDateLabel(date)
        )
    }
}

@Composable
private fun NoSleepDataForDatePlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "이 날의 수면 기록이 없어요",
            style = MaterialTheme.typography.bodyText,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "달력에서 기록이 있는 날짜를 선택해 보세요",
            style = MaterialTheme.typography.caption,
            color = Color.White.copy(0.5f),
            textAlign = TextAlign.Center
        )
    }
}

@Preview
@Composable
fun ReportScreenPreview() {
    SleepAppTheme {
        val date = LocalDate(2023, 1, 1)
        ReportContent(
            user = User(
                userId = 1L,
                nickname = "test",
                email = "test",
                isPremium = false,
                isActive = true,
                profileImageUrl =  "",
                lastLoginAt = LocalDateTime(2023,1,1,0,0),
                createdAt = LocalDateTime(2023,1,1,0,0),
                updatedAt = LocalDateTime(2023,1,1,0,0)
            ),
            authState = AuthContract.State(),
            trackingState = TrackingContract.State(),
            reportState = ReportContract.State(
                selectedTab = ReportTab.WEEKLY,
                date = date,
                isPreview = true,
                sessionDates = emptySet(),
                reportData = DemoReportFactory.createPreviewData(0L, date)
            ),
            sleepStageHistory = emptyList(),
            isCalendarShow = false,
            onCalendarToggleClicked = {_ ->},
            onReportModeSelected = {},
            onDateSelected = {},
            onNavigateToSleepEnvironment = { _, _, _, _, _ -> },
            onPrevMonthClicked = {},
            onNextMonthClicked = {}
        )
    }
}