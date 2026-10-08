package com.soundsleeper.app.ui.alarm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.domain.model.Alarm
import com.soundsleeper.app.enum_.PermissionType
import com.soundsleeper.app.platform.CircleCanvas
import com.soundsleeper.app.platform.rememberPermissionHandler
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.alarm_disabled_hint
import com.soundsleeper.app.resources.alarm_sound_label
import com.soundsleeper.app.resources.alarm_sound_selection_description
import com.soundsleeper.app.resources.alarm_time_setting_description
import com.soundsleeper.app.resources.bedtime_reminder_label
import com.soundsleeper.app.resources.bedtime_setting_description
import com.soundsleeper.app.resources.ic_alarm
import com.soundsleeper.app.resources.ic_caret_right
import com.soundsleeper.app.resources.ic_check
import com.soundsleeper.app.resources.ic_music_note
import com.soundsleeper.app.resources.ic_sleep
import com.soundsleeper.app.resources.ic_volume_high
import com.soundsleeper.app.resources.ic_volume_low
import com.soundsleeper.app.resources.ic_volume_mute
import com.soundsleeper.app.resources.selected_sound_description
import com.soundsleeper.app.resources.setting_alarm
import com.soundsleeper.app.resources.setting_smart_alarm
import com.soundsleeper.app.resources.setting_vibration
import com.soundsleeper.app.resources.sleep_reminder_off_message
import com.soundsleeper.app.resources.sleep_reminder_on_message
import com.soundsleeper.app.resources.sleep_setting_open_label
import com.soundsleeper.app.resources.smart_alarm_range_minutes
import com.soundsleeper.app.resources.volume_level_label
import com.soundsleeper.app.resources.wake_label
import com.soundsleeper.app.ui.component.SegmentedControl
import com.soundsleeper.app.ui.component.ToggleSettingItem
import com.soundsleeper.app.ui.theme.SleepTheme.background
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.SleepTheme.secondary
import com.soundsleeper.app.ui.theme.SleepTheme.surface
import com.soundsleeper.app.ui.theme.bodyHighlight
import com.soundsleeper.app.ui.theme.bodyText
import com.soundsleeper.app.ui.theme.caption
import com.soundsleeper.app.ui.theme.sectionTitle
import com.soundsleeper.app.util.DateTimeUtil.to24TimeString
import com.soundsleeper.app.util.DateTimeUtil.toLocalDateTime
import com.soundsleeper.app.util.ResourceMapper
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

enum class Setting(val labelRes: StringResource) {
    ALARM(Res.string.setting_alarm), VIBRATION(Res.string.setting_vibration), SMART_ALARM(Res.string.setting_smart_alarm)
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
    onChangeReminderHour: (Int) -> Unit,
    onChangeReminderMinute: (Int, Int) -> Unit,
    onToggleAlarm: () -> Unit,
    onSelectAlarmSound: (Alarm.Sound) -> Unit,
    onChangeVolume: (Float) -> Unit,
    onToggleVibration: () -> Unit,
    onToggleSmartAlarm: () -> Unit,
    onSelectSmartAlarmRange: (Int) -> Unit,
    onToggleSleepReminder: (Boolean) -> Unit,
    onStopAlarmPreview: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .navigationBarsPadding()
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlarmStatusCard(
                alarmState = alarmState,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PickerStateIcon(
                        painter = painterResource(Res.drawable.ic_sleep),
                        contentDescription = stringResource(Res.string.bedtime_setting_description),
                        activeColor = secondary,
                        isEnabled = alarmState.isReminderEnabled,
                    )
                    CommonTimeSection(
                        pickerKey = "reminder",
                        selectedHour = alarmState.reminderHour,
                        selectedMinute = alarmState.reminderMinute,
                        isEnabled = alarmState.isReminderEnabled,
                        shadowColor = secondary,
                        onChangeHour = onChangeReminderHour,
                        onChangeMinute = onChangeReminderMinute,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PickerStateIcon(
                        painter = painterResource(Res.drawable.ic_alarm),
                        contentDescription = stringResource(Res.string.alarm_time_setting_description),
                        activeColor = primary,
                        isEnabled = alarmState.isAlarmEnabled,
                    )
                    CommonTimeSection(
                        pickerKey = "alarm",
                        selectedHour = alarmState.alarmHour,
                        selectedMinute = alarmState.alarmMinute,
                        isEnabled = alarmState.isAlarmEnabled,
                        shadowColor = primary,
                        onChangeHour = onChangeAlarmHour,
                        onChangeMinute = onChangeAlarmMinute,
                    )
                }
            }
            AlarmTopStatusSection(
                alarmState = alarmState,
                onToggleAlarm = onToggleAlarm,
                onSelectAlarmSound = onSelectAlarmSound,
                onChangeVolume = onChangeVolume,
                onToggleVibration = onToggleVibration,
                onToggleSmartAlarm = onToggleSmartAlarm,
                onSelectSmartAlarmRange = onSelectSmartAlarmRange,
                onToggleSleepReminder = onToggleSleepReminder,
                onStopAlarmPreview = onStopAlarmPreview,
            )
        }
    }
}

@Composable
@ExperimentalMaterial3Api
fun AlarmTopStatusSection(
    alarmState: AlarmContract.State,
    onToggleAlarm: () -> Unit,
    onSelectAlarmSound: (Alarm.Sound) -> Unit,
    onChangeVolume: (Float) -> Unit,
    onToggleVibration: () -> Unit,
    onToggleSmartAlarm: () -> Unit,
    onSelectSmartAlarmRange: (Int) -> Unit,
    onToggleSleepReminder: (Boolean) -> Unit,
    onStopAlarmPreview: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    var showSoundSelection by remember { mutableStateOf(false) }
    val soundSheetState = rememberModalBottomSheetState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissionHandler = rememberPermissionHandler(onResult = { _, _ -> })
    val alarmTitle =
        stringResource(ResourceMapper.getAlarmTitleRes(alarmState.selectedAlarmSound.id))
    val sleepReminderOnMessage = stringResource(Res.string.sleep_reminder_on_message)
    val sleepReminderOffMessage = stringResource(Res.string.sleep_reminder_off_message)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(
                color = surface,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(8.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToggleSettingItem(
                title = stringResource(Res.string.bedtime_reminder_label),
                checked = alarmState.isReminderEnabled,
                onCheckedChange = {
                    if (it) {
                        permissionHandler.request(PermissionType.NOTIFICATION)
                    }
                    onToggleSleepReminder(it)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (it) sleepReminderOnMessage else sleepReminderOffMessage,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )
            ToggleSettingItem(
                title = stringResource(Setting.ALARM.labelRes),
                checked = alarmState.isAlarmEnabled,
                onCheckedChange = {
                    onToggleAlarm()
                }
            )

            if (alarmState.isAlarmEnabled) {
                // 선택된 사운드만 보여주고 바꿀 방법이 없었다. 행 전체를 눌러 바텀시트를 연다.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSoundSelection = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.alarm_sound_label),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = alarmTitle,
                            style = MaterialTheme.typography.bodyText,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Icon(
                            modifier = Modifier.size(20.dp),
                            painter = painterResource(Res.drawable.ic_caret_right),
                            contentDescription = stringResource(Res.string.alarm_sound_selection_description),
                            tint = Color.White
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.volume_level_label),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                VerticalVolumeDial(
                    volume = alarmState.appVolume,
                    onChangeVolume = onChangeVolume,
                )
                ToggleSettingItem(
                    title = stringResource(Setting.VIBRATION.labelRes),
                    checked = alarmState.isVibrationEnabled,
                    onCheckedChange = {
                        onToggleVibration()
                    }
                )
                ToggleSettingItem(
                    title = stringResource(Setting.SMART_ALARM.labelRes),
                    checked = alarmState.isSmartAlarmEnabled,
                    onCheckedChange = {
                        onToggleSmartAlarm()
                    }
                )
                if (alarmState.isSmartAlarmEnabled) {
                    SegmentedControl(
                        items = alarmState.smartAlarmRangeList,
                        selectedItem = alarmState.selectedSmartAlarmRange,
                        onSelectItem = onSelectSmartAlarmRange,
                    ) {
                        stringResource(Res.string.smart_alarm_range_minutes, it)
                    }
                }

            }


        }
    }

    if (showSoundSelection) {
        ModalBottomSheet(
            onDismissRequest = {
                showSoundSelection = false
                onStopAlarmPreview()
            },
            sheetState = soundSheetState,
            containerColor = surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            Color.White,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.alarm_sound_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                alarmState.alarmSounds.forEach { sound ->
                    val title = stringResource(ResourceMapper.getAlarmTitleRes(sound.id))
                    Row(
                        modifier = Modifier
                            .background(
                                if (sound.id == alarmState.selectedAlarmSound.id)
                                    primary.copy(alpha = 0.1f)
                                else Color.Transparent
                            )
                            .fillMaxWidth()
                            .clickable { onSelectAlarmSound(sound) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyText,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (sound.id == alarmState.selectedAlarmSound.id) {
                            Icon(
                                modifier = Modifier.size(24.dp),
                                painter = painterResource(Res.drawable.ic_check),
                                contentDescription = stringResource(Res.string.selected_sound_description),
                                tint = primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 시각 피커 옆 아이콘.
 *
 * 예전에는 피커가 꺼져 있어도 아이콘만 원래 색으로 남아 켜진 것처럼 보였다.
 * 꺼지면 숫자와 같이 흐려지고, 켜지면 하단 탭바의 선택 아이콘과 같은 방식
 * (흐린 복제본을 아래 깔아 번지게 하는 것)으로 주위를 빛나게 한다.
 */
@Composable
fun PickerStateIcon(
    painter: Painter,
    contentDescription: String,
    activeColor: Color,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (isEnabled) activeColor else Color.White.copy(0.4f),
        label = "pickerIconTint"
    )
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isEnabled) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = activeColor,
                modifier = Modifier.size(24.dp).blur(4.dp)
            )
        }
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 취침과 기상 상태를 나란히 보여 주는 카드.
 *
 * 예전에는 한 카드가 기상 시각과 알람 설정만 담고, 취침 시각은 화면 아래 시간 피커와
 * "취침 시각 알림" 토글에만 흩어져 있었다. 그래서 카드만 보면 몇 시에 자기로 했는지 알 수
 * 없었다. 수면은 취침과 기상 두 끝이 한 쌍이므로 둘을 같은 높이에 나란히 놓는다.
 *
 * [onClick] 은 홈에서 쓴다. 홈에서는 이 카드가 수면 설정으로 가는 진입점이고, 설정 화면
 * 안에서는 이미 그 화면이라 null 로 두어 클릭되지 않게 한다.
 */
@Composable
fun AlarmStatusCard(
    alarmState: AlarmContract.State,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(
                color = surface,
                shape = RoundedCornerShape(16.dp)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = stringResource(Res.string.sleep_setting_open_label)) { onClick() }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BedtimeStatusColumn(
                    modifier = Modifier.weight(1f),
                    alarmState = alarmState,
                )
                WakeStatusColumn(
                    modifier = Modifier.weight(2f),
                    alarmState = alarmState,
                )
            }
            // 알람음은 취침·기상 어느 쪽에도 속하지 않으므로 두 칸 아래 전폭 한 줄로 둔다.
            AlarmSoundRow(
                alarmState = alarmState,
                selectedAlarm = alarmState.selectedAlarmSound,
            )
        }
    }
}

/** 취침 쪽. 시각과 취침 알림 여부만 담는다. */
@Composable
private fun BedtimeStatusColumn(
    alarmState: AlarmContract.State,
    modifier: Modifier = Modifier,
) {
    val reminderMinutes = (alarmState.reminderHour * 60 + alarmState.reminderMinute).toLocalDateTime()
    val reminderString = if (!alarmState.isReminderEnabled) "꺼짐" else reminderMinutes.to24TimeString()
    StatusColumn(
        modifier = modifier,
        icon = painterResource(Res.drawable.ic_sleep),
        iconTint = if (alarmState.isReminderEnabled) secondary else Color.White.copy(0.4f),
        label = stringResource(Res.string.bedtime_reminder_label),
        value = reminderString,
    )
}
@Composable
private fun WakeStatusColumn(
    alarmState: AlarmContract.State,
    modifier: Modifier = Modifier,
) {
    val alarmMinutes = alarmState.alarmHour * 60 + alarmState.alarmMinute
    val alarmString = if (!alarmState.isAlarmEnabled) "꺼짐"
    else {
        val smartStart = (alarmMinutes - alarmState.selectedSmartAlarmRange).toLocalDateTime()
        val smartEnd = alarmMinutes.toLocalDateTime()
        if (alarmState.isSmartAlarmEnabled) "${smartStart.to24TimeString()}~${smartEnd.to24TimeString()}" else smartEnd.to24TimeString()
    }
    StatusColumn(
        modifier = modifier,
        icon = painterResource(Res.drawable.ic_alarm),
        iconTint = if (alarmState.isAlarmEnabled) primary else Color.White.copy(0.4f),
        label = stringResource(Res.string.wake_label),
        value = alarmString,
    )
}
@Composable
private fun StatusColumn(
    icon: Painter,
    iconTint: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    captionContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.caption,
                color = Color.White
            )
            // 진동/스마트알람 뱃지는 기상 아이콘 오른쪽에 바로 붙여 둔다.
            if (captionContent != null) captionContent()
        }
        Text(
            text = value,
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
@Composable
private fun AlarmSoundRow(
    alarmState: AlarmContract.State,
    selectedAlarm: Alarm.Sound,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BouncingPreviewIcon(
            isPlaying = alarmState.isAlarmPreviewPlaying,
            tint = primary
        )
        Text(
            modifier = Modifier.basicMarquee(),
            text = if (alarmState.isAlarmEnabled) stringResource(
                ResourceMapper.getAlarmTitleRes(selectedAlarm.id)
            ) else stringResource(Res.string.alarm_disabled_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1
        )
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
fun CommonTimeSection(
    pickerKey: String,
    selectedHour: Int,
    selectedMinute: Int,
    isEnabled: Boolean,
    shadowColor: Color,
    onChangeHour: (Int) -> Unit,
    onChangeMinute: (Int, Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CommonTimePicker(
            pickerKey = pickerKey,
            isEnabled = isEnabled,
            items = (0..23).toList(),
            selectedValue = selectedHour,
            shadowColor = shadowColor,
            onValueChanged = { value, _ -> onChangeHour(value) }
        )
        Box(contentAlignment = Alignment.Center) {
            CircleCanvas(isEnabled = isEnabled)
        }
        CommonTimePicker(
            pickerKey = pickerKey,
            isEnabled = isEnabled,
            items = (0..59).toList(),
            selectedValue = selectedMinute,
            shadowColor = shadowColor,
            onValueChanged = { value, index -> onChangeMinute(value, index) }
        )
    }
}

@Composable
fun CommonTimePicker(
    pickerKey: String,
    isEnabled: Boolean,
    items: List<Int>,
    selectedValue: Int,
    shadowColor: Color,
    onValueChanged: (value: Int, globalIndex: Int) -> Unit,
) {
    val itemHeight = 40.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val visibleCount = 3
    val centerIndexOffset = visibleCount / 2

    val listState = remember(pickerKey) {
        LazyListState()
    }
    val flingBehavior = rememberLimitedSnapFlingBehavior(listState)
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
        listState.scrollToItem((startIndex - centerIndexOffset))
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
                        color = if (isEnabled) Color.White else Color.White.copy(0.4f),
                    )
                }
            }
        }
    }
}

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

/** 볼륨 조절 단계. 10단계면 대충 끌어도 원하는 값 근처에 정확히 붙는다. */
/**
 * 볼륨 크기에 맞는 스피커 아이콘.
 *
 * 중간 볼륨 전용 에셋이 없어(ic_volume_off / low / high 세 종뿐) 중간 구간은 low 를 쓴다.
 * 구간은 0~20 / 21~79 / 80~100 으로 전 범위를 덮는다 — 경계 사이에 빈 구간이 생기면
 * 그 값에서 아이콘이 무엇이 될지 정의되지 않는다.
 */
@Composable
private fun volumeIconFor(volume: Float) = when ((volume * 100).roundToInt()) {
    in 0..20 -> painterResource(Res.drawable.ic_volume_mute)
    in 21..79 -> painterResource(Res.drawable.ic_volume_low)
    else -> painterResource(Res.drawable.ic_volume_high)
}

private const val VOLUME_STEP = 0.1f

private val VOLUME_VALUE_TEXT_WIDTH = 64.dp

private fun snapVolume(raw: Float): Float =
    (round(raw / VOLUME_STEP) * VOLUME_STEP).coerceIn(0f, 1f)

@Composable
fun VerticalVolumeDial(
    volume: Float,
    onChangeVolume: (Float) -> Unit,
) {
    val trackHeight = 16.dp
    val handleSize = 32.dp
    val handleSizePx = with(LocalDensity.current) { handleSize.toPx() }

    val trackColor = surface
    val dotColor = Color.White.copy(alpha = 0.6f)
    val progressBrush = Brush.horizontalGradient(
        colors = listOf(primary, secondary)
    )

    // 300dp 고정 폭은 좁은 화면에서 잘렸다. 실제로 측정된 폭으로 이동 거리를 계산한다.
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val travelRangePx = (trackWidthPx - handleSizePx).coerceAtLeast(1f)

    var selectedVolume by remember { mutableFloatStateOf(snapVolume(volume)) }
    LaunchedEffect(volume) { selectedVolume = snapVolume(volume) }

    val displayVolume by animateFloatAsState(targetValue = selectedVolume, label = "volumeHandle")
    val handleFraction = displayVolume.coerceIn(0f, 1f)

    fun applyFromX(x: Float) {
        val snapped = snapVolume((x - handleSizePx / 2f) / travelRangePx)
        if (snapped != selectedVolume) {
            selectedVolume = snapped
            onChangeVolume(snapped)
        }
    }

    val accentColor = when (selectedVolume) {
        in 0f..0.2f, in 0.8f..1.0f -> MaterialTheme.colorScheme.error
        else -> primary
    }

    var isDragging by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 볼륨 크기 텍스트는 슬라이더 핸들을 따라다니지 않고, 슬라이더 왼쪽에 고정해 둔다.
        Text(
            text = "${(selectedVolume * 100).roundToInt()}",
            style = MaterialTheme.typography.bodyHighlight,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            modifier = Modifier.width(VOLUME_VALUE_TEXT_WIDTH),
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(handleSize)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(travelRangePx) {
                    detectTapGestures { offset -> applyFromX(offset.x) }
                }
                .pointerInput(travelRangePx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            applyFromX(offset.x)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            applyFromX(change.position.x)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                },
            contentAlignment = Alignment.BottomStart
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(handleSize),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(RoundedCornerShape(50.dp))
                        .background(trackColor)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = handleSize / 2)) {
                        // 눈금 개수를 스냅 단계 수와 맞춰, 실제 조절 단위를 눈으로 알 수 있게 한다.
                        val dotCount = (1f / VOLUME_STEP).roundToInt()
                        val spacing = size.width / dotCount
                        for (i in 0..dotCount) {
                            drawCircle(
                                color = dotColor,
                                radius = 1.5.dp.toPx(),
                                center = Offset(i * spacing, size.height / 2)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(handleFraction)
                        .height(trackHeight)
                        .background(progressBrush, RoundedCornerShape(50.dp))
                )
            }

            Surface(
                modifier = Modifier
                    .width(handleSize)
                    .height(handleSize)
                    .offset { IntOffset((travelRangePx * handleFraction).roundToInt(), 0) },
                // 캡슐 모양이라 위로 늘어나도 자연스럽게 이어진다.
                shape = RoundedCornerShape(handleSize / 2),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Icon(
                        modifier = Modifier.size(handleSize).padding(4.dp),
                        painter = volumeIconFor(selectedVolume),
                        contentDescription = null,
                        tint = accentColor
                    )
                }
            }
        }
    }
}
