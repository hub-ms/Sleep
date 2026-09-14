package com.sleepytime.shared.ui.tracking

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.navigator.internal.BackHandler
import com.sleepytime.shared.domain.model.SleepMusic
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.*
import com.sleepytime.shared.ui.alarm.AlarmContract
import com.sleepytime.shared.ui.alarm.AlarmTimeSection
import com.sleepytime.shared.ui.home.MusicBrowserSection
import com.sleepytime.shared.ui.music.MusicContract
import com.sleepytime.shared.ui.theme.SleepTheme
import com.sleepytime.shared.ui.theme.bodyText
import com.sleepytime.shared.ui.theme.caption
import com.sleepytime.shared.util.DateTimeUtil.formatSleepMusicSeconds
import com.sleepytime.shared.util.DateTimeUtil.toAmPmTimeString
import com.sleepytime.shared.util.ResourceMapper
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
@ExperimentalTime
@InternalVoyagerApi
fun TrackingContent(
    trackingState: TrackingContract.State,
    musicState: MusicContract.State,
    elapsedSleepMusicSeconds: Int,
    elapsedSleepTimeMillis: Long,
    onFinishTracking: () -> Unit,
    onDiscardTracking: () -> Unit,
    onTogglePlaying: () -> Unit,
    onMusicSelected: (SleepMusic?) -> Unit,
    onSetTimer: (Int?) -> Unit,
    onToggleFavorite: (SleepMusic) -> Unit,
    onChangeAlarmHour: (Int) -> Unit,
    onChangeAlarmMinute: (Int, Int) -> Unit
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

    var showMusicList by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        isTrackingFinishDialogShow.value = true
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    val x = change.position.x
                    val width = size.width
                    // 왼쪽 끝(10%)에서 오른쪽으로 스와이프하거나 오른쪽 끝(90%)에서 왼쪽으로 스와이프할 때
                    if ((x < width * 0.1f && dragAmount > 20) || (x > width * 0.9f && dragAmount < -20)) {
                        showMusicList = true
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "수면 측정 중",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = formatMillis(elapsedSleepTimeMillis),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            CurrentMusicCard(
                musicState = musicState,
                elapsedSleepMusicSeconds = elapsedSleepMusicSeconds,
                onTogglePlaying = onTogglePlaying,
                onSetTimer = onSetTimer,
            )
            Spacer(Modifier.height(24.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(Res.drawable.ic_alarm_clock),
                    tint = Color.White.copy(0.4f),
                    contentDescription = "알람 시간 변경 아이콘"
                )
                Text(
                    text = "기상 알람 ${trackingState.trackingEndTime.toAmPmTimeString()}",
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
                        painter = painterResource(Res.drawable.ic_pencil),
                        tint = Color.White.copy(0.4f),
                        contentDescription = "알람 시간 변경"
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
                isAlarmTimePickerShow = isAlarmTimePickerShow,
                onChangeAlarmHour = onChangeAlarmHour,
                onChangeAlarmMinute = onChangeAlarmMinute
            )
            Column(
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
                val primaryColor = MaterialTheme.colorScheme.primary
                val dragProgress = if (offsetYAnim.value < 0f) (-offsetYAnim.value / 400f).coerceIn(0f, 1f) else 0f
                IconButton(
                    modifier = Modifier.size(36.dp),
                    onClick = {}
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(Res.drawable.ic_caret_up),
                        tint = Color.White.copy(0.4f),
                        contentDescription = "수면 종료"
                    )
                }
                Text(
                    text = if(dragProgress==0f) "위로 밀어서 수면 종료" else "화면을 위로 끝까지 밀어주세요",
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
    
    // Music Section Overlay
    AnimatedVisibility(
        visible = showMusicList,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable { showMusicList = false }
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .clickable(enabled = false) {}, // 클릭 전파 방지
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color(0xFF1A1A1A)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(Color.Gray.copy(0.3f), RoundedCornerShape(2.dp))
                            .align(Alignment.CenterHorizontally)
                    )
                    Text(
                        "수면 음악 선택",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    MusicBrowserSection(
                        musicState = musicState,
                        onMusicSelected = { music ->
                            onMusicSelected(music)
                            showMusicList = false // 음악 선택 시 닫기
                        },
                        onToggleFavorite = onToggleFavorite
                    )
                }
            }
        }
    }

    if (isTrackingFinishDialogShow.value) {
        AlertDialog(
            modifier = Modifier
                .background(
                    brush = SleepTheme.gradients.surface,
                    shape = RoundedCornerShape(16.dp)
                ),
            onDismissRequest = {
                isTrackingFinishDialogShow.value = false
            },
            containerColor = Color.Transparent,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "수면 측정 종료",
                    style = MaterialTheme.typography.bodyText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (elapsedMinutes < 30) "수면을 측정한 지 30분이 지나지 않아\n리포트가 만들어지지 않았어요.\n수면 측정을 종료할까요?" else "수면 측정을 종료할까요?",
                    style = MaterialTheme.typography.bodyText,
                    color = Color.White
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isTrackingFinishDialogShow.value = false
                        val elapsedMinutes = elapsedSleepTimeMillis / (1000 * 60)
                        if (elapsedMinutes < 5) onDiscardTracking() else onFinishTracking()
                    }
                ) {
                    Text(
                        text = "측정 종료",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isTrackingFinishDialogShow.value = false
                    }
                ) {
                    Text(
                        text = "계속 측정",
                        style = MaterialTheme.typography.caption,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }
}

@Composable
fun CurrentMusicCard(
    musicState: MusicContract.State,
    elapsedSleepMusicSeconds: Int,
    onTogglePlaying: () -> Unit,
    onSetTimer: (Int?) -> Unit,

) {
    val image = remember(musicState.selectedMusic?.imageName) { ResourceMapper.getDrawableRes(musicState.selectedMusic?.imageName) }
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
        if(musicState.selectedMusic==null) {
            Text(
                text = "수면 음악과 함께 잠들어보세요",
                style = MaterialTheme.typography.bodyText,
                color = Color.White
            )
        } else {
            Column(
                modifier = Modifier
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(image),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = onTogglePlaying,
                            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), CircleShape)
                        ) {
                            Icon(
                                painter = if (musicState.isPlaying) painterResource(Res.drawable.ic_pause) else painterResource(Res.drawable.ic_play),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = musicState.selectedMusic.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = formatSleepMusicSeconds(elapsedSleepMusicSeconds),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (musicState.isPlaying) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            ControlMenu(
                                icon = if(musicState.timerMinutes != null) Res.drawable.ic_timer_on else Res.drawable.ic_timer_off,
                                label = if (musicState.timerMinutes != null) "${musicState.timerMinutes}분" else "타이머",
                                onClick = { onSetTimer(if (musicState.timerMinutes == null) 30 else null) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ControlMenu(icon: DrawableResource, label: String, selected: Boolean = false, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(8.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}
@Composable
fun TimeSection(
    alarmState: AlarmContract.State,
    elapsedSleepTimeMillis: Long,
    sleepDurationMillis: Long,
    isAlarmTimePickerShow: MutableState<Boolean>,
    onChangeAlarmHour: (Int) -> Unit,
    onChangeAlarmMinute: (Int, Int) -> Unit,
) {
    val gradientBrush = Brush.sweepGradient(
        0.0f to MaterialTheme.colorScheme.primary,
        0.5f to MaterialTheme.colorScheme.secondary,
        1.0f to MaterialTheme.colorScheme.primary
    )
    Box(
        modifier = Modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        val targetSweepAngle =
            (elapsedSleepTimeMillis.toFloat() / sleepDurationMillis.toFloat()) * 360f

        val safeSweepAngle = if (targetSweepAngle.isNaN() || targetSweepAngle.isInfinite()) 0f
        else targetSweepAngle.coerceIn(0f, 360f)

        val sweepAngle by animateFloatAsState(
            targetValue = safeSweepAngle,
            animationSpec = tween(1000, easing = LinearEasing),
            label = "ArcSweepAnimation"
        )
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
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
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),

                )
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier,
                contentAlignment = Alignment.Center
            ) {
                if (isAlarmTimePickerShow.value) {
                    AlarmTimeSection(
                        alarmState = alarmState,
                        onChangeAlarmHour = onChangeAlarmHour,
                        onChangeAlarmMinute = onChangeAlarmMinute,
                    )
                }
            }
        }
    }
}

private fun formatMillis(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return if (hours > 0) {
        "${hours}시간 ${minutes}분"
    } else {
        "${minutes}분 ${seconds}초"
    }
}
