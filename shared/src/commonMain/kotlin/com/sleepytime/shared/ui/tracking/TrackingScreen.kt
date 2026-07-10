package com.sleepytime.shared.ui.tracking

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.navigator.internal.BackHandler
import com.sleepytime.shared.domain.model.SleepMusic
import com.sleepytime.shared.enum_.PredictionStageType
import com.sleepytime.shared.enum_.SleepStageType
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_alarm_clock
import com.sleepytime.shared.resources.ic_caret_right
import com.sleepytime.shared.resources.ic_check
import com.sleepytime.shared.resources.ic_pause
import com.sleepytime.shared.resources.ic_pencil
import com.sleepytime.shared.resources.ic_play
import com.sleepytime.shared.ui.alarm.AlarmContract
import com.sleepytime.shared.ui.component.EnvironmentDataRow
import com.sleepytime.shared.ui.component.EnvironmentDisplayMode
import com.sleepytime.shared.ui.music.MusicContract
import com.sleepytime.shared.ui.theme.EnvironmentColors
import com.sleepytime.shared.ui.report.sleepStageColors
import com.sleepytime.shared.ui.report.stageName
import com.sleepytime.shared.ui.alarm.AlarmTimeSection
import com.sleepytime.shared.ui.music.formatSleepMusicSeconds
import com.sleepytime.shared.ui.theme.SleepAppTheme
import com.sleepytime.shared.ui.theme.bodyHighlight
import com.sleepytime.shared.ui.theme.bodyText
import com.sleepytime.shared.ui.theme.caption
import com.sleepytime.shared.ui.theme.sectionTitle
import com.sleepytime.shared.util.DateTimeUtil.formatSleepTimeSeconds
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(InternalVoyagerApi::class)
@Composable
@ExperimentalMaterial3Api
fun TrackingContent(
    trackingState: TrackingContract.State,
    musicState: MusicContract.State,
    elapsedSleepTimeSeconds: Int,
    elapsedSleepMusicSeconds: Int,
    onFinishTracking: () -> Unit,
    onDiscardTracking: () -> Unit,
    onToggleSleepMusicClicked: () -> Unit,
    onChangeMusic: () -> Unit,
    onUpdateEndTime: (Int, Int) -> Unit,
) {
    val isAlarmTimePickerShow = remember { mutableStateOf(false) }
    val isShortTrackingWarningDialogShow = remember { mutableStateOf(false) }
    val isBackPressDialogShow = remember { mutableStateOf(false) }

    val alarmHour = trackingState.trackingEndTime.hour
    val alarmMinute = trackingState.trackingEndTime.minute

    BackHandler(enabled = true) {
        isBackPressDialogShow.value = true
    }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val uiStage = when (trackingState.currentSleepStageType) {
                PredictionStageType.AWAKE -> SleepStageType.AWAKE
                PredictionStageType.N1, PredictionStageType.N2 -> SleepStageType.LIGHT
                PredictionStageType.N3 -> SleepStageType.DEEP
                PredictionStageType.REM -> SleepStageType.REM
            }
            Box(
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.align(Alignment.TopCenter),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TopStatusSection(
                        elapsedSleepTimeSeconds = elapsedSleepTimeSeconds,
                        currentSleepStage = uiStage,
                        onAlarmClick = {
                            isAlarmTimePickerShow.value = !isAlarmTimePickerShow.value
                        },
                        trackingEndTime = trackingState.trackingEndTime
                    )
                    MusicSection(
                        musicState = musicState,
                        elapsedSleepMusicSeconds = elapsedSleepMusicSeconds,
                        onChangeMusicClicked = onChangeMusic,
                        onToggleSleepMusicClicked = onToggleSleepMusicClicked
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EnvironmentStatusCard(
                        trackingState = trackingState,
                    )
                    SleepEndButton(
                        onFinishTrackingClicked = {
                            val elapsedMinutes = elapsedSleepTimeSeconds / 60
                            if (elapsedMinutes < 5) isShortTrackingWarningDialogShow.value = true
                            else onFinishTracking()
                        }
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.BottomCenter),
            contentAlignment = Alignment.Center
        ) {
            if (isAlarmTimePickerShow.value) {
                AlarmTimeSection(
                    alarmState = AlarmContract.State(
                        alarmHour = alarmHour,
                        alarmMinute = alarmMinute,
                        isAlarmEnabled = true
                    ),
                    onChangeAlarmHour = { hour -> onUpdateEndTime(hour, alarmMinute) },
                    onChangeAlarmMinute = { minute, _ -> onUpdateEndTime(alarmHour, minute) }
                )
            } else {
                EnvironmentDataRow(
                    mode = EnvironmentDisplayMode.Live(
                        history = trackingState.environmentHistory
                    )
                )
            }
        }
        if (isShortTrackingWarningDialogShow.value) {
            AlertDialog(
                onDismissRequest = {
                    isShortTrackingWarningDialogShow.value = false
                },
                containerColor = MaterialTheme.colorScheme.background,
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
                        text = "5분 미만으로 측정된 기록은\n저장되지 않고 삭제됩니다.\n정말 종료하시겠습니까?",
                        style = MaterialTheme.typography.caption,
                        color = Color.White
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isShortTrackingWarningDialogShow.value = false
                            onDiscardTracking()
                        }
                    ) {
                        Text(
                            text = "기록 폐기 및 종료",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            isShortTrackingWarningDialogShow.value = false
                        }
                    ) {
                        Text(
                            text = "계속 측정",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
        if (isBackPressDialogShow.value) {
            AlertDialog(
                onDismissRequest = { isBackPressDialogShow.value = false },
                containerColor = MaterialTheme.colorScheme.background,
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
                        text = "수면 측정을 종료하고 나가시겠습니까?",
                        style = MaterialTheme.typography.caption,
                        color = Color.White
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isBackPressDialogShow.value = false
                            // 여기서 시간을 체크하여 분기하거나 바로 종료 처리를 합니다.
                            val elapsedMinutes = elapsedSleepTimeSeconds / 60
                            if (elapsedMinutes < 5) {
                                isShortTrackingWarningDialogShow.value = true
                            } else {
                                onFinishTracking()
                            }
                        }
                    ) {
                        Text(
                            text = "종료하기",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isBackPressDialogShow.value = false }) {
                        Text(
                            text = "취소",
                            style = MaterialTheme.typography.caption,
                            color = Color.White.copy(0.6f)
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun TopStatusSection(
    elapsedSleepTimeSeconds: Int,
    currentSleepStage: SleepStageType,
    onAlarmClick: () -> Unit,
    trackingEndTime: LocalDateTime
) {
    val infiniteTransition = rememberInfiniteTransition("rec_pulse")
    val recAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_alpha"
    )
    Napier.d("elapsedSleepTimeSeconds: $elapsedSleepTimeSeconds")


    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(recAlpha)
                .background(Color(0xFFFF4444), CircleShape)
        )
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatSleepTimeSeconds(elapsedSleepTimeSeconds),
                style = MaterialTheme.typography.sectionTitle,
                color = Color.White
            )
            Row(
                modifier = Modifier
                    .border(2.dp, Color.Green),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    painter = painterResource(Res.drawable.ic_alarm_clock),
                    tint = Color.White.copy(0.4f),
                    contentDescription = "알람 시간 변경 아이콘"
                )
                Text(
                    text = "${
                        trackingEndTime.hour.toString().padStart(2, '0')
                    }:${trackingEndTime.minute.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.caption,
                    color = Color.White.copy(0.4f),
                )
                IconButton(
                    modifier = Modifier.size(24.dp),
                    onClick = {
                        onAlarmClick()
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
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = sleepStageColors[currentSleepStage]!!.copy(0.4f),
            border = BorderStroke(1.dp, sleepStageColors[currentSleepStage]!!)
        ) {
            Text(
                modifier = Modifier.padding(4.dp),
                text = currentSleepStage.stageName,
                style = MaterialTheme.typography.caption,
                color = Color.White,
            )
        }
    }
}

@Composable
fun MusicSection(
    musicState: MusicContract.State,
    elapsedSleepMusicSeconds: Int,
    onChangeMusicClicked: () -> Unit,
    onToggleSleepMusicClicked: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (musicState.selectedMusic != null) 120.dp else 60.dp)
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (musicState.selectedMusic != null) musicState.selectedMusic.title else "수면 음악 없음",
                style = MaterialTheme.typography.bodyHighlight,
                color = Color.White
            )
            if (musicState.selectedMusic != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(0.2f), CircleShape)
                        .clickable { onToggleSleepMusicClicked() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = if (musicState.isPlaying) painterResource(Res.drawable.ic_pause)
                        else painterResource(Res.drawable.ic_play),
                        contentDescription = if (musicState.isPlaying) "일시정지" else "재생",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (musicState.selectedMusic != null) {
                Text(
                    text = formatSleepMusicSeconds(elapsedSleepMusicSeconds),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        IconButton(
            modifier = Modifier
                .size(36.dp)
                .align(Alignment.CenterEnd),
            onClick = onChangeMusicClicked
        ) {
            Icon(
                modifier = Modifier.size(36.dp),
                painter = painterResource(Res.drawable.ic_caret_right),
                contentDescription = "수면음악 선택 화면으로 이동",
                tint = Color.White,
            )
        }
    }
}

@Composable
fun EnvironmentStatusCard(
    trackingState: TrackingContract.State,
) {
    val latest = trackingState.environmentHistory.lastOrNull()
    val hasSensorError = latest?.let {
        it.heartRate == 0f || it.noise == 0f
    } ?: true

    val allGood = !trackingState.isHeartRateAnomaly &&
            !trackingState.isNoiseDanger &&
            !trackingState.isTempExtreme &&
            !trackingState.isHumidityExtreme

    val cardBorderColor = when {
        hasSensorError -> Color.White.copy(0.2f)
        allGood -> EnvironmentColors.NORMAL
        else -> EnvironmentColors.ANOMALY
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (!hasSensorError) cardBorderColor.copy(0.2f) else cardBorderColor,
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                hasSensorError -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "일부 환경 데이터를 불러오는 중입니다",
                            style = MaterialTheme.typography.caption,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                allGood -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_check),
                            contentDescription = "정상 상태 표시",
                            modifier = Modifier.size(16.dp),
                            tint = EnvironmentColors.NORMAL
                        )
                        Text(
                            text = "수면 환경이 전반적으로 양호해요",
                            style = MaterialTheme.typography.caption,
                            color = EnvironmentColors.NORMAL
                        )
                    }
                }

                else -> {
                    buildList {
                        if (trackingState.isHeartRateAnomaly) add("심박수가 비정상 범위입니다")
                        if (trackingState.isNoiseDanger) add("소음이 높아 수면에 방해될 수 있어요")
                        if (trackingState.isTempExtreme) add("수면에 적합하지 않은 온도예요")
                        if (trackingState.isHumidityExtreme) add("습도가 쾌적 범위를 벗어났어요")
                    }.forEach { message ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(6.dp)
                                    .background(EnvironmentColors.ANOMALY, CircleShape)
                            )
                            Text(
                                text = message,
                                style = MaterialTheme.typography.caption,
                                color = EnvironmentColors.ANOMALY
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SleepEndButton(onFinishTrackingClicked: () -> Unit) {
    Button(
        onClick = onFinishTrackingClicked,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        )
    ) {
        Text(
            text = "수면 종료",
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Preview
@Composable
@ExperimentalMaterial3Api
fun TrackingScreenPreview() {
    SleepAppTheme {
        TrackingContent(
            trackingState = TrackingContract.State(
                currentSleepStageType = PredictionStageType.N2,
                avgHeartRate = 65f,
                avgNoise = 30f,
                avgTemperature = 22f,
                avgHumidity = 45f
            ),
            musicState = MusicContract.State(
                selectedMusic = PreviewData.sampleMusic,
                isPlaying = true
            ),
            elapsedSleepTimeSeconds = 3660,
            elapsedSleepMusicSeconds = 120,
            onFinishTracking = {},
            onDiscardTracking = {},
            onToggleSleepMusicClicked = {},
            onChangeMusic = {},
            onUpdateEndTime = { _, _ -> },
        )
    }
}

object PreviewData {
    val sampleMusic = SleepMusic(
        musicName = "bird",
        title = "새벽을 깨우는 새소리",
        category = "DEFAULT",
        imageName = "",
        duration = 300L,
        volume = 0.5f,
        isFavorite = false,
        isLooping = true,
        isPremium = false
    )
}
