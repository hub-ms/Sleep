package com.sleepytime.shared.ui.alarm

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepytime.shared.domain.model.Alarm
import com.sleepytime.shared.platform.CircleCanvas
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.button_sleep_save
import com.sleepytime.shared.resources.ic_alarm_clock
import com.sleepytime.shared.resources.ic_caret_right
import com.sleepytime.shared.resources.ic_help
import com.sleepytime.shared.resources.ic_pause
import com.sleepytime.shared.resources.ic_play
import com.sleepytime.shared.resources.ic_smart_alarm
import com.sleepytime.shared.resources.ic_smart_phone
import com.sleepytime.shared.resources.ic_smart_watch
import com.sleepytime.shared.resources.ic_vibration
import com.sleepytime.shared.resources.ic_volume_high
import com.sleepytime.shared.resources.ic_volume_low
import com.sleepytime.shared.resources.ic_volume_off
import com.sleepytime.shared.util.HelpItem
import com.sleepytime.shared.ui.music.MusicContract
import com.sleepytime.shared.ui.theme.SleepAppTheme
import com.sleepytime.shared.ui.theme.bodyHighlight
import com.sleepytime.shared.ui.theme.caption
import com.sleepytime.shared.ui.theme.sectionTitle
import com.sleepytime.shared.util.DateTimeUtil.toAmPmTimeString
import com.sleepytime.shared.util.DateTimeUtil.toLocalDateTime
import com.sleepytime.shared.util.ResourceMapper
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.abs

enum class Setting(val text: String) {
    ALARM("알람"), VIBRATION("진동"), SMART_ALARM("스마트 알람"), GRADUAL_VOLUME("점점 커지는 알람"), AUTO_TRACKING(
        "자동 수면 측정"
    )
}

enum class SleepTrackingMode(val text: String) {
    AUTO_PHONE("스마트폰"), AUTO_WATCH("스마트워치")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSettingContent(
    alarmState: AlarmContract.State,
    musicState: MusicContract.State,
    onChangeAlarmHour: (Int) -> Unit,
    onChangeAlarmMinute: (Int, Int) -> Unit,
    onToggleAlarm: () -> Unit,
    onToggleAlarmPreview: () -> Unit,
    onSelectAlarmSound: (Alarm.Sound) -> Unit,
    onChangeVolume: (Float) -> Unit,
    onToggleVibration: () -> Unit,
    onToggleSmartAlarm: () -> Unit,
    onSelectSmartAlarmRange: (Int) -> Unit,
    onToggleGradualVolume: () -> Unit,
    onToggleAutoTracking: () -> Unit,
    onSelectSleepTrackingMode: (SleepTrackingMode) -> Unit,
    onSave: () -> Unit
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
            AlarmTopStatusSection(alarmState = alarmState)

            AlarmTimeSection(
                alarmState = alarmState,
                onChangeAlarmHour = onChangeAlarmHour,
                onChangeAlarmMinute = onChangeAlarmMinute
            )

            BottomSection(
                alarmState = alarmState,
                musicState = musicState,
                onToggleAlarm = onToggleAlarm,
                onToggleAlarmPreview = onToggleAlarmPreview,
                onSelectAlarmSound = onSelectAlarmSound,
                onChangeVolume = onChangeVolume,
                onToggleVibration = onToggleVibration,
                onToggleSmartAlarm = onToggleSmartAlarm,
                onSelectSmartAlarmRange = onSelectSmartAlarmRange,
                onToggleGradualVolume = onToggleGradualVolume,
                onToggleAutoTracking = onToggleAutoTracking,
                onSelectSleepTrackingMode = onSelectSleepTrackingMode
            )
        }
        SaveButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(16.dp)
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            onSaveButtonClicked = onSave
        )
    }
}

@Composable
@ExperimentalMaterial3Api
fun AlarmTopStatusSection(
    alarmState: AlarmContract.State,
    onSleepSettingClicked: () -> Unit = {}
) {
    val alarmMinutes = alarmState.alarmHour * 60 + alarmState.alarmMinute
    val effectiveVibrationEnabled =
        alarmState.isAlarmEnabled && (alarmState.isVibrationEnabled || alarmState.appVolume == 0f)
    val effectiveSmartAlarmEnabled = alarmState.isAlarmEnabled && alarmState.isSmartAlarmEnabled
    val effectiveGradualVolumeEnabled =
        alarmState.isAlarmEnabled && alarmState.isGradualVolumeEnabled

    var showSheet by remember { mutableStateOf(false) }

    if (showSheet) {
        HelpModalSheet(
            onDismiss = { showSheet = false },
            helpItems = listOf(
                HelpItem(
                    icon = listOf(painterResource(Res.drawable.ic_vibration)),
                    question = "${Setting.VIBRATION.text}은 무엇인가요?",
                    answer = "알람이 울릴 때 진동과 함께 사용해서 깨어나는 데 도움을 줍니다."
                ),
                HelpItem(
                    icon = listOf(painterResource(Res.drawable.ic_smart_alarm)),
                    question = "${Setting.SMART_ALARM.text}은 무엇인가요?",
                    answer = "사용자가 설정한 스마트 알람 범위 내에서 얕은 수면일 때 알람을 울립니다."
                ),
                HelpItem(
                    icon = listOf(
                        painterResource(Res.drawable.ic_volume_off),
                        painterResource(Res.drawable.ic_volume_low),
                        painterResource(Res.drawable.ic_volume_high)
                    ),
                    question = "${Setting.GRADUAL_VOLUME.text}은 무엇인가요?",
                    answer = "알람 소리가 사용자가 설정한 음량까지 점진적으로 증가합니다."
                ),
                HelpItem(
                    icon = listOf(
                        painterResource(Res.drawable.ic_smart_phone),
                        painterResource(Res.drawable.ic_smart_watch)
                    ),
                    question = "${Setting.AUTO_TRACKING.text}은 무엇인가요?",
                    answer = "선택한 기기를 사용하여 설정한 시간에 수면을 자동으로 측정합니다."
                ),
            ),
            intervalMs = 1500L,
        )
    }

    Box(
        modifier = Modifier
            .clickable { onSleepSettingClicked() }
            .fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopStatusHeader(
                alarmState = alarmState,
                onHelpClick = { showSheet = true }
            )
            BottomStatusRow(
                alarmState = alarmState,
                effectiveVibrationEnabled = effectiveVibrationEnabled,
                effectiveSmartAlarmEnabled = effectiveSmartAlarmEnabled,
                effectiveGradualVolumeEnabled = effectiveGradualVolumeEnabled
            )
        }
    }
}

@Composable
@ExperimentalMaterial3Api
private fun HelpModalSheet(
    onDismiss: () -> Unit,
    helpItems: List<HelpItem>,
    intervalMs: Long? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            helpItems.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HelpItemIcons(
                            icons = item.icon,
                            tint = MaterialTheme.colorScheme.primary,
                            intervalMs = intervalMs
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = item.question,
                            style = MaterialTheme.typography.bodyHighlight,
                            color = Color.White
                        )
                    }
                    Text(
                        text = item.answer,
                        style = MaterialTheme.typography.caption,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HelpItemIcons(
    icons: List<Painter?>,
    tint: Color,
    intervalMs: Long?,
) {
    when {
        icons.size == 1 -> {
            icons[0]?.let {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = it,
                    contentDescription = null,
                    tint = tint,
                )
            }
        }

        intervalMs != null && icons.size == 3 -> {
            val infiniteTransition = rememberInfiniteTransition()
            val progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = icons.size.toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = intervalMs.toInt()),
                    repeatMode = RepeatMode.Restart
                )
            )
            val currentIconIndex = progress.toInt().coerceIn(0, icons.lastIndex)
            icons[currentIconIndex]?.let {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = it,
                    contentDescription = null,
                    tint = tint,
                )
            }
        }

        else -> {
            Row {
                icons.forEachIndexed { index, painter ->
                    painter?.let {
                        Icon(
                            modifier = Modifier
                                .size(24.dp)
                                .offset(x = if (index > 0) (-8).dp else 0.dp),
                            painter = it,
                            contentDescription = null,
                            tint = tint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopStatusHeader(
    alarmState: AlarmContract.State,
    onHelpClick: () -> Unit,
) {
    val alarmMinutes = alarmState.alarmHour * 60 + alarmState.alarmMinute
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    val smartStart = (alarmMinutes - alarmState.selectedSmartAlarmRange).toLocalDateTime()
                    val smartEnd = alarmMinutes.toLocalDateTime()
                    if (alarmState.isSmartAlarmEnabled) {
                        "${smartStart.toAmPmTimeString()} ~ ${smartEnd.toAmPmTimeString()}"
                    } else {
                        smartEnd.toAmPmTimeString()
                    }
                } else "알람 꺼짐",
                style = MaterialTheme.typography.sectionTitle,
                color = Color.White
            )
        }


        IconButton(
            onClick = onHelpClick,
            modifier = Modifier.size(36.dp).align(Alignment.TopEnd)
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_help),
                contentDescription = "도움말",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun BottomStatusRow(
    alarmState: AlarmContract.State,
    effectiveVibrationEnabled: Boolean,
    effectiveSmartAlarmEnabled: Boolean,
    effectiveGradualVolumeEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconBadge(
                icons = listOf(painterResource(Res.drawable.ic_vibration)),
                enabled = effectiveVibrationEnabled,
            )
            IconBadge(
                icons = listOf(painterResource(Res.drawable.ic_smart_alarm)),
                enabled = effectiveSmartAlarmEnabled,
            )
            IconBadge(
                icons = listOf(
                    painterResource(Res.drawable.ic_volume_off),
                    painterResource(Res.drawable.ic_volume_low),
                    painterResource(Res.drawable.ic_volume_high)
                ),
                enabled = effectiveGradualVolumeEnabled,
                intervalMs = 1500L
            )
        }

        IconBadge(
            alarmState = alarmState,
            icons = listOf(
                painterResource(Res.drawable.ic_smart_phone),
                painterResource(Res.drawable.ic_smart_watch),
            ),
            enabled = alarmState.isAutoTrackingEnabled
        )
    }
}

@Composable
fun IconBadge(
    alarmState: AlarmContract.State = AlarmContract.State(),
    icons: List<Painter>,
    enabled: Boolean,
    intervalMs: Long? = null,
) {
    val inactiveColor = Color.LightGray.copy(0.4f)
    val activeColor = MaterialTheme.colorScheme.primary

    when {
        icons.isEmpty() -> Unit
        else -> {
            if (icons.size == 1) {
                Box(
                    modifier = Modifier
                        .size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = icons[0],
                        contentDescription = null,
                        tint = if(enabled) activeColor else inactiveColor
                    )
                    if(!enabled) {
                        DiagonalSlashOverlay(color = inactiveColor)
                    }
                }
            } else if (intervalMs != null) {
                val infiniteTransition = rememberInfiniteTransition()
                val progress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = icons.size.toFloat(),
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = intervalMs.toInt()),
                        repeatMode = RepeatMode.Restart
                    )
                )
                val currentIconIndex = progress.toInt().coerceIn(0, icons.lastIndex)
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = icons[currentIconIndex],
                    contentDescription = null,
                    tint = if(enabled) activeColor else inactiveColor
                )
            } else {
                val phoneSelected = alarmState.selectedSleepTrackingModes.contains(SleepTrackingMode.AUTO_PHONE)
                val watchSelected = alarmState.selectedSleepTrackingModes.contains(SleepTrackingMode.AUTO_WATCH)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = icons[0],
                            contentDescription = null,
                            tint = if (phoneSelected) activeColor else inactiveColor
                        )
                        if (!phoneSelected) DiagonalSlashOverlay(color = inactiveColor)
                    }
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = icons[1],
                            contentDescription = null,
                            tint = if (watchSelected) activeColor else inactiveColor
                        )
                        if (!watchSelected) DiagonalSlashOverlay(color = inactiveColor)
                    }
                }
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
) {
    MaterialTheme.colorScheme.primary.toArgb()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AlarmTimePicker(
            alarmState = alarmState,
            items = (0..23).toList(),
            selectedValue = alarmState.alarmHour,
            onValueChanged = { value, _ -> onChangeAlarmHour(value) }
        )
        Box(contentAlignment = Alignment.Center) {
            CircleCanvas(isEnabled = alarmState.isAlarmEnabled)
        }
        AlarmTimePicker(
            alarmState = alarmState,
            items = (0..11).map { it * 5 },
            selectedValue = alarmState.alarmMinute,
            onValueChanged = { value, globalIndex -> onChangeAlarmMinute(value, globalIndex) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlarmTimePicker(
    alarmState: AlarmContract.State,
    items: List<Int>,
    selectedValue: Int,
    onValueChanged: (value: Int, globalIndex: Int) -> Unit,
) {
    val density = LocalDensity.current
    val itemHeight = 40.dp
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val visibleCount = 3
    visibleCount / 2

    val listState = rememberLazyListState()
    val flingBehavior = rememberLimitedSnapFlingBehavior(listState)
    val shadowColor = MaterialTheme.colorScheme.primary

    val totalCount = Int.MAX_VALUE
    val middle = totalCount / 2

    LaunchedEffect(Unit) {
        val baseIndex = items.indexOf(selectedValue).coerceAtLeast(0)
        val startIndex = middle - (middle % items.size) + baseIndex
        listState.scrollToItem(startIndex)
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { isScrolling ->
            if (!isScrolling) {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isNotEmpty()) {
                    val viewportCenter =
                        (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                    val centerItem =
                        visibleItems.minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }
                    centerItem?.let { item ->
                        onValueChanged(items[item.index % items.size], item.index)
                    }
                }
            }
        }
    }

    val viewportCenter by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            (info.viewportStartOffset + info.viewportEndOffset) / 2

        }
    }



    Box(
        modifier = Modifier.height(itemHeight * visibleCount).width(30.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            userScrollEnabled = alarmState.isAlarmEnabled,
            state = listState,
            flingBehavior = flingBehavior,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(totalCount) { index ->
                val value = items[index % items.size]
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val vc = viewportCenter
                            val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == index}
                            val distance = if (itemInfo != null) {
                                abs((itemInfo.offset + itemInfo.size / 2) - vc).toFloat()
                            } else {
                                Float.MAX_VALUE
                            }
                            val normalized = (distance / itemHeightPx).coerceIn(0f, 1f)
                            val curved = (1f - normalized).let { it * it }

                            scaleX = 1.2f + 0.6f * curved
                            scaleY = 1.2f + 0.6f * curved
                            alpha = if (alarmState.isAlarmEnabled) 0.2f + 0.8f * curved else 1f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = value.toString().padStart(2, '0'),
                        style = TextStyle(
                            shadow = if (alarmState.isAlarmEnabled) Shadow(
                                color = shadowColor,
                                blurRadius = 16f
                            ) else Shadow.None
                        ),
                        color = if (alarmState.isAlarmEnabled) Color.White else Color.LightGray.copy(
                            0.4f
                        ),
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
@ExperimentalMaterial3Api
fun BottomSection(
    alarmState: AlarmContract.State,
    musicState: MusicContract.State,
    onToggleAlarm: () -> Unit,
    onToggleAlarmPreview: () -> Unit,
    onSelectAlarmSound: (Alarm.Sound) -> Unit,
    onChangeVolume: (Float) -> Unit,
    onToggleVibration: () -> Unit,
    onToggleSmartAlarm: () -> Unit,
    onSelectSmartAlarmRange: (Int) -> Unit,
    onToggleGradualVolume: () -> Unit,
    onToggleAutoTracking: () -> Unit,
    onSelectSleepTrackingMode: (SleepTrackingMode) -> Unit
) {
    val alarmSounds = remember {
        listOf(
            Alarm.Sound("bird", ResourceMapper.getAlarmTitleRes("bird"), "files/alarm_bird.mp3",0.8f),
            Alarm.Sound("cricket", ResourceMapper.getAlarmTitleRes("cricket"), "files/alarm_cricket.mp3",0.8f),
            Alarm.Sound("piano", ResourceMapper.getAlarmTitleRes("piano"), "files/alarm_piano.mp3",0.8f),
            Alarm.Sound("wave", ResourceMapper.getAlarmTitleRes("wave"), "files/alarm_wave.mp3",0.8f),
            Alarm.Sound("upbeat", ResourceMapper.getAlarmTitleRes("upbeat"), "files/alarm_upbeat.mp3",0.8f)
        )
    }
    val scrollState = rememberScrollState()
    var showSoundSelection by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
    ) {
        if (showSoundSelection) {
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(alarmSounds) { sound ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onSelectAlarmSound(sound)
                            showSoundSelection = false
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(sound.titleRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                        RadioButton(
                            selected = sound.titleRes == alarmState.selectedAlarmSound,
                            onClick = { onSelectAlarmSound(sound); showSoundSelection = false },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AdvancedSettingToggle(
                    isEnabled = alarmState.isAlarmEnabled,
                    label = Setting.ALARM,
                    onToggle = onToggleAlarm
                )

                if (alarmState.isAlarmEnabled) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onToggleAlarmPreview) {
                            Icon(
                                if (musicState.isPlaying) painterResource(Res.drawable.ic_pause) else painterResource(
                                    Res.drawable.ic_play
                                ), null, tint = Color.White
                            )
                        }
                        Text(
                            text = stringResource(alarmState.selectedAlarmSound),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        IconButton(onClick = { showSoundSelection = true }) {
                            Icon(painterResource(Res.drawable.ic_caret_right), null, tint = Color.White)
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            painterResource(if (alarmState.appVolume == 0f) Res.drawable.ic_volume_off else Res.drawable.ic_volume_high),
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        VolumeSegmentIndicator(
                            volume = alarmState.appVolume,
                            onChangeVolume = onChangeVolume
                        )
                        Text(
                            "${(alarmState.appVolume * 100).toInt()}%",
                            style = MaterialTheme.typography.caption,
                            color = Color.White,
                            modifier = Modifier.width(36.dp)
                        )
                    }

                    AdvancedSettingToggle(
                        alarmState = alarmState,
                        isEnabled = alarmState.isVibrationEnabled,
                        label = Setting.VIBRATION,
                        onToggle = onToggleVibration
                    )
                    if (alarmState.appVolume == 0f) Text(
                        "음량이 0이면 진동이 항상 켜집니다",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colorScheme.primary
                    )

                    AdvancedSettingToggle(
                        isEnabled = alarmState.isSmartAlarmEnabled,
                        label = Setting.SMART_ALARM,
                        onToggle = onToggleSmartAlarm
                    )
                    if (alarmState.isSmartAlarmEnabled) {
                        ItemCard(
                            items = alarmState.smartAlarmRangeList,
                            selectedValue = alarmState.selectedSmartAlarmRange,
                            onValueSelected = onSelectSmartAlarmRange,
                            valueLabel = { "${it}분" })
                    }

                    AdvancedSettingToggle(
                        isEnabled = alarmState.isGradualVolumeEnabled,
                        label = Setting.GRADUAL_VOLUME,
                        onToggle = onToggleGradualVolume
                    )
                }

                AdvancedSettingToggle(
                    isEnabled = alarmState.isAutoTrackingEnabled,
                    label = Setting.AUTO_TRACKING,
                    onToggle = onToggleAutoTracking
                )
                if (alarmState.isAutoTrackingEnabled) {
                    MultiItemCard(
                        items = alarmState.sleepTrackingModeList,
                        selectedValues = alarmState.selectedSleepTrackingModes,
                        onValueChanged = onSelectSleepTrackingMode,
                        valueLabel = { it.text })
                }
            }
        }
    }
}

@Composable
fun VolumeSegmentIndicator(volume: Float, onChangeVolume: (Float) -> Unit) {
    val segments = 10
    Row(
        modifier = Modifier.height(32.dp).pointerInput(Unit) {
            detectDragGestures { change, _ ->
                val progress = (change.position.x / size.width).coerceIn(0f, 1f)
                onChangeVolume((progress * segments).toInt() / segments.toFloat())
            }
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(segments) { index ->
            val isFilled = index < (volume * segments).toInt()
            Box(
                modifier = Modifier.weight(1f).height(12.dp + (index * 2).dp).background(
                    color = if (isFilled) MaterialTheme.colorScheme.primary else Color.White.copy(
                        alpha = 0.2f
                    ),
                    shape = RoundedCornerShape(2.dp)
                ).clickable { onChangeVolume((index + 1) / segments.toFloat()) }
            )
        }
    }
}

@Composable
fun AdvancedSettingToggle(
    alarmState: AlarmContract.State? = null,
    isEnabled: Boolean,
    label: Setting,
    onToggle: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label.text, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun ItemCard(
    items: List<Int>,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit,
    valueLabel: (Int) -> String
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { value ->
            val isSelected = value == selectedValue
            Surface(
                modifier = Modifier.weight(1f).clickable { onValueSelected(value) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(
                    alpha = 0.1f
                ),
                border = BorderStroke(1.dp, if (isSelected) Color.White else Color.Transparent)
            ) {
                Text(
                    valueLabel(value),
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun MultiItemCard(
    items: List<SleepTrackingMode>,
    selectedValues: Set<SleepTrackingMode>,
    onValueChanged: (SleepTrackingMode) -> Unit,
    valueLabel: (SleepTrackingMode) -> String
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { mode ->
            val isSelected = selectedValues.contains(mode)
            Surface(
                modifier = Modifier.weight(1f).clickable { onValueChanged(mode) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(
                    alpha = 0.1f
                ),
                border = BorderStroke(1.dp, if (isSelected) Color.White else Color.Transparent)
            ) {
                Text(
                    valueLabel(mode),
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun SaveButton(modifier: Modifier, onSaveButtonClicked: () -> Unit) {
    Button(
        onClick = onSaveButtonClicked,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ),
    ) {
        Text(
            text = stringResource(Res.string.button_sleep_save),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge
        )
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
                selectedAlarmSound = ResourceMapper.getAlarmTitleRes("bird"),
                appVolume = 0.5f,
                isVibrationEnabled = true,
                isSmartAlarmEnabled = true,
                selectedSmartAlarmRange = 20,
                isGradualVolumeEnabled = true,
                isAutoTrackingEnabled = true,
                selectedSleepTrackingModes = setOf(SleepTrackingMode.AUTO_PHONE)
            ),
            musicState = MusicContract.State(),
            onChangeAlarmHour = {},
            onChangeAlarmMinute = { _, _ -> },
            onToggleAlarm = {},
            onToggleAlarmPreview = {},
            onSelectAlarmSound = {},
            onChangeVolume = {},
            onToggleVibration = {},
            onToggleSmartAlarm = {},
            onSelectSmartAlarmRange = {},
            onToggleGradualVolume = {},
            onToggleAutoTracking = {},
            onSelectSleepTrackingMode = {},
            onSave = {}
        )
    }
}
