package com.sleepytime.shared.ui.alarm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.domain.model.Alarm
import com.sleepytime.shared.enum_.PermissionType
import com.sleepytime.shared.platform.CircleCanvas
import com.sleepytime.shared.platform.rememberPermissionHandler
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_alarm_clock
import com.sleepytime.shared.resources.ic_music_note
import com.sleepytime.shared.resources.ic_smart_alarm
import com.sleepytime.shared.resources.ic_vibration
import com.sleepytime.shared.resources.ic_volume_high
import com.sleepytime.shared.resources.ic_volume_low
import com.sleepytime.shared.resources.ic_volume_off
import com.sleepytime.shared.ui.component.SelectableChipGroup
import com.sleepytime.shared.ui.component.ToggleSettingItem
import com.sleepytime.shared.ui.theme.SleepAppTheme
import com.sleepytime.shared.ui.theme.SleepTheme
import com.sleepytime.shared.ui.theme.bodyText
import com.sleepytime.shared.ui.theme.caption
import com.sleepytime.shared.ui.theme.sectionTitle
import com.sleepytime.shared.util.DateTimeUtil.to24TimeString
import com.sleepytime.shared.util.DateTimeUtil.toLocalDateTime
import com.sleepytime.shared.util.ResourceMapper
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.round
import kotlin.math.roundToInt

enum class Setting(val text: String) {
    ALARM("알람"), VIBRATION("진동"), SMART_ALARM("스마트 알람")
}

enum class SleepTrackingMode(val text: String) {
    AUTO_PHONE("스마트폰"), AUTO_WATCH("스마트워치")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSettingContent(
    alarmState: AlarmContract.State,
    onChangeAlarmHour: (Int) -> Unit,
    onChangeAlarmMinute: (Int, Int) -> Unit,
    onToggleAlarm: () -> Unit,
    onSelectAlarmSound: (Alarm.Sound) -> Unit,
    onChangeVolume: (Float) -> Unit,
    onToggleVibration: () -> Unit,
    onToggleSmartAlarm: () -> Unit,
    onSelectSmartAlarmRange: (Int) -> Unit,
    onToggleRecommend: (Boolean) -> Unit,
    onToggleSleepReminder: (Boolean) -> Unit,
    onChangeReminderTime: (Int, Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlarmStatusCard(
                alarmState = alarmState,
            )
            AlarmTopStatusSection(
                alarmState = alarmState,
                selectedAlarm = alarmState.selectedAlarmSound,
                onChangeAlarmHour = onChangeAlarmHour,
                onChangeAlarmMinute = onChangeAlarmMinute,
                onToggleAlarm = onToggleAlarm,
                onSelectAlarmSound = onSelectAlarmSound,
                onChangeVolume = onChangeVolume,
                onToggleVibration = onToggleVibration,
                onToggleSmartAlarm = onToggleSmartAlarm,
                onSelectSmartAlarmRange = onSelectSmartAlarmRange,
                onToggleRecommend = onToggleRecommend,
                onToggleSleepReminder = onToggleSleepReminder,
                onChangeReminderTime = onChangeReminderTime
            )
        }
    }
}

@Composable
@ExperimentalMaterial3Api
fun AlarmTopStatusSection(
    alarmState: AlarmContract.State,
    selectedAlarm: Alarm.Sound,
    onNavigateToSleepSetting: () -> Unit = {},
    onChangeAlarmHour: (Int) -> Unit,
    onChangeAlarmMinute: (Int, Int) -> Unit,
    onToggleAlarm: () -> Unit,
    onSelectAlarmSound: (Alarm.Sound) -> Unit,
    onChangeVolume: (Float) -> Unit,
    onToggleVibration: () -> Unit,
    onToggleSmartAlarm: () -> Unit,
    onSelectSmartAlarmRange: (Int) -> Unit,
    onToggleRecommend: (Boolean) -> Unit,
    onToggleSleepReminder: (Boolean) -> Unit,
    onChangeReminderTime: (Int, Int) -> Unit
) {
    val effectiveVibrationEnabled =
        alarmState.isAlarmEnabled && (alarmState.isVibrationEnabled || alarmState.appVolume == 0f)
    val effectiveSmartAlarmEnabled = alarmState.isAlarmEnabled && alarmState.isSmartAlarmEnabled

    val alarmSounds = remember {
        listOf(
            Alarm.Sound(
                "bird",
                ResourceMapper.getAlarmTitleRes("bird").toString(),
                "files/alarm_bird.ogg",
                0.8f
            ),
            Alarm.Sound(
                "cricket",
                ResourceMapper.getAlarmTitleRes("cricket").toString(),
                "files/alarm_cricket.ogg",
                0.8f
            ),
            Alarm.Sound(
                "piano",
                ResourceMapper.getAlarmTitleRes("piano").toString(),
                "files/alarm_piano.ogg",
                0.8f
            ),
            Alarm.Sound(
                "wave",
                ResourceMapper.getAlarmTitleRes("wave").toString(),
                "files/alarm_wave.ogg",
                0.8f
            ),
            Alarm.Sound(
                "upbeat",
                ResourceMapper.getAlarmTitleRes("upbeat").toString(),
                "files/alarm_upbeat.ogg",
                0.8f
            )
        )
    }
    val scrollState = rememberScrollState()
    var showSoundSelection by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissionHandler = rememberPermissionHandler(onResult = { _, _ -> })

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
            modifier = Modifier.padding(8.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ToggleSettingItem(
                title = Setting.ALARM.text,
                checked = alarmState.isAlarmEnabled,
                onCheckedChange = {
                    onToggleAlarm()
                }
            )

            if (alarmState.isAlarmEnabled) {
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AlarmTimeSection(
                            alarmState = alarmState,
                            onChangeAlarmHour = onChangeAlarmHour,
                            onChangeAlarmMinute = onChangeAlarmMinute,
                            onToggleRecommend = onToggleRecommend,
                        )
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            alarmSounds.forEach { sound ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectAlarmSound(sound)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val title =
                                        stringResource(ResourceMapper.getAlarmTitleRes(sound.id))
                                    Text(
                                        modifier = Modifier.align(Alignment.CenterStart),
                                        text = title,
                                        style = MaterialTheme.typography.bodyText,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    RadioButton(
                                        modifier = Modifier.align(Alignment.CenterEnd),
                                        selected = sound == alarmState.selectedAlarmSound,
                                        onClick = {
                                            onSelectAlarmSound(sound)
                                            showSoundSelection = false
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                    VerticalVolumeDial(
                        volume = alarmState.appVolume,
                        onChangeVolume = onChangeVolume,
                    )
                }
                ToggleSettingItem(
                    alarmState = alarmState,
                    title = Setting.VIBRATION.text,
                    checked = alarmState.isVibrationEnabled,
                    onCheckedChange = {
                        onToggleVibration()
                    }
                )
                if (alarmState.appVolume == 0f) Text(
                    text = "음량이 0이면 진동이 항상 켜집니다",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colorScheme.primary
                )
                ToggleSettingItem(
                    title = Setting.SMART_ALARM.text,
                    checked = alarmState.isSmartAlarmEnabled,
                    onCheckedChange = {
                        onToggleSmartAlarm()
                    }
                )
                if (alarmState.isSmartAlarmEnabled) {
                    SelectableChipGroup(
                        items = alarmState.smartAlarmRangeList,
                        selectedItem = alarmState.selectedSmartAlarmRange,
                        onSelectItem = onSelectSmartAlarmRange,
                        itemLabel = { "${it}분" }
                    )
                }
                ToggleSettingItem(
                    title = "취침 시각 알림",
                    subtitle = "설정하신 시간에 맞춰 수면 준비를 도와드립니다",
                    checked = alarmState.isReminderEnabled,
                    onCheckedChange = {
                        if (it) {
                            permissionHandler.request(PermissionType.NOTIFICATION)
                        }
                        onToggleSleepReminder(it)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (it) "취침 시각 알림이 켜졌습니다" else "취침 시각 알림이 꺼졌습니다",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                )
                AnimatedVisibility(
                    visible = alarmState.isReminderEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    BedtimePickerSection(
                        hour = alarmState.reminderHour,
                        minute = alarmState.reminderMinute,
                        onTimeChanged = { hour, minute ->
                            onChangeReminderTime(hour, minute)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AlarmStatusCard(
    alarmState: AlarmContract.State,
) {
    val effectiveVibrationEnabled =
        alarmState.isAlarmEnabled && (alarmState.isVibrationEnabled || alarmState.appVolume == 0f)
    val effectiveSmartAlarmEnabled = alarmState.isAlarmEnabled && alarmState.isSmartAlarmEnabled
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
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopStatusHeader(
                alarmState = alarmState,
            )
            if (alarmState.isAlarmEnabled) {
                BottomStatusRow(
                    alarmState = alarmState,
                    selectedAlarm = alarmState.selectedAlarmSound,
                    effectiveVibrationEnabled = effectiveVibrationEnabled,
                    effectiveSmartAlarmEnabled = effectiveSmartAlarmEnabled,
                )
            }

        }
    }
}

@Composable
fun TopStatusHeader(
    alarmState: AlarmContract.State,
) {
    val alarmMinutes = alarmState.alarmHour * 60 + alarmState.alarmMinute
    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_alarm_clock),
                contentDescription = "알람",
                tint = if (alarmState.isAlarmEnabled) MaterialTheme.colorScheme.primary else Color.LightGray.copy(
                    0.4f
                ),
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = if (alarmState.isAlarmEnabled) {
                    val smartStart =
                        (alarmMinutes - alarmState.selectedSmartAlarmRange).toLocalDateTime()
                    val smartEnd = alarmMinutes.toLocalDateTime()
                    if (alarmState.isSmartAlarmEnabled) "${smartStart.to24TimeString()} ~ ${smartEnd.to24TimeString()}"
                    else smartEnd.to24TimeString()
                } else "알람 꺼짐",
                style = MaterialTheme.typography.sectionTitle,
                color = Color.White
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomStatusRow(
    alarmState: AlarmContract.State,
    selectedAlarm: Alarm.Sound,
    effectiveVibrationEnabled: Boolean,
    effectiveSmartAlarmEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BouncingPreviewIcon(
                isPlaying = alarmState.isAlarmPreviewPlaying,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                modifier = Modifier.basicMarquee(),
                text = stringResource(ResourceMapper.getAlarmTitleRes(selectedAlarm.id)),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconBadge(
                icons = listOf(painterResource(Res.drawable.ic_vibration)),
                enabled = effectiveVibrationEnabled,
            )
            IconBadge(
                icons = listOf(painterResource(Res.drawable.ic_smart_alarm)),
                enabled = effectiveSmartAlarmEnabled,
            )
        }
    }
}

@Composable
fun BouncingPreviewIcon(
    isPlaying: Boolean,
    tint: Color,
) {
    val offsetY = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // 위아래로 무한 반복 바운스
            offsetY.animateTo(
                targetValue = -6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            )
        }
    }

    Icon(
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                translationY = offsetY.value
            },
        painter = painterResource(Res.drawable.ic_music_note),
        contentDescription = null,
        tint = tint
    )
}

@Composable
fun IconBadge(
    icons: List<Painter>,
    enabled: Boolean,
) {
    val inactiveColor = Color.LightGray.copy(0.4f)
    val activeColor = MaterialTheme.colorScheme.primary

    when {
        icons.isEmpty() -> Unit
        else -> {
            Box(
                modifier = Modifier
                    .size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = icons[0],
                    contentDescription = null,
                    tint = if (enabled) activeColor else inactiveColor
                )
                if (!enabled) DiagonalSlashOverlay(color = inactiveColor)
            }
        }
    }
}

@Composable
fun DiagonalSlashOverlay(color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val inset = size.width * 0.2f
        drawLine(
            color = color,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun AlarmTimeSection(
    alarmState: AlarmContract.State,
    onChangeAlarmHour: (Int) -> Unit,
    onChangeAlarmMinute: (Int, Int) -> Unit,
    onToggleRecommend: (Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (alarmState.isRecommendEnabled) "기상시각 추천 켜짐" else "기상시각 추천 꺼짐",
                style = MaterialTheme.typography.caption,
                color = SleepTheme.textColors.primary
            )
            Switch(
                checked = alarmState.isRecommendEnabled,
                onCheckedChange = { onToggleRecommend(it) }
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CommonTimePicker(
                isEnabled = alarmState.isAlarmEnabled && !alarmState.isRecommendEnabled,
                items = (0..23).toList(),
                selectedValue = alarmState.alarmHour,
                onValueChanged = { value, _ -> onChangeAlarmHour(value) }
            )
            Box(contentAlignment = Alignment.Center) {
                CircleCanvas(isEnabled = alarmState.isAlarmEnabled && !alarmState.isRecommendEnabled)
            }
            CommonTimePicker(
                isEnabled = alarmState.isAlarmEnabled && !alarmState.isRecommendEnabled,
                items = (0..59).toList(),
                selectedValue = alarmState.alarmMinute,
                onValueChanged = { value, index -> onChangeAlarmMinute(value, index) }
            )
        }
    }
}
@Composable
fun BedtimePickerSection(
    hour: Int,
    minute: Int,
    onTimeChanged: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CommonTimePicker(
                isEnabled = true,
                items = (0..23).toList(),
                selectedValue = hour,
                onValueChanged = { value, _ -> onTimeChanged(value, minute) }
            )
            Text(
                text = ":",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            CommonTimePicker(
                isEnabled = true,
                items = (0..59).toList(),
                selectedValue = minute,
                onValueChanged = { value, _ -> onTimeChanged(hour, value) }
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommonTimePicker(
    isEnabled: Boolean,
    items: List<Int>,
    selectedValue: Int,
    onValueChanged: (value: Int, globalIndex: Int) -> Unit,
) {
    val itemHeight = 40.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val visibleCount = 3
    val centerIndexOffset = visibleCount / 2

    val listState = rememberLazyListState()
    val flingBehavior = rememberLimitedSnapFlingBehavior(listState)
    val shadowColor = MaterialTheme.colorScheme.primary

    val totalCount = Int.MAX_VALUE
    val middle = totalCount / 2

    val isDragged by listState.interactionSource.collectIsDraggedAsState()

    var lastEmittedIndex by remember { mutableStateOf<Int?>(null) }
    val latestOnValueChanged by rememberUpdatedState(onValueChanged)

    val currentCenterIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) null
            else {
                val viewportCenter =
                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val centerItem =
                    visibleItems.minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }
                centerItem?.index
            }
        }
    }

    val isUserInteracting by remember {
        derivedStateOf { isDragged || listState.isScrollInProgress }
    }
    LaunchedEffect(Unit) {
        val baseIndex = items.indexOf(selectedValue).coerceAtLeast(0)
        val startIndex = middle - (middle % items.size) + baseIndex
        listState.scrollToItem((startIndex - centerIndexOffset).coerceAtLeast(0))
    }
    LaunchedEffect(selectedValue) {
        if (isUserInteracting) return@LaunchedEffect

        val centerIndex = currentCenterIndex
        val currentCenterValue = centerIndex?.let { items[it % items.size] }

        if (currentCenterValue != selectedValue) {
            val baseIndex = items.indexOf(selectedValue).coerceAtLeast(0)
            val targetIndex = middle - (middle % items.size) + baseIndex
            listState.animateScrollToItem((targetIndex - centerIndexOffset).coerceAtLeast(0))
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val isStopped = !listState.isScrollInProgress && !isDragged
            isStopped to currentCenterIndex
        }.collect { (isStopped, centerIndex) ->
            if (isStopped && centerIndex != null) {
                val newSelectedValue = items[centerIndex % items.size]
                if (lastEmittedIndex != centerIndex) {
                    lastEmittedIndex = centerIndex
                    latestOnValueChanged(newSelectedValue, centerIndex)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .height(itemHeight * visibleCount)
            .width(48.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            userScrollEnabled = isEnabled,
            state = listState,
            flingBehavior = flingBehavior,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(
                count = totalCount,
                key = { it }
            ) { index ->
                val value = items[index % items.size]

                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val itemInfo =
                                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

                            val distance = if (itemInfo != null) {
                                val viewportCenter =
                                    (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
                                abs((itemInfo.offset + itemInfo.size / 2) - viewportCenter).toFloat()
                            } else {
                                Float.MAX_VALUE
                            }

                            val normalized = (distance / itemHeightPx).coerceIn(0f, 1f)
                            val curved = (1f - normalized).let { it * it }

                            scaleX = 1.2f + 0.6f * curved
                            scaleY = 1.2f + 0.6f * curved
                            alpha = if (isEnabled) 0.2f + 0.8f * curved else 1f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = value.toString().padStart(2, '0'),
                        style = TextStyle(
                            shadow = if (isEnabled) Shadow(
                                color = shadowColor,
                                blurRadius = 16f
                            ) else Shadow.None
                        ),
                        color = if (isEnabled) Color.White else Color.LightGray.copy(0.4f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberLimitedSnapFlingBehavior(listState: LazyListState): FlingBehavior {
    val snapFling =
        rememberSnapFlingBehavior(lazyListState = listState, snapPosition = SnapPosition.Center)
    return remember {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                val maxVelocity = 3000f
                return with(snapFling) {
                    performFling(
                        initialVelocity.coerceIn(
                            -maxVelocity,
                            maxVelocity
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun VerticalVolumeDial(
    volume: Float,
    onChangeVolume: (Float) -> Unit,
) {
    val trackHeight = 16.dp
    val trackWidth = 300.dp
    val handleSize = trackHeight * 2
    val handleSizePx = with(LocalDensity.current) { handleSize.toPx() }

    val trackColor = Color.White.copy(0.15f)
    val handleColor = Color.White
    val progressBrush = Brush.horizontalGradient(
        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    )

    var draggingVolume by remember { mutableStateOf(volume) }
    val animatedVolume = remember { Animatable(volume) }

    LaunchedEffect(volume) {
        animatedVolume.snapTo(volume)
    }

    val currentDisplayVolume =
        if (animatedVolume.isRunning) animatedVolume.value else (draggingVolume.absoluteValue)
    val chipColor = when (currentDisplayVolume) {
        in 0f..0.2f, in 0.8f..1.0f -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val statusText = when (currentDisplayVolume) {
        in 0f..0.2f -> "볼륨을 조금 더 키워보세요."
        in 0.8f..1.0f -> "볼륨이 커서 놀랄 수 있어요"
        else -> "적절한 볼륨이에요"
    }

    Column(
        modifier = Modifier.wrapContentWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = chipColor.copy(0.4f)
        ) {
            Text(
                modifier = Modifier.padding(4.dp),
                text = "${(currentDisplayVolume * 100).roundToInt()}%",
                style = MaterialTheme.typography.caption,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.caption,
            color = SleepTheme.textColors.primary
        )
        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(handleSize), // 높이를 핸들 사이즈에 맞춤
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(50.dp))
                    .background(trackColor)
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = (handleSize / 2))) {
                    val dotCount = 20
                    val spacing = size.width / dotCount
                    for (i in 0..dotCount) {
                        drawCircle(
                            color = Color.White,
                            radius = 1.2.dp.toPx(),
                            center = Offset(size.width - (i * spacing), size.height / 2)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(currentDisplayVolume)
                    .height(trackHeight)
                    .background(progressBrush, RoundedCornerShape(50.dp))
            )

            // 3. 투명한 드래그 감지 레이어 (트랙 전체 영역)
            Box(
                modifier = Modifier
                    .border(2.dp, Color.Green)
                    .fillMaxWidth()
                    .height(handleSize)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val travelRange = (trackWidth - handleSize).toPx()
                                val relativeX = (offset.x - handleSizePx / 2f)
                                val rawVolume = (relativeX / travelRange).coerceIn(0f, 1f)
                                draggingVolume = (round(rawVolume / 0.05f) * 0.05f)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val travelRange = (trackWidth - handleSize).toPx()
                                val relativeX = (change.position.x - handleSizePx / 2f)
                                val rawVolume = (relativeX / travelRange).coerceIn(0f, 1f)
                                draggingVolume = (round(rawVolume / 0.05f) * 0.05f)
                                onChangeVolume(draggingVolume)
                            },
                            onDragEnd = { onChangeVolume(draggingVolume) }
                        )
                    }
            )

            // 4. 볼륨 핸들 (트랙보다 높이가 큼)
            Surface(
                modifier = Modifier
                    .size(handleSize)
                    .offset(x = (trackWidth - handleSize) * currentDisplayVolume),
                shape = CircleShape,
                color = handleColor,
                shadowElevation = 4.dp // 💡 핸들이 돋보이도록 그림자 추가
            ) {
                Icon(
                    modifier = Modifier.padding(8.dp),
                    painter = when ((currentDisplayVolume * 100).roundToInt()) {
                        in 0..20 -> painterResource(Res.drawable.ic_volume_off)
                        in 20..60 -> painterResource(Res.drawable.ic_volume_low)
                        else -> painterResource(Res.drawable.ic_volume_high)
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview
@Composable
fun SleepSettingScreenPreview() {
    SleepAppTheme {
        SleepSettingContent(
            alarmState = AlarmContract.State(
                alarmHour = 7,
                alarmMinute = 30,
                isAlarmEnabled = true,
                selectedAlarmSound = Alarm.Sound.DEFAULT,
                appVolume = 0.5f,
                isVibrationEnabled = true,
                isSmartAlarmEnabled = true,
                selectedSmartAlarmRange = 15,
                isGradualVolumeEnabled = true,
                isAutoTrackingEnabled = true,
                selectedSleepTrackingModes = setOf(SleepTrackingMode.AUTO_PHONE)
            ),
            onChangeAlarmHour = {},
            onChangeAlarmMinute = { _, _ -> },
            onToggleAlarm = {},
            onSelectAlarmSound = {},
            onChangeVolume = {},
            onToggleVibration = {},
            onToggleSmartAlarm = {},
            onSelectSmartAlarmRange = {},
            onToggleRecommend = {},
            onToggleSleepReminder = { _ -> },
            onChangeReminderTime = { _, _ -> }
        )
    }
}
