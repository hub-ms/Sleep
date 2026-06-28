package com.sleepytime.shared.ui.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.util.ResourceMapper
import com.sleepytime.shared.domain.model.SleepMusic
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_pause
import com.sleepytime.shared.resources.ic_play
import com.sleepytime.shared.ui.theme.bodyHighlight
import com.sleepytime.shared.ui.theme.bodyText
import com.sleepytime.shared.ui.theme.caption
import org.jetbrains.compose.resources.painterResource

@ExperimentalMaterial3Api
@Composable
fun SleepMusicSelectionContent(
    musicState: MusicContract.State,
    elapsedSeconds: Int,
    onTabSelected: (Int) -> Unit,
    onMusicSelected: (SleepMusic) -> Unit,
    onTogglePlay: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            SleepMusicCategoryTab(
                menus = listOf("자연", "백색소음", "악기"),
                selectedIndex = selectedTab,
                onSelected = {
                    selectedTab = it
                    onTabSelected(it)
                }
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            items(musicState.musicList.size) { index ->
                val music = musicState.musicList[index]

                MusicSelectionCard(
                    music = music,
                    isSelected = music.musicName == musicState.selectedMusic?.musicName,
                    onMusicSelected = onMusicSelected
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = onTogglePlay
            ) {
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = if (musicState.isPlaying) painterResource(Res.drawable.ic_pause) else painterResource(
                        Res.drawable.ic_play
                    ),
                    contentDescription = "재생",
                    tint = Color.White,
                )
            }
            Text(
                text = musicState.selectedMusic?.title ?: "선택된 음악 없음",
                style = MaterialTheme.typography.bodyText,
                color = Color.White
            )
            Text(
                text = if (musicState.isPlaying) formatSleepMusicSeconds(elapsedSeconds) else "00:00",
                style = MaterialTheme.typography.caption,
                color = Color.White
            )
        }
    }
}

@Composable
fun SleepMusicCategoryTab(menus: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier.padding(16.dp)
    ) {
        val tabWidth = maxWidth / menus.size
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                menus.forEachIndexed { index, menu ->
                    Text(
                        modifier = Modifier
                            .width(tabWidth)
                            .clickable { onSelected(index) },
                        text = menu,
                        style = MaterialTheme.typography.bodyHighlight,
                        color = if (index == selectedIndex) MaterialTheme.colorScheme.primary else Color.Gray,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
            ) {
                Box(
                    modifier = Modifier
                        .width(tabWidth)
                        .height(4.dp)
                        .offset(x = tabWidth * selectedIndex)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                )
            }
        }
    }
}

@Composable
fun MusicSelectionCard(
    music: SleepMusic,
    isSelected: Boolean,
    onMusicSelected: (SleepMusic) -> Unit
) {
    val image = ResourceMapper.getDrawableRes(music.imageName)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMusicSelected(music) }
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp)
            ) {
                Image(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                    ,
                    painter = painterResource(image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = music.title,
                        style = MaterialTheme.typography.bodyHighlight,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

fun formatSleepMusicSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}
