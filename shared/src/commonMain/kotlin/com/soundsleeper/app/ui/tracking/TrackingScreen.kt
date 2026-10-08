@file:OptIn(ExperimentalMaterial3Api::class)

package com.soundsleeper.app.ui.tracking

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.draw.alpha
import com.soundsleeper.app.enum_.PredictionStageType
import com.soundsleeper.app.ui.alarm.PickerStateIcon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.navigator.internal.BackHandler
import com.soundsleeper.app.domain.model.SleepMusic
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.*
import com.soundsleeper.app.ui.alarm.AlarmContract
import com.soundsleeper.app.ui.alarm.CommonTimeSection
import com.soundsleeper.app.ui.component.ChargingPhoneIllustration
import com.soundsleeper.app.ui.component.CurrentMusicCard
import com.soundsleeper.app.ui.component.SleepAlertDialog
import com.soundsleeper.app.ui.home.MusicBrowserSection
import com.soundsleeper.app.ui.music.MusicContract
import com.soundsleeper.app.ui.theme.SleepTheme
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.SleepTheme.secondary
import com.soundsleeper.app.ui.theme.bodyText
import com.soundsleeper.app.ui.theme.caption
import com.soundsleeper.app.util.DateTimeUtil.formatElapsedTimeFromMillis
import com.soundsleeper.app.util.DateTimeUtil.toAmPmTimeString
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 측정 중 화면의 실제 UI. 경과 시간/진행 링/현재 수면 단계를 실시간으로 보여주고,
 * 위로 스와이프하거나 뒤로가기를 누르면 종료 확인 다이얼로그를 띄운다.
 * 다이얼로그 확인 시 경과 시간에 따라 [onDiscardTracking](5분 미만) 또는
 * [onFinishTracking](그 외)을 호출해 실제 종료 처리를 위로 전달한다.
 */
@Composable
@ExperimentalTime
@InternalVoyagerApi
fun TrackingContent(
    trackingState: TrackingContract.State,
    musicState: MusicContract.State,
    pagerState: PagerState,
    onChangePage: (Int) -> Unit,
    elapsedSleepMusicSeconds: Int,
    elapsedSleepTimeMillis: Long,
    onFinishTracking: () -> Unit,
    onDiscardTracking: () -> Unit,
    onTogglePlaying: () -> Unit,
    onMusicSelected: (SleepMusic?) -> Unit,
    onToggleFavorite: (SleepMusic) -> Unit,
    // 예전에는 측정 화면이 이 두 값을 받지 않아서, 음악 목록이 사용자의 구독 상태를 모르고
    // 유료곡을 전부 잠긴 것으로 그렸다.
    isUserPremium: Boolean = false,
    onLockedTrackClicked: () -> Unit = {},
    onChangeAlarmHour: (Int) -> Unit,
    onChangeAlarmMinute: (Int, Int) -> Unit,
) {
    val isAlarmTimePickerShow = remember { mutableStateOf(false) }
    val isTrackingFinishDialogShow = remember { mutableStateOf(false) }

    val currentEndTime = trackingState.trackingEndTime
    val endInstant = currentEndTime.toInstant(TimeZone.currentSystemDefault())

    val sleepDurationMillis =
        (endInstant - trackingState.trackingStartTime.toInstant(TimeZone.currentSystemDefault())).inWholeMilliseconds
    val elapsedMinutes = elapsedSleepTimeMillis / (1000 * 60)

    val coroutineScope = rememberCoroutineScope()
    val offsetYAnim = remember { Animatable(0f) }
    val hasTriggeredFinish = remember { mutableStateOf(false) }
    val dragStartTime = remember { mutableLongStateOf(0L) }

    // 0 = 측정 화면, 1 = 음악 선택. 예전에는 showMusicList 로 바텀시트를 띄우게 되어
    // 있었지만 그 값을 true 로 바꾸는 코드가 어디에도 없어 영원히 닫힌 채였다. 잠들기 직전에
    // 곡을 바꾸려면 시트를 여는 버튼을 찾는 것보다 화면을 미는 쪽이 눈을 덜 쓴다.


    // 도움말 시트는 음악 시트와 상태를 공유하면 애니메이션이 꼬이므로 따로 둔다.
    var showHelp by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState()

    BackHandler(enabled = true) {
        isTrackingFinishDialogShow.value = true
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepTheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            if (page == 1) {
                TrackingMusicPage(
                    musicState = musicState,
                    onChangePage = onChangePage,
                    isUserPremium = isUserPremium,
                    onMusicSelected = onMusicSelected,
                    onToggleFavorite = onToggleFavorite,
                    onLockedTrackClicked = onLockedTrackClicked,
                )
                return@HorizontalPager
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(Res.string.tracking_in_progress_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = primary
                        )
                        Text(
                            text = formatElapsedTimeFromMillis(elapsedSleepTimeMillis),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(8.dp),
                        onClick = { showHelp = true }
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(Res.drawable.ic_help),
                            tint = Color.White.copy(0.6f),
                            contentDescription = stringResource(Res.string.tracking_help_description)
                        )
                    }
                }
                CurrentMusicCard(
                    modifier = Modifier.weight(1f),
                    musicState = musicState,
                    elapsedSleepMusicSeconds = elapsedSleepMusicSeconds,
                    onTogglePlaying = onTogglePlaying,
                    onToggleFavorite = onToggleFavorite,
                )
                Text(
                    text = "화면을 왼쪽으로 밀어 마음에 드는 수면음악을 골라보세요.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(Res.drawable.ic_alarm),
                        tint = Color.White.copy(0.4f),
                        contentDescription = stringResource(Res.string.tracking_alarm_icon_description)
                    )
                    Text(
                        text = stringResource(Res.string.tracking_wake_alarm_label, trackingState.trackingEndTime.toAmPmTimeString()),
                        style = MaterialTheme.typography.bodyText,
                        color = Color.White.copy(0.4f),
                    )
                    IconButton(
                        modifier = Modifier.size(24.dp),
                        onClick = {
                            isAlarmTimePickerShow.value = !isAlarmTimePickerShow.value
                        }
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            painter = painterResource(Res.drawable.ic_edit),
                            tint = Color.White.copy(0.4f),
                            contentDescription = stringResource(Res.string.tracking_change_alarm_time)
                        )
                    }
                }
                TimeSection(
                    alarmState = AlarmContract.State(
                        alarmHour = trackingState.trackingEndTime.hour,
                        alarmMinute = trackingState.trackingEndTime.minute,
                        isAlarmEnabled = true
                    ),
                    elapsedSleepTimeMillis = elapsedSleepTimeMillis,
                    sleepDurationMillis = sleepDurationMillis,
                    currentStage = trackingState.currentSleepStageType,
                    isAlarmTimePickerShow = isAlarmTimePickerShow,
                    onChangeAlarmHour = onChangeAlarmHour,
                    onChangeAlarmMinute = onChangeAlarmMinute,
                )
                Column(
                    // 아래 pointerInput: 위로 400px 이상, 250ms 이상 끌어올리면(스와이프 업)
                    // 종료 확인 다이얼로그를 띄우는 제스처. 드래그 중에는 저항감(resistanceFactor)을
                    // 줘서 끝까지 끌어야 트리거되는 느낌을 준다.
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    hasTriggeredFinish.value = false
                                    dragStartTime.longValue =
                                        Clock.System.now().toEpochMilliseconds()
                                    coroutineScope.launch { offsetYAnim.snapTo(0f) }
                                },
                                onDragEnd = {
                                    coroutineScope.launch {
                                        offsetYAnim.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring()
                                        )
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        offsetYAnim.animateTo(0f)
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    coroutineScope.launch {
                                        val current = offsetYAnim.value

                                        if (dragAmount < 0 || current < 0) {
                                            val resistanceFactor =
                                                1f / (1f + abs(current) / 150f)
                                            val newOffset =
                                                current + (dragAmount * resistanceFactor)
                                            offsetYAnim.snapTo(newOffset)

                                            val dragDuration = Clock.System.now()
                                                .toEpochMilliseconds() - dragStartTime.longValue

                                            if (!hasTriggeredFinish.value && newOffset <= -400f && dragDuration >= 250L) {
                                                hasTriggeredFinish.value = true
                                                isTrackingFinishDialogShow.value = true
                                            }
                                        } else {
                                            offsetYAnim.snapTo(current + dragAmount)
                                        }
                                    }
                                }
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val primaryColor = primary
                    val dragProgress = if (offsetYAnim.value < 0f) (-offsetYAnim.value / 400f).coerceIn(0f, 1f) else 0f
                    // 예전에는 IconButton(onClick = {}) 이라 눌러도 아무 일이 없었다. 누를 수
                    // 있어 보이는 모양이 반응하지 않으면 제스처를 못 찾은 사용자가 더 헤맨다.
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(Res.drawable.ic_caret_up),
                        tint = Color.White.copy(0.4f),
                        contentDescription = null
                    )
                    Text(
                        text = if (dragProgress == 0f) stringResource(Res.string.tracking_swipe_up_hint)
                            else stringResource(Res.string.tracking_swipe_up_all_the_way),
                        style = MaterialTheme.typography.bodyText,
                        color = Color.White
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    ) {
                        drawRoundRect(
                            color = Color.White.copy(0.4f),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, size.height)
                        )
                        drawRoundRect(
                            color = primaryColor,
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            topLeft = Offset(0f, 0f),
                            size = Size(
                                width = size.width * dragProgress,
                                height = size.height
                            )
                        )
                    }
                }
            }
        }
    }

    if (showHelp) {
        ModalBottomSheet(
            onDismissRequest = { showHelp = false },
            sheetState = helpSheetState,
            containerColor = SleepTheme.surface,
            contentColor = Color.White,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            Color.Gray.copy(alpha = 0.4f),
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.tracking_help_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                ChargingPhoneIllustration(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .aspectRatio(1f)
                )
                Text(
                    text = stringResource(Res.string.tracking_help_body),
                    style = MaterialTheme.typography.caption,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
    if (isTrackingFinishDialogShow.value) {
        // 종료 뒤에 무엇이 남는지가 갈리는 지점이다. 5분 미만은 기록을 버리고, 30분 미만은
        // 저장은 되지만 리포트가 만들어지지 않는다. 예전 문구는 30분 분기만 설명해서,
        // 5분 안에 끈 사용자는 기록이 사라진 것을 뒤늦게 알았다.
        val message = when {
            elapsedMinutes < 5 -> stringResource(Res.string.tracking_finish_dialog_message_discard)
            elapsedMinutes < 30 -> stringResource(Res.string.tracking_finish_dialog_message_no_report)
            else -> stringResource(Res.string.tracking_finish_dialog_message_normal)
        }
        SleepAlertDialog(
            title = stringResource(Res.string.tracking_finish_dialog_title),
            message = message,
            confirmText = stringResource(Res.string.tracking_finish_confirm),
            dismissText = stringResource(Res.string.tracking_finish_dismiss),
            isDestructive = elapsedMinutes < 30,
            onConfirm = {
                isTrackingFinishDialogShow.value = false
                if (elapsedMinutes < 5) onDiscardTracking() else onFinishTracking()
            },
            onDismiss = { isTrackingFinishDialogShow.value = false }
        )
    }
}

/**
 * 측정 중 화면에서 좌우로 밀면 나오는 음악 선택 페이지.
 *
 * 바텀시트가 아니라 같은 화면의 다른 장이다. 시트는 뜨고 지는 애니메이션과 스크림 때문에
 * 어두운 방에서 화면이 한 번 밝아지는데, 잠들려는 사람에게는 그게 가장 거슬린다.
 */
@Composable
private fun TrackingMusicPage(
    musicState: MusicContract.State,
    isUserPremium: Boolean,
    onMusicSelected: (SleepMusic?) -> Unit,
    onChangePage: (Int) -> Unit,
    onToggleFavorite: (SleepMusic) -> Unit,
    onLockedTrackClicked: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = stringResource(Res.string.tracking_music_page_title),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        MusicBrowserSection(
            modifier = Modifier.weight(1f),
            musicState = musicState,
            isUserPremium = isUserPremium,
            onMusicSelected = {
                onMusicSelected(it)
                onChangePage(0)
            },
            onToggleFavorite = onToggleFavorite,
            onLockedTrackClicked = onLockedTrackClicked,
        )
    }
}

@Composable
fun TimeSection(
    alarmState: AlarmContract.State,
    elapsedSleepTimeMillis: Long,
    sleepDurationMillis: Long,
    currentStage: PredictionStageType,
    isAlarmTimePickerShow: MutableState<Boolean>,
    onChangeAlarmHour: (Int) -> Unit,
    onChangeAlarmMinute: (Int, Int) -> Unit,
) {
    val gradientBrush = Brush.sweepGradient(
        0.0f to primary,
        0.5f to secondary,
        1.0f to primary
    )
    Box(
        modifier = Modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        // 경과 시간은 1초에 한 번만 갱신된다. 그 값을 그대로 그리면 트랙이 1초씩 계단처럼
        // 움직인다. 예전에는 1초짜리 tween 으로 틱 사이를 메웠는데, 틱이 조금이라도 늦게
        // 오면 애니메이션이 먼저 끝나 그 자리에 멈췄다가 다시 출발해 오히려 더 끊겨 보였다.
        //
        // 그래서 틱 사이를 보간하는 대신 매 프레임 실제 시계를 읽어 진행도를 직접 계산한다.
        // 이 값은 Canvas 의 그리기 람다 안에서만 읽으므로 컴포지션은 다시 돌지 않고
        // 그리기만 다시 일어난다.
        val lastTickWallClock = remember(elapsedSleepTimeMillis) {
            Clock.System.now().toEpochMilliseconds()
        }
        val nowMillis = remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
        LaunchedEffect(Unit) {
            while (true) {
                withFrameMillis { nowMillis.longValue = Clock.System.now().toEpochMilliseconds() }
            }
        }

        // 기상 시각을 바꾸면 분모가 순간적으로 변해 바늘이 다른 자리로 튄다. 분자(경과)는
        // 매끄럽게 흐르게 두고 분모만 따로 애니메이션해야 둘이 서로를 방해하지 않는다.
        val animatedDuration = remember { Animatable(sleepDurationMillis.toFloat()) }
        LaunchedEffect(sleepDurationMillis) {
            animatedDuration.animateTo(
                targetValue = sleepDurationMillis.toFloat(),
                animationSpec = spring(stiffness = Spring.StiffnessLow)
            )
        }

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val smoothElapsed =
                (elapsedSleepTimeMillis + (nowMillis.longValue - lastTickWallClock)).coerceAtLeast(0L)
            val duration = animatedDuration.value
            val progress = if (duration > 0f) {
                (smoothElapsed.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
            drawArc(
                color = Color.White.copy(0.4f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(6.dp.toPx()),
            )
            drawArc(
                brush = gradientBrush,
                startAngle = -90f,
                sweepAngle = progress * 360f,
                useCenter = false,
                style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        if (isAlarmTimePickerShow.value) {
            // 피커 왼쪽에 알람 아이콘을 둔다. 수면 설정 화면도 같은 자리에 같은 아이콘을
            // 쓰고 있어, 두 화면에서 이 피커가 무엇을 고르는 것인지 같은 방식으로 읽힌다.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PickerStateIcon(
                    painter = painterResource(Res.drawable.ic_alarm),
                    contentDescription = stringResource(Res.string.tracking_wake_time_setting_description),
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
        } else {
            // 피커를 닫으면 링 안이 통째로 비어 있었다. 측정이 돌고 있다는 신호가 화면에
            // 하나도 없어서, 밤중에 깬 사용자가 제대로 재고 있는지 알 수 없었다.
            // 수집은 이미 하고 있지만 한 번도 그리지 않던 수면 단계를 여기에 보여준다.
            MeasuringIndicator(currentStage = currentStage)
        }
    }
}

/** 수면 단계 코드를 사용자가 읽는 말로. 모델의 N1/N2/N3 는 그대로 보여줄 이름이 아니다. */
@Composable
private fun PredictionStageType.toDisplayLabel(): String = when (this) {
    PredictionStageType.AWAKE -> stringResource(Res.string.tracking_stage_awake)
    PredictionStageType.N1 -> stringResource(Res.string.tracking_stage_n1)
    PredictionStageType.N2 -> stringResource(Res.string.tracking_stage_n2)
    PredictionStageType.N3 -> stringResource(Res.string.tracking_stage_n3)
    PredictionStageType.REM -> stringResource(Res.string.tracking_stage_rem)
}

/**
 * 측정이 살아 있음을 보여주는 링 중앙 표시.
 *
 * 숨 쉬듯 느리게 밝아졌다 어두워지는 점 하나와 현재 수면 단계. 1초마다 바뀌는 숫자를 더 넣으면
 * 어두운 방에서 눈에 거슬리므로 움직임은 최소로 둔다.
 */
@Composable
private fun MeasuringIndicator(currentStage: PredictionStageType) {
    val transition = rememberInfiniteTransition(label = "measuring")
    val breath by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .alpha(breath)
                .background(primary, CircleShape)
        )
        Text(
            text = stringResource(Res.string.tracking_measuring_label),
            style = MaterialTheme.typography.bodyText,
            color = Color.White.copy(0.6f)
        )
        Text(
            text = currentStage.toDisplayLabel(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
