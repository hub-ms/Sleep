package com.sleepytime.shared.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.enum_.EnvironmentCategory
import com.sleepytime.shared.enum_.ReportTab
import com.sleepytime.shared.enum_.SleepStageType
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.button_sleep_start
import com.sleepytime.shared.resources.ic_mypage
import com.sleepytime.shared.resources.ic_report
import com.sleepytime.shared.resources.ic_sleep
import com.sleepytime.shared.resources.tab_home
import com.sleepytime.shared.resources.tab_mypage
import com.sleepytime.shared.resources.tab_report
import com.sleepytime.shared.ui.alarm.AlarmContract
import com.sleepytime.shared.ui.auth.AuthContract
import com.sleepytime.shared.ui.component.ChartLegend
import com.sleepytime.shared.ui.music.MusicContract
import com.sleepytime.shared.ui.report.DemoReportFactory
import com.sleepytime.shared.ui.report.LegendItem
import com.sleepytime.shared.ui.report.ReportContract
import com.sleepytime.shared.ui.report.ScoreDonutChart
import com.sleepytime.shared.ui.report.SleepStageColors
import com.sleepytime.shared.ui.report.StageBarChart
import com.sleepytime.shared.ui.report.SummaryCard
import com.sleepytime.shared.ui.report.rememberSleepTimeStyles
import com.sleepytime.shared.ui.report.sleepStageColors
import com.sleepytime.shared.ui.report.stageName
import com.sleepytime.shared.ui.report.stageTypes
import com.sleepytime.shared.ui.report.toAnnotatedString
import com.sleepytime.shared.ui.alarm.AlarmTopStatusSection
import com.sleepytime.shared.ui.theme.SleepAppTheme
import com.sleepytime.shared.ui.theme.bodyText
import com.sleepytime.shared.ui.theme.caption
import com.sleepytime.shared.ui.theme.sectionTitle
import com.sleepytime.shared.ui.tracking.MusicSection
import com.sleepytime.shared.ui.tracking.TrackingContract
import com.sleepytime.shared.util.DateTimeUtil.formatSleepDuration
import com.sleepytime.shared.util.DateTimeUtil.formatSleepDurationFromMillis
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.collections.indexOf
import kotlin.math.roundToLong

data class TabItem(
    val title: String,
    val icon: Painter
)

data class EnvironmentStatus(
    val statusText: String,
    val primaryColor: Color,
)

val EnvironmentCategory.displayName: String
    get() = when (this) {
        EnvironmentCategory.HEART_RATE -> "심박수"
        EnvironmentCategory.NOISE -> "소음"
        EnvironmentCategory.TEMPERATURE -> "온도"
        EnvironmentCategory.HUMIDITY -> "습도"
    }


@ExperimentalMaterial3Api
@Composable
fun HomeContent(
    homeState: HomeContract.State,
    authState: AuthContract.State,
    alarmState: AlarmContract.State,
    musicState: MusicContract.State,
    trackingState: TrackingContract.State,
    reportState: ReportContract.State,
    elapsedSleepMusicSeconds: Int,
    onSleepSettingClicked: () -> Unit,
    onSleepSummaryClicked: () -> Unit,
    onToggleSleepMusicClicked: () -> Unit,
    onSleepMusicClicked: () -> Unit,
    onStartTrackingClicked: (Int, String?) -> Unit,
    onBottomTabSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "안녕하세요 ${authState.userType.displayName}님",
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White
        )
        AlarmTopStatusSection(
            alarmState = alarmState,
            onSleepSettingClicked = onSleepSettingClicked
        )
        SleepSummaryCard(
            reportState = reportState,
        )
        MusicSection(
            musicState = musicState,
            elapsedSleepMusicSeconds = elapsedSleepMusicSeconds,
            onChangeMusicClicked = onSleepMusicClicked,
            onToggleSleepMusicClicked = onToggleSleepMusicClicked
        )
        SleepStartButton(
            trackingState = trackingState,
            musicState = musicState,
            onStartTrackingClicked = onStartTrackingClicked
        )
    }
}

@Composable
fun SleepSummaryCard(
    reportState: ReportContract.State,
) {
    var activeTooltipDate by remember { mutableStateOf<LocalDate?>(null) }
    val (baseSectionStyle, baseBodyStyle) = rememberSleepTimeStyles()

    var selectedStage by remember { mutableStateOf<SleepStageType?>(null) }
    val targetDate = activeTooltipDate ?: reportState.date
    val finalReportData = reportState.reportData


    val sleepStageItems = listOf(
        Triple(SleepStageType.AWAKE, finalReportData.awakeMinutes.roundToLong(), sleepStageColors[SleepStageType.AWAKE]!!),
        Triple(SleepStageType.LIGHT, finalReportData.lightMinutes.roundToLong(), sleepStageColors[SleepStageType.LIGHT]!!),
        Triple(SleepStageType.DEEP, finalReportData.deepMinutes.roundToLong(), sleepStageColors[SleepStageType.DEEP]!!),
        Triple(SleepStageType.REM, finalReportData.remMinutes.roundToLong(), sleepStageColors[SleepStageType.REM]!!),
    )
    val latencyMinutes = if (reportState.isPreview) finalReportData.dailyLatencyMinutes[targetDate]?.toLong() ?: 0L
    else finalReportData.sleepLatencyMinutes.toLong()

    val bedTime = if (reportState.isPreview) finalReportData.dailyBedTimes[targetDate]
    else finalReportData.bedTime

    val wakeTime = if (reportState.isPreview) finalReportData.dailyWakeTimes[targetDate]
    else finalReportData.wakeTime

    val rawScore = if (reportState.isPreview) finalReportData.dailyScores[targetDate]
    else finalReportData.sleepScore



    val sleepLatencyAnnotatedText =
        formatSleepDurationFromMillis(latencyMinutes).toAnnotatedString(
            baseSectionStyle.copy(color = Color.White), baseBodyStyle.copy(color = Color.White)
        )
    val sleepDurationAnnotatedText =
        formatSleepDuration(bedTime, wakeTime).toAnnotatedString(
            baseSectionStyle.copy(color = Color.White), baseBodyStyle.copy(color = Color.White)
        )



    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(0.2f)
        )
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
    }
}

@Composable
private fun NoSleepDataPlaceholder() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "어제 수면 기록이 없어요",
            style = MaterialTheme.typography.bodyText,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "오늘 밤 수면 측정을 시작하면\n내일 리포트를 확인할 수 있어요",
            style = MaterialTheme.typography.caption,
            color = Color.White.copy(0.5f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SleepStartButton(
    trackingState: TrackingContract.State,
    musicState: MusicContract.State,
    onStartTrackingClicked: (Int, String?) -> Unit
) {

    Button(
        onClick = {
            onStartTrackingClicked(
                trackingState.duration,
                musicState.selectedMusic?.title
            )
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ),
    ) {
        Text(
            text = stringResource(Res.string.button_sleep_start),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
fun CustomBottomTabBar(
    homeState: HomeContract.State,
    onBottomTabSelected: (String) -> Unit,
    modifier: Modifier
) {
    val tabs = listOf(
        TabItem(stringResource(Res.string.tab_home), painterResource(Res.drawable.ic_sleep)),
        TabItem(stringResource(Res.string.tab_report), painterResource(Res.drawable.ic_report)),
        TabItem(stringResource(Res.string.tab_mypage), painterResource(Res.drawable.ic_mypage))
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(72.dp)
            .background(MaterialTheme.colorScheme.surface),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val iconColor =
                if (homeState.selectedTab == tab.title) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                    0.4f
                )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.clickable { onBottomTabSelected(tab.title) }.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (homeState.selectedTab == tab.title) {
                        Icon(
                            painter = tab.icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(36.dp).blur(4.dp)
                        )
                    }
                    Icon(
                        painter = tab.icon,
                        contentDescription = tab.title,
                        tint = iconColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.caption,
                    color = Color.White
                )
            }
        }
    }
}

@Preview
@ExperimentalMaterial3Api
@Composable
fun HomeScreenPreview() {
    SleepAppTheme {
        val date = LocalDate(2023, 1, 1)
        HomeContent(
            homeState = HomeContract.State(),
            alarmState = AlarmContract.State(),
            musicState = MusicContract.State(),
            trackingState = TrackingContract.State(),
            reportState = ReportContract.State(
                selectedTab = ReportTab.WEEKLY,
                date = date,
                isPreview = true,
                sessionDates = emptySet(),
                reportData = DemoReportFactory.createPreviewData(0L, date)
            ),
            elapsedSleepMusicSeconds = 0,
            onSleepSettingClicked = {},
            onSleepSummaryClicked = {},
            onToggleSleepMusicClicked = {},
            onSleepMusicClicked = {},
            onStartTrackingClicked = {_, _ -> },
            onBottomTabSelected = {},
            authState = AuthContract.State(
                userType = User.AuthInfo.Guest
            ),
        )
    }
}
