package com.soundsleeper.app.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.domain.model.SleepMusic
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.ic_bookmark_filled
import com.soundsleeper.app.resources.ic_bookmark_outlined
import com.soundsleeper.app.resources.ic_pause
import com.soundsleeper.app.resources.ic_play
import com.soundsleeper.app.ui.music.MusicContract
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.SleepTheme.surface
import com.soundsleeper.app.util.DateTimeUtil.formatSleepMusicSeconds
import com.soundsleeper.app.util.ResourceMapper
import org.jetbrains.compose.resources.painterResource

@Composable
fun CurrentMusicCard(
    modifier: Modifier,
    musicState: MusicContract.State,
    elapsedSleepMusicSeconds: Int,
    onTogglePlaying: () -> Unit,
    onToggleFavorite: (SleepMusic) -> Unit = {},
    timerContent: (@Composable () -> Unit)? = null,
) {
    val selected = musicState.selectedMusic

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .background(color = surface, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Column {
            if (selected == null) EmptyMusicContent()
            else SelectedMusicContent(
                music = selected,
                isPlaying = musicState.isPlaying,
                elapsedSeconds = elapsedSleepMusicSeconds,
                onTogglePlaying = onTogglePlaying,
                onToggleFavorite = onToggleFavorite
            )
            if (selected != null) timerContent?.invoke()
        }
    }
}

@Composable
private fun EmptyMusicContent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "어떤 음악과 함께 잠들어볼까요?",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "편안한 소리와 함께 더 깊은 잠에 들어보세요",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SelectedMusicContent(
    music: SleepMusic,
    isPlaying: Boolean,
    elapsedSeconds: Int,
    onTogglePlaying: () -> Unit,
    onToggleFavorite: (SleepMusic) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {

        // 전체 배경 이미지
        Image(
            painter = painterResource(
                ResourceMapper.getMusicImageRes(music.imageName)
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth()
        )

        // 하단 영역
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {

            // 블러 배경
            Image(
                painter = painterResource(
                    ResourceMapper.getMusicImageRes(music.imageName)
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(24.dp)
            )

            // 다크 오버레이
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            // 실제 컨텐츠
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                IconButton(
                    onClick = onTogglePlaying,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            primary.copy(alpha = 0.15f),
                            CircleShape
                        )
                ) {
                    Icon(
                        painter = painterResource(
                            if (isPlaying) {
                                Res.drawable.ic_pause
                            } else {
                                Res.drawable.ic_play
                            }
                        ),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = music.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = formatSleepMusicSeconds(elapsedSeconds),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            onToggleFavorite(music)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = if (music.isFavorite) {
                            painterResource(
                                Res.drawable.ic_bookmark_filled
                            )
                        } else {
                            painterResource(
                                Res.drawable.ic_bookmark_outlined
                            )
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
