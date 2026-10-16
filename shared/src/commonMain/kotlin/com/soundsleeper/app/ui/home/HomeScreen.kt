package com.soundsleeper.app.ui.home

import com.soundsleeper.app.resources.music_favorite_add
import com.soundsleeper.app.resources.music_favorite_remove
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.domain.model.SleepMusic
import com.soundsleeper.app.enum_.MusicCategory
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.button_sleep_start
import com.soundsleeper.app.resources.common_cancel
import com.soundsleeper.app.resources.ic_bookmark_filled
import com.soundsleeper.app.resources.ic_bookmark_outlined
import com.soundsleeper.app.resources.ic_lock
import com.soundsleeper.app.resources.ic_profile
import com.soundsleeper.app.resources.ic_report
import com.soundsleeper.app.resources.ic_sleep
import com.soundsleeper.app.resources.music_category_empty
import com.soundsleeper.app.resources.music_favorites_empty
import com.soundsleeper.app.resources.music_premium_lock_description
import com.soundsleeper.app.resources.music_timer_label
import com.soundsleeper.app.resources.music_timer_remaining_suffix
import com.soundsleeper.app.resources.short_sleep_dialog_confirm
import com.soundsleeper.app.resources.short_sleep_dialog_message
import com.soundsleeper.app.resources.short_sleep_dialog_title
import com.soundsleeper.app.resources.tab_home
import com.soundsleeper.app.resources.tab_mypage
import com.soundsleeper.app.resources.tab_report
import com.soundsleeper.app.resources.timer_1hour
import com.soundsleeper.app.resources.timer_2hour
import com.soundsleeper.app.resources.timer_30min
import com.soundsleeper.app.resources.timer_auto
import com.soundsleeper.app.ui.alarm.AlarmContract
import com.soundsleeper.app.ui.alarm.AlarmStatusCard
import com.soundsleeper.app.ui.component.CurrentMusicCard
import com.soundsleeper.app.ui.component.SegmentedControl
import com.soundsleeper.app.ui.component.SelectableChipGroup
import com.soundsleeper.app.ui.component.SleepAlertDialog
import com.soundsleeper.app.ui.music.MusicContract
import com.soundsleeper.app.ui.theme.IconSize
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.caption
import com.soundsleeper.app.ui.theme.sectionTitle
import com.soundsleeper.app.util.DateTimeUtil.formatSleepMusicSeconds
import com.soundsleeper.app.util.ResourceMapper
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class TabItem(
    val key: HomeTab,
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
    elapsedSleepMusicSeconds: Int,
    isUserPremium: Boolean = false,
    onStartTracking: (String?) -> Unit,
    onTogglePlaying: () -> Unit,
    onMusicSelected: (SleepMusic?) -> Unit,
    onToggleFavorite: (SleepMusic) -> Unit,
    onNavigateToSleepSetting: () -> Unit,
    onSetTimer: (Int?) -> Unit,
    onLockedTrackClicked: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AlarmStatusCard(
            alarmState = alarmState,
            onClick = onNavigateToSleepSetting
        )
        // 고정 240dp 였다. 그러면 (알람 카드 + 240 + 재생 카드 + 버튼) 합이 화면을 넘길 때
        // 가장 아래의 "수면 시작" 버튼이 밖으로 밀려 나갔다. 재생 카드와 버튼은 높이가
        // 고정이므로, 남는 공간을 전부 이 목록에 주면 어떤 화면 크기에서도 버튼이 보인다.
        // 덤으로 목록이 길어져 다음 줄이 걸쳐 보이고, 그게 스크롤된다는 힌트가 된다.
        MusicBrowserSection(
            modifier = Modifier.weight(1f),
            musicState = musicState,
            isUserPremium = isUserPremium,
            onMusicSelected = { music ->
                onMusicSelected(music)
            },
            onToggleFavorite = onToggleFavorite,
            onLockedTrackClicked = onLockedTrackClicked,
        )
        CurrentMusicCard(
            musicState = musicState,
            elapsedSleepMusicSeconds = elapsedSleepMusicSeconds,
            onTogglePlaying = onTogglePlaying,
            onToggleFavorite = onToggleFavorite,
            // 이 카드 바로 아래가 "수면 시작" 버튼이다. 곡을 고르고 말고에 따라 카드가
            // 자라면 그만큼 버튼이 밀려 작은 화면에서는 화면 밖으로 나간다.
            fixedHeight = true,
            timerContent = {
                MusicTimerSection(
                    musicState = musicState,
                    onSetTimer = onSetTimer,
                )
            },
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
    isUserPremium: Boolean = false,
    onMusicSelected: (SleepMusic?) -> Unit,
    onToggleFavorite: (SleepMusic) -> Unit,
    onLockedTrackClicked: () -> Unit = {},
) {
    var selectedCategory by remember { mutableStateOf(MusicCategory.NATURE) }
    val categories = listOf(
        MusicCategory.FAVORITE,
        MusicCategory.NATURE,
        MusicCategory.AMBIENT,
        MusicCategory.WAVE
    )

    Column(
        // 높이는 호출부가 정한다(홈은 weight, 측정 화면은 페이지 전체).
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SelectableChipGroup(
            items = categories,
            selectedItem = selectedCategory,
            onSelectItem = { selectedCategory = it }
        ) {
            when (val res = it.resId) {
                is StringResource -> stringResource(res)
                is DrawableResource -> painterResource(res)
                else -> res.toString()
            }
        }

        val filteredMusicList = remember(musicState.musicList, selectedCategory) {
            if (selectedCategory == MusicCategory.FAVORITE) musicState.musicList.filter { it.isFavorite }
            else musicState.musicList.filter { it.category == selectedCategory }
        }

        // 빈 상태 안내가 즐겨찾기에만 걸려 있었다. 뇌파 카테고리는 아직 트랙이 하나도 없어서
        // 칩을 누르면 설명 없이 빈 격자만 남았다.
        if (filteredMusicList.isEmpty()) {
            Box(
                // 고정 높이를 주면 카테고리를 옮길 때마다 목록 영역이 들썩인다.
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedCategory == MusicCategory.FAVORITE) {
                        stringResource(Res.string.music_favorites_empty)
                    } else {
                        stringResource(Res.string.music_category_empty)
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                columns = StaggeredGridCells.Fixed(2),
                verticalItemSpacing = 16.dp,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredMusicList) { music ->
                    MusicCompactCard(
                        music = music,
                        isSelected = music == musicState.selectedMusic,
                        isUserPremium = isUserPremium,
                        onMusicSelected = {
                            onMusicSelected(it)
                        },
                        onToggleFavorite = onToggleFavorite,
                        onLockedTrackClicked = onLockedTrackClicked,
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
    isUserPremium: Boolean = false,
    onMusicSelected: (SleepMusic?) -> Unit,
    onToggleFavorite: (SleepMusic) -> Unit = {},
    onLockedTrackClicked: () -> Unit = {},
) {
    val isLocked = music.isPremium && !isUserPremium
    // DB 의 title 컬럼을 쓰지 않는다. 카탈로그 시드가 INSERT OR IGNORE 라 한 번 들어간 제목은
    // 영영 그대로이고(= strings.xml 을 고쳐도 기존 기기에는 반영되지 않는다), 그 값이 시드
    // 당시의 로케일로 굳어 있어 앱 언어를 바꿔도 음악 제목만 번역되지 않았다.
    val title = stringResource(ResourceMapper.getMusicTitleRes(music.musicName))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isLocked) onLockedTrackClicked()
                else if (isSelected) onMusicSelected(null)
                else onMusicSelected(music)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(ResourceMapper.getMusicImageRes(music.imageName)),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_lock),
                        contentDescription = stringResource(Res.string.music_premium_lock_description),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (!isLocked) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .clickable {
                            onToggleFavorite(music)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (music.isFavorite) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_bookmark_filled),
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(24.dp).blur(4.dp)
                        )
                    }
                    Icon(
                        painter = if (music.isFavorite) painterResource(Res.drawable.ic_bookmark_filled) else painterResource(Res.drawable.ic_bookmark_outlined),
                        contentDescription = if (music.isFavorite) stringResource(Res.string.music_favorite_remove) else stringResource(Res.string.music_favorite_add),
                        tint = primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 수면음악 종료 타이머 선택. CurrentMusicCard 안에 들어간다.
 *
 * 알약 칩 네 개를 SpaceBetween 으로 흩어 놓았던 예전 모양은 글자 길이에 따라 칸 폭이 달라져
 * 무엇이 켜져 있는지 한눈에 들어오지 않았다. 선택지가 고정 4개이고 서로 배타적이므로 한 트랙
 * 안에서 칸을 균등 분할하는 세그먼트 컨트롤이 맞다.
 */
/** 세그먼트의 정체성. 예전에는 화면에 적힌 한국어 라벨("30분" 등) 그 자체가 정체성이었다 —
 * 라벨을 영어로 바꾸면 "지금 몇 분이 선택돼 있는지" 비교가 전부 깨지는, HomeTab 과 같은
 * 종류의 버그였다. 분(minute) 값을 식별자로 쓰고, 화면에 뭐라 적을지는 별도로 고른다. */
private enum class TimerChoice(val minutes: Int?) {
    THIRTY(30), ONE_HOUR(60), TWO_HOUR(120), AUTO(null)
}

@Composable
fun MusicTimerSection(
    musicState: MusicContract.State,
    onSetTimer: (Int?) -> Unit,
) {
    val selected = when {
        musicState.timerMinutes == 30 -> TimerChoice.THIRTY
        musicState.timerMinutes == 60 -> TimerChoice.ONE_HOUR
        musicState.timerMinutes == 120 -> TimerChoice.TWO_HOUR
        musicState.isAutoStopEnabled -> TimerChoice.AUTO
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 남은 시간을 그리는 UI가 아예 없었다. remainingSeconds 는 ViewModel 이 1초마다
        // 갱신하고 있었는데 아무도 읽지 않았고, music_timer_label / _remaining_suffix 두
        // 문자열도 선언만 된 채 참조가 0건이었다 — 이 Column 의 spacedBy(6.dp) 래퍼가
        // 라벨 행이 있던 시절의 잔재다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.music_timer_label),
                style = MaterialTheme.typography.caption,
                color = Color.White
            )
            musicState.remainingSeconds?.let { remaining ->
                Text(
                    text = formatSleepMusicSeconds(remaining) +
                        stringResource(Res.string.music_timer_remaining_suffix),
                    style = MaterialTheme.typography.caption,
                    color = primary
                )
            }
        }

        SegmentedControl(
            items = TimerChoice.entries,
            selectedItem = selected,
            itemLabel = { choice ->
                when (choice) {
                    TimerChoice.THIRTY -> stringResource(Res.string.timer_30min)
                    TimerChoice.ONE_HOUR -> stringResource(Res.string.timer_1hour)
                    TimerChoice.TWO_HOUR -> stringResource(Res.string.timer_2hour)
                    TimerChoice.AUTO -> stringResource(Res.string.timer_auto)
                }
            },
            onSelectItem = { onSetTimer(it.minutes) }
        )
    }
}

/**
 * 홈 화면의 "수면 시작" 버튼. 전체 수면 측정 사이클의 진입점이다.
 * 눌리면 알람까지 남은 시간을 계산해 너무 짧은 수면(30분 미만)이면 확인 다이얼로그를 먼저 띄우고,
 * 그렇지 않으면 바로 [onStartTracking]을 호출해 측정 화면으로 넘어가는 트래킹 시작 흐름을 건다.
 */
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
            containerColor = primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        onClick = {
            // 알람이 켜져 있다면 지금 자면 몇 분 뒤 깨는지 미리 계산해 너무 짧은 수면을 경고한다.
            val expectedMinutes = expectedSleepMinutesUntilAlarm(alarmState)
            if (expectedMinutes != null && expectedMinutes < 30) {
                showShortSleepDialog = true
            } else {
                // 선택된 음악 이름과 함께 트래킹 시작을 요청한다 (실제 시작 로직은 AppScreens.beginTracking).
                onStartTracking(musicState.selectedMusic?.musicName)
            }
        },
    ) {
        Text(
            text = stringResource(Res.string.button_sleep_start),
            style = MaterialTheme.typography.sectionTitle,
            textAlign = TextAlign.Center,
        )
    }

    // 측정 화면의 종료 확인 다이얼로그와 같은 공용 컴포넌트를 쓴다. 예전에는 같은 모양을
    // 이 파일에 한 번 더 복사해 두고 있었다.
    if (showShortSleepDialog) {
        SleepAlertDialog(
            title = stringResource(Res.string.short_sleep_dialog_title),
            message = stringResource(Res.string.short_sleep_dialog_message),
            confirmText = stringResource(Res.string.short_sleep_dialog_confirm),
            dismissText = stringResource(Res.string.common_cancel),
            onConfirm = {
                showShortSleepDialog = false
                onStartTracking(musicState.selectedMusic?.musicName)
            },
            onDismiss = { showShortSleepDialog = false }
        )
    }
}

/**
 * 알람이 켜져 있을 때, 지금 시각부터 알람이 울리는 시각까지 남은 시간을(분 단위) 계산한다.
 * 알람이 꺼져 있으면 비교할 기준이 없으므로 null을 반환한다.
 */
private fun expectedSleepMinutesUntilAlarm(alarmState: AlarmContract.State): Long? {
    if (!alarmState.isAlarmEnabled) return null

    val tz = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val today = now.toLocalDateTime(tz)

    // 알람 시:분을 오늘 날짜에 적용해 알람이 울릴 구체적인 시각을 만든다.
    val alarmDateTime = LocalDateTime(
        year = today.year,
        month = today.month,
        day = today.day,
        hour = alarmState.alarmHour,
        minute = alarmState.alarmMinute
    )
    var alarmInstant = alarmDateTime.toInstant(tz)
    // 계산된 알람 시각이 이미 지났다면(예: 밤 11시에 오전 7시 알람) 다음날로 하루 밀어준다.
    if (alarmInstant <= now) {
        alarmInstant = alarmInstant.plus(1L, DateTimeUnit.DAY, tz)
    }

    return (alarmInstant - now).inWholeMinutes
}

@Composable
fun CustomBottomTabBar(
    homeState: HomeContract.State,
    onBottomTabSelected: (HomeTab) -> Unit,
    modifier: Modifier
) {
    val tabs = listOf(
        TabItem(
            HomeTab.HOME,
            stringResource(Res.string.tab_home),
            painterResource(Res.drawable.ic_sleep)
        ),
        TabItem(
            HomeTab.REPORT,
            stringResource(Res.string.tab_report),
            painterResource(Res.drawable.ic_report)
        ),
        TabItem(
            HomeTab.MYPAGE,
            stringResource(Res.string.tab_mypage),
            painterResource(Res.drawable.ic_profile)
        )
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(48.dp)
            .background(primary.copy(0.4f)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val iconColor =
                if (homeState.selectedTab == tab.key) primary else MaterialTheme.colorScheme.onSurface.copy(
                    0.4f
                )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.clickable { onBottomTabSelected(tab.key) }.size(IconSize.Default),
                    contentAlignment = Alignment.Center
                ) {
                    if (homeState.selectedTab == tab.key) {
                        Icon(
                            painter = tab.icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(IconSize.Default).blur(4.dp)
                        )
                    }
                    Icon(
                        painter = tab.icon,
                        contentDescription = tab.title,
                        tint = iconColor,
                        modifier = Modifier.size(IconSize.Default)
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
