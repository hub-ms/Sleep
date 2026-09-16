package com.sleepytime.shared.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.domain.model.SleepMusic
import com.sleepytime.shared.enum_.MusicCategory
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.button_sleep_start
import com.sleepytime.shared.resources.ic_favorite_filled
import com.sleepytime.shared.resources.ic_favorite_outline
import com.sleepytime.shared.resources.ic_mypage
import com.sleepytime.shared.resources.ic_pencil
import com.sleepytime.shared.resources.ic_report
import com.sleepytime.shared.resources.ic_sleep
import com.sleepytime.shared.resources.tab_home
import com.sleepytime.shared.resources.tab_mypage
import com.sleepytime.shared.resources.tab_report
import com.sleepytime.shared.ui.alarm.AlarmContract
import com.sleepytime.shared.ui.alarm.AlarmStatusCard
import com.sleepytime.shared.ui.component.SelectableChipGroup
import com.sleepytime.shared.ui.music.MusicContract
import com.sleepytime.shared.ui.report.ReportContract
import com.sleepytime.shared.ui.report.rememberSleepTimeStyles
import com.sleepytime.shared.ui.report.toAnnotatedString
import com.sleepytime.shared.ui.theme.SleepTheme
import com.sleepytime.shared.ui.theme.bodyText
import com.sleepytime.shared.ui.theme.caption
import com.sleepytime.shared.ui.theme.sectionTitle
import com.sleepytime.shared.ui.tracking.CurrentMusicCard
import com.sleepytime.shared.ui.tracking.TrackingContract
import com.sleepytime.shared.util.DateTimeUtil.formatSleepDurationFromMillis
import com.sleepytime.shared.util.ResourceMapper
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.ExperimentalTime

data class TabItem(
    val title: String,
    val icon: Painter
)

data class EnvironmentStatus(
    val primaryColor: Color,
)

@Composable
@ExperimentalMaterial3Api
@ExperimentalTime
fun HomeContent(
    alarmState: AlarmContract.State,
    musicState: MusicContract.State,
    trackingState: TrackingContract.State,
    reportState: ReportContract.State,
    elapsedSleepMusicSeconds: Int,
    onStartTracking: (String?) -> Unit,
    onTogglePlaying: () -> Unit,
    onMusicSelected: (SleepMusic?) -> Unit,
    onToggleFavorite: (SleepMusic) -> Unit,
    onNavigateToSleepSetting: () -> Unit,
    onSetTimer: (Int?) -> Unit,
) {
    val (baseSectionStyle, baseBodyStyle, _) = rememberSleepTimeStyles()

    val targetDate = reportState.date
    val bedTime = if (reportState.isPreview) reportState.reportData?.dailyBedTimes[targetDate]
        ?: reportState.reportData?.bedTime ?: trackingState.trackingStartTime
    else reportState.reportData?.bedTime ?: trackingState.trackingStartTime

    val wakeTime = reportState.reportData?.dailyWakeTimes?.get(targetDate)
        ?: reportState.reportData?.wakeTime ?: trackingState.trackingEndTime

    val sleepDurationMillis = remember(reportState.reportData) {
        (reportState.reportData?.sleepMinutes ?: 0.0).toLong() * 60 * 1000
    }
    val rawScore = reportState.reportData?.dailyScores?.get(targetDate)
        ?: reportState.reportData?.sleepScore ?: 0

    val latencyMinutes = reportState.reportData?.dailySleepLatencyMinutes?.get(targetDate)
        ?: reportState.reportData?.sleepLatencyMinutes ?: 0.0
    val latencyMillis = (latencyMinutes * 60000).toLong()

    var showMusicList = remember { mutableStateOf(false) }


    val scoreText = "${rawScore}점".toAnnotatedString(baseSectionStyle, baseBodyStyle)
    val durationText = formatSleepDurationFromMillis(sleepDurationMillis).toAnnotatedString(
        baseSectionStyle,
        baseBodyStyle
    )
    val latencyText = formatSleepDurationFromMillis(latencyMillis).toAnnotatedString(
        baseSectionStyle,
        baseBodyStyle
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        AlarmStatusCard(
            alarmState = alarmState,
        )
        Surface(
            modifier = Modifier
                .clickable { onNavigateToSleepSetting() },
            shape = RoundedCornerShape(50.dp),
            color = Color.White.copy(0.1f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(0.4f))
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                text = "수면 설정",
                style = MaterialTheme.typography.caption,
                color = SleepTheme.textColors.primary
            )
        }
        CurrentMusicCard(
            musicState = musicState,
            elapsedSleepMusicSeconds = elapsedSleepMusicSeconds,
            onTogglePlaying = onTogglePlaying,
            onSetTimer = onSetTimer,
        )
        MusicBrowserSection(
            musicState = musicState,
            onMusicSelected = { music ->
                onMusicSelected(music)
                showMusicList.value = false
            },
            onToggleFavorite = onToggleFavorite,
        )
        SleepStartButton(
            musicState = musicState,
            alarmState = alarmState,
            onStartTracking = onStartTracking
        )
    }
}

@Composable
fun MusicBrowserSection(
    modifier: Modifier = Modifier,
    musicState: MusicContract.State,
    onMusicSelected: (SleepMusic?) -> Unit,
    onToggleFavorite: (SleepMusic) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(MusicCategory.NATURE) }
    val categories = listOf(
        MusicCategory.FAVORITE,
        MusicCategory.NATURE,
        MusicCategory.AMBIENT,
        MusicCategory.WAVE
    )

    fun getDisplayMusicCategoryName(category: MusicCategory): String = when (category) {
        MusicCategory.FAVORITE -> "즐겨찾기"
        MusicCategory.NATURE -> "자연"
        MusicCategory.AMBIENT -> "멜로디"
        MusicCategory.WAVE -> "뇌파"
    }

    Column(
        modifier = modifier

            .fillMaxWidth()
            .height(240.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SelectableChipGroup(
            items = categories,
            selectedItem = selectedCategory,
            onSelectItem = { selectedCategory = it },
            itemLabel = { getDisplayMusicCategoryName(it) }
        )

        val filteredMusicList = remember(musicState.musicList, selectedCategory) {
            if (selectedCategory == MusicCategory.FAVORITE) musicState.musicList.filter { it.isFavorite }
            else musicState.musicList.filter { it.category == selectedCategory }
        }

        if (selectedCategory == MusicCategory.FAVORITE && filteredMusicList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "즐겨찾는 음악이 없습니다.\n마음에 드는 음악에 하트를 눌러보세요!",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth(),
                verticalItemSpacing = 16.dp,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredMusicList) { music ->
                    MusicCompactCard(
                        music = music,
                        isSelected = music == musicState.selectedMusic,
                        onMusicSelected = {
                            onMusicSelected(it)
                        },
                        onToggleFavorite = onToggleFavorite
                    )
                }
            }
        }
    }
}

@Composable
fun MusicCompactCard(
    music: SleepMusic,
    isSelected: Boolean,
    onMusicSelected: (SleepMusic?) -> Unit,
    onToggleFavorite: (SleepMusic) -> Unit,
) {
    val image = remember(music.imageName) { ResourceMapper.getDrawableRes(music.imageName) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isSelected) {
                    onMusicSelected(null)
                } else {
                    onMusicSelected(music)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(image),
                contentDescription = music.title,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .aspectRatio(1.0f)
                    .clip(RoundedCornerShape(16.dp))
                    .then(
                        if (isSelected) Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(16.dp)
                        ) else Modifier
                    ),
                contentScale = ContentScale.Crop
            )
            IconButton(
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.TopEnd),
                onClick = {
                    onToggleFavorite(music)
                },
            ) {
                Icon(
                    painter = if(music.isFavorite) painterResource(Res.drawable.ic_favorite_filled) else painterResource(Res.drawable.ic_favorite_outline),
                    contentDescription = if(music.isFavorite) "즐겨찾기 해제" else "즐겨찾기 등록",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = music.title,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SleepStartButton(
    musicState: MusicContract.State,
    alarmState: AlarmContract.State,
    onStartTracking: (String?) -> Unit
) {
    var showShortSleepDialog by remember { mutableStateOf(false) }

    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ),
        onClick = {
            val expectedMinutes = expectedSleepMinutesUntilAlarm(alarmState)
            if (expectedMinutes != null && expectedMinutes < 30) {
                showShortSleepDialog = true
            } else {
                onStartTracking(musicState.selectedMusic?.title)
            }
        },
    ) {
        Text(
            text = stringResource(Res.string.button_sleep_start),
            style = MaterialTheme.typography.sectionTitle,
            textAlign = TextAlign.Center,
        )
    }

    // 트래킹 화면의 종료 확인 다이얼로그와 동일한 스타일을 재사용합니다.
    if (showShortSleepDialog) {
        AlertDialog(
            modifier = Modifier
                .background(
                    brush = SleepTheme.gradients.surface,
                    shape = RoundedCornerShape(16.dp)
                ),
            onDismissRequest = { showShortSleepDialog = false },
            containerColor = Color.Transparent,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "짧은 수면 시간",
                    style = MaterialTheme.typography.bodyText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "설정된 기상 시각까지 30분이 채 남지 않았어요.\n그래도 수면 측정을 시작할까요?",
                    style = MaterialTheme.typography.bodyText,
                    color = Color.White
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showShortSleepDialog = false
                        onStartTracking(musicState.selectedMusic?.title)
                    }
                ) {
                    Text(
                        text = "시작하기",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showShortSleepDialog = false }) {
                    Text(
                        text = "취소",
                        style = MaterialTheme.typography.caption,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }
}

// 알람이 켜져 있을 때, 지금부터 다음 알람 시각까지 남은 분을 계산합니다.
// (알람 시각이 이미 지났다면 다음 날로 넘어가는 것으로 간주합니다.)
private fun expectedSleepMinutesUntilAlarm(alarmState: AlarmContract.State): Long? {
    if (!alarmState.isAlarmEnabled) return null

    val tz = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val today = now.toLocalDateTime(tz)

    var alarmDateTime = LocalDateTime(
        year = today.year,
        month = today.month,
        dayOfMonth = today.dayOfMonth,
        hour = alarmState.alarmHour,
        minute = alarmState.alarmMinute
    )
    var alarmInstant = alarmDateTime.toInstant(tz)
    if (alarmInstant <= now) {
        alarmInstant = alarmInstant.plus(1L, DateTimeUnit.DAY, tz)
    }

    return (alarmInstant - now).inWholeMinutes
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
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(48.dp)
            .background(MaterialTheme.colorScheme.primary.copy(0.4f)),
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
                    modifier = Modifier.clickable { onBottomTabSelected(tab.title) }.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (homeState.selectedTab == tab.title) {
                        Icon(
                            painter = tab.icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(32.dp).blur(4.dp)
                        )
                    }
                    Icon(
                        painter = tab.icon,
                        contentDescription = tab.title,
                        tint = iconColor,
                        modifier = Modifier.size(32.dp)
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
