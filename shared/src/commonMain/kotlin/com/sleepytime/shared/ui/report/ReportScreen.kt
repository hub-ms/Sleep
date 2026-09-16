package com.sleepytime.shared.ui.report

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.sleepytime.shared.enum_.EnvironmentCategory
import com.sleepytime.shared.enum_.SleepScoreLevel
import com.sleepytime.shared.enum_.SleepStageType
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_calendar
import com.sleepytime.shared.resources.ic_caret_down
import com.sleepytime.shared.resources.ic_caret_left
import com.sleepytime.shared.resources.ic_caret_right
import com.sleepytime.shared.resources.ic_caret_up
import com.sleepytime.shared.resources.ic_noise
import com.sleepytime.shared.resources.ic_report
import com.sleepytime.shared.resources.ic_sleep
import com.sleepytime.shared.resources.ic_star
import com.sleepytime.shared.ui.component.ChartLegend
import com.sleepytime.shared.ui.component.EnvironmentChart
import com.sleepytime.shared.ui.component.EnvironmentValues
import com.sleepytime.shared.ui.component.SelectableChip
import com.sleepytime.shared.ui.component.SelectableChipGroup
import com.sleepytime.shared.ui.component.toStatus
import com.sleepytime.shared.ui.theme.SleepAppTheme
import com.sleepytime.shared.ui.theme.SleepTheme
import com.sleepytime.shared.ui.theme.bodyHighlight
import com.sleepytime.shared.ui.theme.bodyText
import com.sleepytime.shared.ui.theme.caption
import com.sleepytime.shared.ui.theme.sectionTitle
import com.sleepytime.shared.ui.tracking.TrackingContract
import com.sleepytime.shared.util.ChartUtil
import com.sleepytime.shared.util.ChartUtil.scoreToY
import com.sleepytime.shared.util.ChartUtil.timeToY
import com.sleepytime.shared.util.DateTimeUtil.formatCalendarMonth
import com.sleepytime.shared.util.DateTimeUtil.formatCalendarWeek
import com.sleepytime.shared.util.DateTimeUtil.formatDate
import com.sleepytime.shared.util.DateTimeUtil.formatDateLabel
import com.sleepytime.shared.util.DateTimeUtil.formatSleepDurationFromMillis
import com.sleepytime.shared.util.DateTimeUtil.to24TimeString
import kotlinx.coroutines.delay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

data class SleepScoreStatus(
    val primaryColor: Color,
    val level: SleepScoreLevel
)

data class LegendItem(
    val label: String,
    val color: Color,
    val duration: Long,
    val percent: Int
)

data class ComparisonItem(
    val label: String,
    val value: Int? = null,
    val valueText: AnnotatedString,
    val isIncrease: Boolean? = null,
)

val stageTypes = listOf(
    SleepStageType.AWAKE,
    SleepStageType.LIGHT,
    SleepStageType.REM,
    SleepStageType.DEEP
)

val sleepStageColors: Map<SleepStageType, Color>
    @Composable
    get() = mapOf(
        stageTypes[0] to Color(0xFFFFB74D), // AWAKE: 호박색(amber) - 어두운 배경에서도 눈에 잘 띄는 경고성 색상
        stageTypes[1] to Color(0xFF64B5F6), // LIGHT: 밝은 하늘색 - REM/DEEP과 명확히 구분되는 채도/밝기
        stageTypes[2] to Color(0xFF26C6DA), // REM: 청록색 - LIGHT(파랑 계열)와 헷갈리지 않도록 색상(hue)을 분리
        stageTypes[3] to Color(0xFF7E57C2)  // DEEP: 진보라 - 기존 남색(0xFF1A237E)은 어두운 배경과 대비가 낮아 거의 안 보였음
    )
val BAR_WIDTH = 16.dp
val X_AXIS_PADDING = 16.dp
val Y_AXIS_PADDING = 16.dp
val LABEL_WIDTH = 48.dp
val CHART_TOP_PADDING = 24.dp


val SleepStageType.stageName: String
    get() = when (this) {
        SleepStageType.AWAKE -> "깨어남"
        SleepStageType.LIGHT -> "얕은수면"
        SleepStageType.REM -> "렘수면"
        SleepStageType.DEEP -> "깊은수면"
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
        color = SleepTheme.textColors.primary,
        fontWeight = FontWeight.Bold
    )
    val bodyStyle = MaterialTheme.typography.bodyText.toSpanStyle().copy(
        color = SleepTheme.textColors.primary
    )
    val labelStyle = MaterialTheme.typography.caption.copy(
        color = SleepTheme.textColors.primary,
    )

    return Triple(sectionStyle, bodyStyle, labelStyle)
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
    trackingState: TrackingContract.State,
    reportState: ReportContract.State,
    onToggleCalendarExpanded: (Boolean) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onPrevClicked: (DateTimeUnit.DateBased) -> Unit,
    onNextClicked: (DateTimeUnit.DateBased) -> Unit,
    onReportDeleteClicked: (String) -> Unit,
    onConfirmDelete: (String) -> Unit,
    onDismissDeleteDialog: () -> Unit
) {
    val (baseSectionStyle, baseBodyStyle, labelStyle) = rememberSleepTimeStyles()
    val scrollState = rememberScrollState()
    val weekStartDate = remember(reportState.date) {
        reportState.date.minus(
            (reportState.date.dayOfWeek.isoDayNumber - 1).toLong(),
            DateTimeUnit.DAY
        )
    }
    val xLabels = remember(weekStartDate) {
        buildCalendarLabels(startDate = weekStartDate)
    }
    var rangeItem by remember { mutableStateOf("오늘") }

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
    val durationText = formatSleepDurationFromMillis(sleepDurationMillis).toAnnotatedString(
        baseSectionStyle,
        baseBodyStyle
    )
    val scoreText = "${rawScore}점".toAnnotatedString(baseSectionStyle, baseBodyStyle)
    val latencyText = formatSleepDurationFromMillis(latencyMillis).toAnnotatedString(
        baseSectionStyle,
        baseBodyStyle
    )
    val avgDurationMillis = remember(reportState.weeklyChartData) {
        (reportState.weeklyChartData?.averageSleepDuration ?: 0).toLong() * 60 * 1000
    }
    val prevAvgDurationMillis = remember(reportState.weeklyChartData) {
        (reportState.prevWeeklyChartData?.averageSleepDuration ?: 0).toLong() * 60 * 1000
    }
    val avgScore = reportState.weeklyChartData?.averageScore
        ?: reportState.weeklyChartData?.sleepScore
        ?: reportState.reportData?.sleepScore
        ?: 0
    val prevAvgScore = reportState.prevWeeklyChartData?.averageScore
        ?: reportState.prevWeeklyChartData?.sleepScore
        ?: reportState.prevDayReportData?.sleepScore
        ?: 0
    val avgLatencyMinutes = reportState.weeklyChartData?.averageSleepLatencyMinutes
        ?: reportState.weeklyChartData?.sleepLatencyMinutes
        ?: reportState.reportData?.sleepLatencyMinutes
        ?: 0.0
    val prevAvgLatencyMinutes = reportState.prevWeeklyChartData?.averageSleepLatencyMinutes
        ?: reportState.prevWeeklyChartData?.sleepLatencyMinutes
        ?: reportState.prevDayReportData?.sleepLatencyMinutes
        ?: 0.0
    val avgLatencyMillis = (avgLatencyMinutes * 60000).toLong()
    val prevAvgLatencyMillis = (prevAvgLatencyMinutes * 60000).toLong()


    val avgDurationText = formatSleepDurationFromMillis(avgDurationMillis).toAnnotatedString(
        baseSectionStyle,
        baseBodyStyle
    )
    val avgScoreText = "${avgScore}점".toAnnotatedString(baseSectionStyle, baseBodyStyle)
    val avgLatencyText = formatSleepDurationFromMillis(avgLatencyMillis).toAnnotatedString(
        baseSectionStyle,
        baseBodyStyle
    )

    // "평균 소음"은 TimeScoreCard에서 표시하는 값으로, 소음 그래프 헤더 옆에 중복 표시하던 텍스트를
    // 이곳으로 옮긴 것이다. GraphHeader의 unit="dB"와 마찬가지로 단위를 값 뒤에 작게 붙인다.
    val noiseAvgText = buildAnnotatedString {
        withStyle(baseSectionStyle) { append(values.noiseAvg.roundToInt().toString()) }
        withStyle(baseBodyStyle) { append("dB") }
    }
    val weeklyNoiseAvg = reportState.weeklyChartData?.avgNoise ?: values.noiseAvg
    val weeklyNoiseAvgText = buildAnnotatedString {
        withStyle(baseSectionStyle) { append(weeklyNoiseAvg.roundToInt().toString()) }
        withStyle(baseBodyStyle) { append("dB") }
    }

    val dailyItems = listOf(
        ComparisonItem(
            label = "수면시간",
            valueText = durationText,
            isIncrease = sleepDurationMillis > prevSleepDurationMillis
        ),
        ComparisonItem(
            label = "수면점수",
            valueText = scoreText,
            isIncrease = rawScore > prevScore
        ),
        ComparisonItem(
            label = "잠들기까지",
            valueText = latencyText,
            isIncrease = latencyMillis > prevLatencyMillis
        ),
        ComparisonItem(
            label = "평균 소음",
            valueText = noiseAvgText,
        ),
    )
    val weeklyItems = listOf(
        ComparisonItem(
            label = "수면시간",
            valueText = avgDurationText,
            isIncrease = avgDurationMillis > prevAvgDurationMillis
        ),
        ComparisonItem(
            label = "수면점수",
            valueText = avgScoreText,
            isIncrease = avgScore > prevAvgScore
        ),
        ComparisonItem(
            label = "잠들기까지",
            valueText = avgLatencyText,
            isIncrease = avgLatencyMillis > prevAvgLatencyMillis
        ),
        ComparisonItem(
            label = "평균 소음",
            valueText = weeklyNoiseAvgText,
        ),
    )
    val textMeasurer = rememberTextMeasurer()
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ReportHeader(
                reportState = reportState,

                rangeItem = rangeItem,
                onSelectItem = {
                    rangeItem = it
                },
            )

            // 🐛 버그 수정 (달력 아래 화살표를 눌러도 확장이 안 되던 문제):
            // 기존에는 Calendar()와 토글 버튼이 스크롤되지 않는 고정 영역에 있었고,
            // 월간(확장) 보기로 전환되면 주간 보기보다 훨씬 많은 행(최대 5~6주)이 필요했습니다.
            // 화면 높이를 넘어서는 행들은 스크롤할 방법이 없어 화면 밖으로 잘려나갔고,
            // 그 결과 버튼을 눌러도 실제로는 상태가 바뀌었지만 화면에는 아무 변화가 없는 것처럼 보였습니다.
            // Calendar()와 토글 버튼을 아래의 스크롤 가능한 Column 안으로 옮겨서,
            // 달력이 몇 주로 확장되든 항상 스크롤해서 전체를 확인할 수 있도록 수정했습니다.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Calendar(
                    reportState = reportState,
                    selectedDate = reportState.date,
                    onDateSelected = { date ->
                        onDateSelected(date)
                    },
                    onPrevClicked = { onPrevClicked(it) },
                    onNextClicked = { onNextClicked(it) },
                )
                IconButton(
                    modifier = Modifier.size(36.dp),
                    onClick = { onToggleCalendarExpanded(!reportState.isCalendarExpanded) }
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = if (reportState.isCalendarExpanded) painterResource(Res.drawable.ic_caret_up) else painterResource(
                            Res.drawable.ic_caret_down
                        ),
                        contentDescription = "달력 토글",
                        tint = SleepTheme.textColors.primary
                    )
                }

                if (!reportState.isCalendarExpanded) {
                    TimeScoreCard(
                        rangeItem = rangeItem,
                        dailyItems = dailyItems,
                        weeklyItems = weeklyItems,
                    )
                    when(rangeItem) {
                        "오늘" -> SleepTimeLineEnvironmentCard(
                            reportState = reportState,
                            sleepDurationMillis = sleepDurationMillis,
                            targetDate = targetDate,
                            reportData = reportState.reportData,
                            bedTimeText = bedTimeText,
                            wakeTimeText = wakeTimeText,
                            values = values,
                            labelStyle = labelStyle,
                        )

                        else -> {
                            TimeChart(
                                reportState = reportState,
                                selectedMetric = "수면시간",
                                xLabels = xLabels,
                                textMeasurer = textMeasurer,
                                labelStyle = labelStyle,
                            )
                            TimeChart(
                                reportState = reportState,
                                selectedMetric = "수면점수",
                                xLabels = xLabels,
                                textMeasurer = textMeasurer,
                                labelStyle = labelStyle,
                            )
                            TimeChart(
                                reportState = reportState,
                                selectedMetric = "잠들기까지",
                                xLabels = xLabels,
                                textMeasurer = textMeasurer,
                                labelStyle = labelStyle,
                            )
                        }
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
                                contentDescription = "리포트 삭제",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
        if (reportState.isPreview) {
//            PreviewOverlay()
        }
        if (reportState.showDeleteDialog && reportState.pendingDeleteSessionId != null) {
            AlertDialog(
                onDismissRequest = onDismissDeleteDialog,
                title = { Text("리포트 삭제") },
                text = { Text("이 수면 리포트를 정말 삭제하시겠습니까? 삭제된 데이터는 복구할 수 없습니다.") },
                confirmButton = {
                    TextButton(
                        onClick = { onConfirmDelete(reportState.pendingDeleteSessionId) }
                    ) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDeleteDialog) {
                        Text("취소")
                    }
                }
            )
        }
    }
}

@Composable
fun PreviewOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.4f))
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "예시 리포트입니다",
                style = MaterialTheme.typography.bodyHighlight,
                color = SleepTheme.textColors.primary,
            )
            Text(
                text = "홈 화면에서 수면 시작 버튼을 눌러\n실제 수면 측정을 해보세요",
                style = MaterialTheme.typography.caption,
                color = SleepTheme.textColors.primary
            )
        }
    }
}

@Composable
fun Calendar(
    reportState: ReportContract.State,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onPrevClicked: (DateTimeUnit.DateBased) -> Unit,
    onNextClicked: (DateTimeUnit.DateBased) -> Unit,
) {
    val currentDayOfWeekNum = selectedDate.dayOfWeek.isoDayNumber
    val weekStartDate = selectedDate.minus((currentDayOfWeekNum - 1).toLong(), DateTimeUnit.DAY)
    val weekDates = (0 until 7).map { weekStartDate.plus(it.toLong(), DateTimeUnit.DAY) }


    val firstDayOfMonth = LocalDate(selectedDate.year, selectedDate.month, 1)
    val firstDayOfWeekNum = firstDayOfMonth.dayOfWeek.isoDayNumber
    val calendarStartDate =
        firstDayOfMonth.minus((firstDayOfWeekNum - 1).toLong(), DateTimeUnit.DAY)
    val monthDates = (0 until 35).map { calendarStartDate.plus(it.toLong(), DateTimeUnit.DAY) }


    val calendarTitleText =
        if (reportState.isCalendarExpanded) formatCalendarMonth(reportState.date) else formatCalendarWeek(
            reportState.date
        )

    val unit = if (reportState.isCalendarExpanded) DateTimeUnit.MONTH else DateTimeUnit.WEEK
    val prevDescription = if (reportState.isCalendarExpanded) "1달 전으로 이동" else "1주일 전으로 이동"
    val nextDescription = if (reportState.isCalendarExpanded) "1달 후로 이동" else "1주일 후로 이동"

    val prevTargetDate =
        if (reportState.isCalendarExpanded) reportState.date.minus(1, DateTimeUnit.MONTH)
        else reportState.date.minus(1, DateTimeUnit.WEEK)
    val nextTargetDate =
        if (reportState.isCalendarExpanded) reportState.date.plus(1, DateTimeUnit.MONTH)
        else reportState.date.plus(1, DateTimeUnit.WEEK)

    val hasSessionInPrev = remember(
        prevTargetDate,
        reportState.isCalendarExpanded,
        reportState.sessionDates,
        reportState.isPreview
    ) {
        if (reportState.isPreview) true
        else {
            val (start, end) = getTargetRange(prevTargetDate, reportState.isCalendarExpanded)
            reportState.sessionDates.any { it in start..end }
        }
    }
    val hasSessionInNext = remember(
        nextTargetDate,
        reportState.isCalendarExpanded,
        reportState.sessionDates,
        reportState.isPreview
    ) {
        if (reportState.isPreview) true
        else {
            val (start, end) = getTargetRange(nextTargetDate, reportState.isCalendarExpanded)
            reportState.sessionDates.any { it in start..end }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                modifier = Modifier.size(36.dp),
                enabled = hasSessionInPrev,
                onClick = { onPrevClicked(unit) }
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(Res.drawable.ic_caret_left),
                    contentDescription = prevDescription,
                    tint = if (hasSessionInPrev) SleepTheme.textColors.primary else SleepTheme.textColors.primary.copy(
                        0.4f
                    )
                )
            }
            Text(
                text = calendarTitleText,
                style = MaterialTheme.typography.bodyText,
                color = SleepTheme.textColors.primary,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                modifier = Modifier.size(36.dp),
                enabled = hasSessionInNext,
                onClick = { onNextClicked(unit) }
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(Res.drawable.ic_caret_right),
                    contentDescription = nextDescription,
                    tint = if (hasSessionInNext) SleepTheme.textColors.primary else SleepTheme.textColors.primary.copy(
                        0.4f
                    )
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
                        color = SleepTheme.textColors.primary
                    )
                }
            }
        }
        val weeks = monthDates.chunked(7)
        if (reportState.isCalendarExpanded) {
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
                                    .height(64.dp)
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
                            .height(64.dp)
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
    val currentDayScore =
        if (hasValidData) reportState.reportData?.dailyScores?.get(date) ?: 0 else 0

    val fillColor = when(currentDayScore) {
        in 0..60 -> Color.Red
        in 61..80 -> Color.Magenta
        else -> MaterialTheme.colorScheme.primary
    }
    if(hasValidData) {
        Box(
            modifier = modifier
                .clickable { onDateSelected(date) },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .drawWithContent {
                        drawContent()
                        drawRoundRect(
                            color = fillColor.copy(0.4f),
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(size.width / 2, size.width / 2),
                        )
                        if(date==selectedDate) {
                            drawRoundRect(
                                color = fillColor,
                                topLeft = Offset(0f, 0f),
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(size.width / 2, size.width / 2),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyHighlight,
                color = SleepTheme.textColors.primary
            )
        }
    }
}
@Composable
private fun ReportHeader(
    reportState: ReportContract.State,
    rangeItem: String,
    onSelectItem: (String) -> Unit,
) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = formatDate(reportState.date),
            style = MaterialTheme.typography.bodyText,
            color = SleepTheme.textColors.primary,
            fontWeight = FontWeight.Bold
        )
        val rangeItems = listOf("오늘", "이번 주")
        if(!reportState.isCalendarExpanded) {
            SelectableChipGroup(
                items = rangeItems,
                selectedItem = rangeItem,
                onSelectItem = onSelectItem
            )
        }
    }
}

@Composable
fun TimeScoreCard(
    rangeItem: String? = "오늘",
    dailyItems: List<ComparisonItem>,
    weeklyItems: List<ComparisonItem> = emptyList(),
) {
    val currentItems = if (rangeItem == "오늘") dailyItems else weeklyItems
    val itemMap = currentItems.associateBy { it.label }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(
                brush = SleepTheme.gradients.surface,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(itemMap["수면시간"])
                SummaryItem(itemMap["수면점수"])
                SummaryItem(itemMap["잠들기까지"])
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(itemMap["평균 소음"])
            }
        }
    }
}

@Composable
fun SleepTimeLineEnvironmentCard(
    reportState: ReportContract.State,
    sleepDurationMillis: Long,
    targetDate: LocalDate?,
    reportData: ReportContract.ReportData?,
    bedTimeText: String,
    wakeTimeText: String,
    values: EnvironmentValues,
    labelStyle: TextStyle,
) {
    if (reportData == null) return
    val noiseList = reportData.environmentHistory.map { it.noise }.filter { it > 0 }

    val awakeDuration = reportState.reportData?.awakeMinutes ?: 0.0
    val awakeMillis = (awakeDuration * 60000).roundToLong()

    val lightDuration = reportState.reportData?.lightMinutes ?: 0.0
    val lightMillis = (lightDuration * 60000).roundToLong()

    val deepDuration = reportState.reportData?.deepMinutes ?: 0.0
    val deepMillis = (deepDuration * 60000).roundToLong()

    val remDuration = reportState.reportData?.remMinutes ?: 0.0
    val remMillis = (remDuration * 60000).roundToLong()

    val rawPercents = if (sleepDurationMillis > 0) {
        listOf(
            awakeMillis.toDouble() / sleepDurationMillis * 100,
            lightMillis.toDouble() / sleepDurationMillis * 100,
            remMillis.toDouble() / sleepDurationMillis * 100,
            deepMillis.toDouble() / sleepDurationMillis * 100
        )
    } else {
        listOf(0.0, 0.0, 0.0, 0.0)
    }
    val roundedPercents = rawPercents.map {
        if (it.isNaN() || it.isInfinite()) 0 else it.roundToInt()
    }.toMutableList()

    // ChartLegend가 SleepTimeLineChart 옆으로 이동하면서 그만큼 SleepTimeLineChart의 플롯 폭이 줄어든다.
    // EnvironmentChart와 취침/기상 시각 텍스트는 그 줄어든 만큼(범례 폭 + 둘 사이 8dp 간격)을 오른쪽에
    // 동일하게 비워두어야 세 영역의 가로 길이가 실제로 일치한다.
    var legendWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val legendReserve = if (legendWidthPx > 0f) {
        with(density) { (legendWidthPx + 8.dp.toPx()).toDp() }
    } else 0.dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(
                brush = SleepTheme.gradients.surface,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GraphHeader(
                icon = Res.drawable.ic_sleep,
                title = "수면 단계",
                categoryColor = MaterialTheme.colorScheme.primary,
            )
            // 수면 단계 그래프 옆에 범례를 세로로 나란히 배치한다. ChartLegend는 각 항목을 그래프와
            // 동일한 16dp 높이 행으로 위에서부터 쌓으므로, 각 수면 단계(깨어남/얕은수면/렘수면/깊은수면)
            // 범례가 그래프의 해당 단계 행과 같은 높이에 정렬된다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SleepTimeLineChart(
                    modifier = Modifier.weight(1f),
                    reportState = reportState,
                    targetDate = targetDate,
                )
                ChartLegend(
                    modifier = Modifier.onSizeChanged { legendWidthPx = it.width.toFloat() },
                    items = listOf(
                        LegendItem(
                            label = stageTypes[0].stageName,
                            color = sleepStageColors[stageTypes[0]] ?: Color.Transparent,
                            duration = awakeMillis,
                            percent = roundedPercents[0]
                        ),
                        LegendItem(
                            label = stageTypes[1].stageName,
                            color = sleepStageColors[stageTypes[1]] ?: Color.Transparent,
                            duration = lightMillis,
                            percent = roundedPercents[1]
                        ),
                        LegendItem(
                            label = stageTypes[2].stageName,
                            color = sleepStageColors[stageTypes[2]] ?: Color.Transparent,
                            duration = remMillis,
                            percent = roundedPercents[2]
                        ),
                        LegendItem(
                            label = stageTypes[3].stageName,
                            color = sleepStageColors[stageTypes[3]] ?: Color.Transparent,
                            duration = deepMillis,
                            percent = roundedPercents[3]
                        )
                    )
                )
            }
            // "평균 [값]" 텍스트는 TimeScoreCard로 이동했으므로 여기서는 헤더만 표시한다.
            GraphHeader(
                icon = Res.drawable.ic_noise,
                title = "소음",
                categoryColor = MaterialTheme.colorScheme.primary,
                unit = "dB"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = legendReserve)
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
                    .padding(start = LABEL_WIDTH + Y_AXIS_PADDING, end = Y_AXIS_PADDING + legendReserve),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bedTimeText,
                    style = labelStyle,
                    color = SleepTheme.textColors.primary
                )
                Text(
                    text = wakeTimeText,
                    style = labelStyle,
                    color = SleepTheme.textColors.primary
                )
            }
        }
    }
}

@Composable
private fun GraphHeader(
    icon: DrawableResource,
    title: String,
    categoryColor: Color,
    unit: String? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(icon),
            contentDescription = title,
            tint = categoryColor
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyHighlight.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color.White
        )
        unit?.let {
            Text(
                text = "($it)",
                style = MaterialTheme.typography.bodyHighlight,
                color = Color.White.copy(0.4f)
            )
        }
    }
}

@Composable
private fun SummaryItem(item: ComparisonItem?) {
    if (item == null) return
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.caption,
            color = SleepTheme.textColors.primary
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

/**
 * 수면 중 깨어난 구간의 발생 원인을 추정합니다.
 * environmentHistory에서 해당 구간(startRatio~endRatio, 전체 수면시간 대비 비율)에 해당하는
 * 구간의 소음이 평균 대비 크게 튀고, 일정 시간 이상 지속되었는지를 보고 원인 텍스트를 만듭니다.
 * (5번 요청: 중간에 깬 지점에 원인을 알 수 있는 툴팁 추가)
 */
private fun <T> resolveWakeCause(
    environmentHistory: List<T>,
    startRatio: Float,
    endRatio: Float,
    avgNoise: Float,
    noiseOf: (T) -> Float,
): String {
    if (environmentHistory.isEmpty()) return "잠깐 깨어남"

    val size = environmentHistory.size
    val startIdx = (startRatio * size).toInt().coerceIn(0, size - 1)
    val endIdx = (endRatio * size).toInt().coerceIn(startIdx, size - 1)
    val window = environmentHistory.subList(startIdx, endIdx + 1)
    if (window.isEmpty()) return "잠깐 깨어남"

    val spikeThreshold = if (avgNoise > 0f) avgNoise * NOISE_SPIKE_RATIO else null

    val noiseSpike = if (spikeThreshold == null) {
        false
    } else if (window.size == 1) {
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

    return if (noiseSpike) "소음 상승 · 잠깐 깨어남" else "잠깐 깨어남"
}

@Composable
fun SleepTimeLineChart(
    modifier: Modifier = Modifier,
    reportState: ReportContract.State,
    targetDate: LocalDate?,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    var tooltipInfo by remember { mutableStateOf<Pair<Offset, String>?>(null) }

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
            val mergedList = mutableListOf<MergedStage<Any?>>()
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

    val sleepColors = sleepStageColors
    Box(modifier = modifier.fillMaxWidth().height(100.dp)) {
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
                            val xStart = chartStartX + (accumulatedTime.toFloat() / totalDurationMillis) * chartWidth
                            val xEnd = chartStartX + ((accumulatedTime + stage.durationMillis).toFloat() / totalDurationMillis) * chartWidth
                            if (offset.x in xStart..xEnd) {
                                if (stage.type == SleepStageType.AWAKE) {
                                    val durationMin = stage.durationMillis / 60000
                                    val startRatio = accumulatedTime.toFloat() / totalDurationMillis
                                    val endRatio = (accumulatedTime + stage.durationMillis).toFloat() / totalDurationMillis
                                    val cause = resolveWakeCause(
                                        environmentHistory = environmentHistory,
                                        startRatio = startRatio,
                                        endRatio = endRatio,
                                        avgNoise = avgNoise,
                                        noiseOf = { it.noise },
                                    )
                                    tooltipInfo = offset to "$cause (${durationMin}분)"
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
            // 심박수/소음 그래프와 동일하게 LABEL_WIDTH + Y_AXIS_PADDING 만큼 왼쪽 여백을,
            // Y_AXIS_PADDING 만큼 오른쪽 여백을 주어 세 그래프의 가로 길이(플롯 영역)를 맞춥니다.
            val chartStartX = LABEL_WIDTH.toPx() + Y_AXIS_PADDING.toPx()
            val chartEndX = size.width - Y_AXIS_PADDING.toPx()
            val chartWidth = (chartEndX - chartStartX).coerceAtLeast(1f)

            val rowHeightPx = 16.dp.toPx()
            val barCornerRadius = 4.dp.toPx()

            if (mergedTimeline.isNotEmpty()) {
                var accumulatedTime = 0L

                mergedTimeline.forEach { stage ->
                    val xStart = chartStartX + (accumulatedTime.toFloat() / totalDurationMillis) * chartWidth
                    val xEnd =
                        chartStartX + ((accumulatedTime + stage.durationMillis).toFloat() / totalDurationMillis) * chartWidth
                    val barWidth = (xEnd - xStart).coerceAtLeast(1f)

                    if (barWidth > 0f) {
                        val color = sleepColors[stage.type] ?: primaryColor.copy(alpha = 0.2f)
                        val stageIdx = when (stage.type) {
                            stageTypes[0] -> 0
                            stageTypes[1] -> 1
                            stageTypes[2] -> 2
                            stageTypes[3] -> 3
                            else -> 0
                        }
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

@Composable
fun TimeChart(
    reportState: ReportContract.State,
    selectedMetric: String,
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
                "수면시간" -> ChartUtil.generateYLabels(inputTimes = currentBedTimes + currentWakeTimes)
                "수면점수" -> listOf("0", "25", "50", "75", "100")
                "잠들기까지" -> ChartUtil.generateYLabels(inputLatencyMinutes = currentLatencyMinutes)
                else -> listOf("0", "25", "50", "75", "100")
            }
        }
    var selectedDate by remember(xLabels) { mutableStateOf(reportState.date) }

    val selectedBedTime = finalReportData.dailyBedTimes[selectedDate]
    val selectedWakeTime = finalReportData.dailyWakeTimes[selectedDate]
    val selectedScore = finalReportData.dailyScores[selectedDate]
    val selectedLatency = finalReportData.dailySleepLatencyMinutes[selectedDate]

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            val valueText = when (selectedMetric) {
                "수면시간" -> {
                    if (selectedBedTime != null && selectedWakeTime != null) {
                        "${selectedBedTime.to24TimeString()} ~ ${selectedWakeTime.to24TimeString()}"
                    } else "기록 없음"
                }

                "수면점수" -> selectedScore?.let { "${it}점" } ?: "기록 없음"
                "잠들기까지" -> selectedLatency?.let { "${it.toInt()}분" } ?: "기록 없음"
                else -> "기록 없음"
            }
            Text(
                text = valueText,
                style = labelStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.primary
            )
        }

        WeeklyBarChart(
            xLabels = xLabels,
            yLabels = yLabels,
            selectedDate = selectedDate,
            onDateSelected = { date -> selectedDate = date },
            textMeasurer = textMeasurer,
            labelStyle = labelStyle,
            calculateY = { chartHeight, date, isBedTime ->
                when (selectedMetric) {
                    "수면시간" -> {
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

                    "수면점수" -> {
                        if (isBedTime == null) {
                            finalReportData.dailyScores[date]?.let { scoreToY(chartHeight, it) }
                        } else null
                    }

                    "잠들기까지" -> {
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
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

@Preview
@Composable
fun ReportScreenPreview() {
    SleepAppTheme {
        val date = LocalDate(2023, 1, 1)
        ReportContent(
            trackingState = TrackingContract.State(),
            reportState = ReportContract.State(
                date = date,
                isPreview = true,
                sessionDates = emptySet(),
                reportData = DemoReportFactory.createPreviewData(0L, date),
                weeklyChartData = DemoReportFactory.createPreviewData(0L, date),
            ),
            onToggleCalendarExpanded = { _ -> },
            onDateSelected = {},
            onPrevClicked = { _ -> },
            onNextClicked = { _ -> },
            onReportDeleteClicked = {},
            onConfirmDelete = {},
            onDismissDeleteDialog = {}
        )
    }
}