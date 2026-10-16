package com.soundsleeper.app.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.domain.model.SleepMusic
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.ic_bookmark_filled
import com.soundsleeper.app.resources.ic_bookmark_outlined
import com.soundsleeper.app.resources.ic_pause
import com.soundsleeper.app.resources.ic_play
import com.soundsleeper.app.resources.music_empty_card_description
import com.soundsleeper.app.resources.music_empty_card_title
import com.soundsleeper.app.resources.music_favorite_add
import com.soundsleeper.app.resources.music_favorite_remove
import com.soundsleeper.app.resources.music_pause
import com.soundsleeper.app.resources.music_play
import com.soundsleeper.app.ui.music.MusicContract
import com.soundsleeper.app.ui.theme.SleepTheme.surface
import com.soundsleeper.app.util.DateTimeUtil.formatSleepMusicSeconds
import com.soundsleeper.app.util.ResourceMapper
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 카드 전체 높이. **선택 여부와 무관하게 고정한다.**
 *
 * 예전에는 곡을 고르지 않았을 때는 글자 두 줄짜리 짧은 카드였다가, 고르면 아트워크(140dp)와
 * 타이머가 붙으면서 카드가 두 배 넘게 길어졌다. 홈 화면은 이 카드 아래에 "수면 시작" 버튼이
 * 있어서, 카드가 자라는 만큼 버튼이 아래로 밀렸다 — 작은 화면에서는 화면 밖으로 나가
 * 아예 누를 수 없었다. 높이를 묶어 두면 곡을 골라도 아래 버튼이 제자리에 있다.
 *
 * 값은 선택 상태의 내용이 들어갈 만큼이다: 아트워크 [ARTWORK_HEIGHT] + 제목 행(약 44dp)
 * + 구분선 + 타이머 슬롯(라벨 행 + 세그먼트 36dp + 여백, 약 90dp). 더 줄이면 타이머
 * 세그먼트의 아래쪽이 잘린다(에뮬레이터에서 232dp 로 확인).
 *
 * 측정 화면은 이 고정 높이를 쓰지 않는다 - 아래에 지켜야 할 버튼이 없고 타이머 슬롯도
 * 넘기지 않아서, 고정하면 카드 아래쪽이 그만큼 빈 채로 남는다. `fixedHeight` 참고.
 */
private val CARD_HEIGHT = 276.dp

/** 아트워크가 차지하는 높이. 나머지는 메타데이터 행 + 타이머 슬롯이 쓴다. */
private val ARTWORK_HEIGHT = 210.dp

/**
 * @param fixedHeight 곡 선택 여부와 무관하게 [CARD_HEIGHT] 로 높이를 묶는다. 이 카드 아래에
 *   위치가 고정이어야 하는 것(홈의 "수면 시작" 버튼)이 있을 때만 켠다. 끄면 내용만큼만
 *   차지한다.
 */
@Composable
fun CurrentMusicCard(
    musicState: MusicContract.State,
    elapsedSleepMusicSeconds: Int,
    onTogglePlaying: () -> Unit,
    onToggleFavorite: (SleepMusic) -> Unit = {},
    fixedHeight: Boolean = false,
    timerContent: (@Composable () -> Unit)? = null,
) {
    val selected = musicState.selectedMusic

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fixedHeight) Modifier.height(CARD_HEIGHT) else Modifier)
            .background(color = surface, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (selected == null) {
                EmptyMusicContent()
            } else {
                SelectedMusicContent(
                    music = selected,
                    timerContent = timerContent,
                    isPlaying = musicState.isPlaying,
                    elapsedSeconds = elapsedSleepMusicSeconds,
                    onTogglePlaying = onTogglePlaying,
                    onToggleFavorite = onToggleFavorite
                )
                // 종료 타이머는 제목·재생시간·북마크가 있는 가로줄 **아래**에 온다.
                // 남는 세로 공간을 가져가므로 카드 높이가 고정이어도 잘리지 않는다.

            }
        }
    }
}

@Composable
private fun EmptyMusicContent() {
    // 고정 높이일 때 빈 상태를 위로 붙여 두면 아래가 텅 빈다. 가운데로 띄운다
    // (높이를 묶지 않은 호출부에서는 이 Box 가 내용만큼만 차지하므로 영향이 없다).
    Box(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 이 두 문구만 하드코딩 한국어로 남아 있어서, 영어 로케일 홈 화면 한가운데에
            // 한국어가 그대로 노출됐다(에뮬레이터에서 확인).
            Text(
                text = stringResource(Res.string.music_empty_card_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(Res.string.music_empty_card_description),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SelectedMusicContent(
    music: SleepMusic,
    timerContent: (@Composable () -> Unit)? = null,
    isPlaying: Boolean,
    elapsedSeconds: Int,
    onTogglePlaying: () -> Unit,
    onToggleFavorite: (SleepMusic) -> Unit,
) {
    val title = stringResource(ResourceMapper.getMusicTitleRes(music.musicName))

    // ── 아트워크 + 가운데 재생 버튼 ────────────────────────────────────
    //
    // 높이를 묶지 않으면 원본 비율대로 폭만큼(≈1:1) 늘어나서 카드 하나가 화면 절반을
    // 넘게 차지한다. 지금 재생 중인 곡을 알려주는 카드이지 갤러리가 아니므로 Crop 한다.
    Box(
        modifier = Modifier.fillMaxWidth().height(ARTWORK_HEIGHT),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(ResourceMapper.getMusicImageRes(music.imageName)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // 재생/일시정지는 이 카드에서 가장 자주 누르는 것이다. 예전에는 아래 가로줄에
        // 제목·시간·북마크와 나란히 48dp 로 끼어 있어 가장 중요한데 가장 눈에 안 띄었다.
        // 썸네일 한가운데로 올리고 키운다 — 어두운 원을 깔아 밝은 아트워크 위에서도 보이게 한다.
        IconButton(
            onClick = onTogglePlaying,
            modifier = Modifier
                .size(64.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
        ) {
            Icon(
                painter = painterResource(if (isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play),
                contentDescription = stringResource(
                    if (isPlaying) Res.string.music_pause else Res.string.music_play
                ),
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }

    // ── 제목 · 재생 시간 · 북마크 한 줄 ────────────────────────────────
    Box(modifier = Modifier.fillMaxWidth()) {
        // 아트워크를 흐리게 깔아 카드 면과 이어지게 한다.
        Image(
            painter = painterResource(ResourceMapper.getMusicImageRes(music.imageName)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().blur(24.dp)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.8f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .padding(8.dp)
                .height(120.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    // HomeScreen.MusicCompactCard 와 같은 이유로 DB 의 title 을 쓰지 않는다.
                    text = title,
                    // 제목이 길면 말줄임으로 잘라 버리는 대신 가로로 흐르게 한다. 곡 제목이
                    // 문장형이라 앞 몇 글자만으로는 어떤 곡인지 구분되지 않는 경우가 많다.
                    modifier = Modifier.weight(1f).basicMarquee(),
                    maxLines = 1,
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
                        .clickable { onToggleFavorite(music) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            if (music.isFavorite) Res.drawable.ic_bookmark_filled
                            else Res.drawable.ic_bookmark_outlined
                        ),
                        contentDescription = stringResource(
                            if (music.isFavorite) Res.string.music_favorite_remove
                            else Res.string.music_favorite_add
                        ),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            timerContent?.let {
                Box(contentAlignment = Alignment.Center) { it() }
            }
        }
    }
}
