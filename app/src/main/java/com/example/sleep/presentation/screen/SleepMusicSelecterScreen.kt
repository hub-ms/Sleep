package com.example.sleep.presentation.screen

import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.sleep.R
import com.example.sleep.SleepMusicItem
import com.example.sleep.presentation.viewmodel.SleepMusicViewModel
import kotlinx.coroutines.delay

data class SleepMusic(
    var title: String,
    var description: String,
    var imageRes: Int,
    var musicRes: Int,
    var duration: Long,
    var isLoop: Boolean,
    var isTimer: Boolean
)

@Composable
fun SleepMusicSelecterScreen(
    navController: NavHostController,
    viewModel: SleepMusicViewModel
) {
    val sleepMusicList by viewModel.sleepMusicList.collectAsState()
    val selectedIndex by viewModel.selectedIndex.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val isLoop by viewModel.isLoop.collectAsState()
    val isTimer by viewModel.isTimer.collectAsState()
    val selectedImageRes =
        if (selectedIndex != -1) sleepMusicList[selectedIndex].imageRes else sleepMusicList[0].imageRes
    var isPlaying by remember { mutableStateOf(false) }
    val icon = if (isPlaying) R.drawable.pause else R.drawable.play
    var musicPlayer by remember {
        mutableStateOf<MediaPlayer?>(null)
    }
    var position by remember { mutableLongStateOf(0L) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A021D)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .height(250.dp)
                .fillMaxWidth()
                .border(2.dp, Color.Blue)
                .clickable {
                    viewModel.clearSelection()
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = selectedImageRes),
                contentDescription = "수면 이미지",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center),
            ) {
                Text(
                    text = if (selectedIndex == -1) "수면음악을 선택해 주세요"
                    else {
                        if (isPlaying) "미리듣기 재생 중" else "수면음악 미리듣기"
                    },
                    fontSize = 20.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(16.dp),
                )
                if (selectedIndex != -1) {
                    Icon(
                        painter = painterResource(id = icon),
                        contentDescription = if (isPlaying) "일시정지" else "재생",
                        tint = Color.White,
                        modifier = Modifier
                            .border(2.dp, Color.Blue)
                            .padding(16.dp)
                            .size(40.dp)
                            .align(Alignment.CenterHorizontally)
                            .clickable {
                                if (isPlaying) {
                                    musicPlayer?.pause()
                                    position = musicPlayer?.currentPosition?.toLong() ?: 0L
                                } else {
                                    musicPlayer?.start()
                                    musicPlayer?.seekTo(position.toInt())
                                }
                                isPlaying = !isPlaying
                            }
                    )
                }
                if (isPlaying) {
                    LaunchedEffect(Unit) {
                        delay(30000)
                        musicPlayer?.pause()
                        position = 0L
                        isPlaying = false
                    }
                }
            }
        }
        SelectableMusicList(
            sleepMusicList = sleepMusicList,
            selectedIndex = selectedIndex,
            currentIndex = currentIndex,
            onItemClick = { viewModel.selectMusic(it) },
            viewModel = viewModel,
            isLoop = isLoop,
            isTimer = isTimer,
            navController = navController
        )
    }
}

@Composable
fun SelectableMusicList(
    sleepMusicList: List<SleepMusicItem>,
    selectedIndex: Int,
    currentIndex: Int,
    onItemClick: (Int) -> Unit,
    viewModel: SleepMusicViewModel,
    isLoop: Boolean,
    isTimer: Boolean,
    navController: NavHostController,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color.Green)
            .background(Color(0xFF10192A)),
    ) {
        itemsIndexed(sleepMusicList) { index, music ->
            val isSelected = index == selectedIndex
            val backgroundColor = if (isSelected) Color(0xFF234075) else Color(0xFF10192A)
//            val isLoop by viewModel.isLoop.collectAsState()
//            val isTimer by viewModel.isTimer.collectAsState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(backgroundColor)
                    .clickable {
                        onItemClick(index)
                        if(currentIndex !=selectedIndex) {
                            viewModel.clearSelection()
                        } else {
                            viewModel.selectMusic(index)
                        }
//                        navController.popBackStack()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = music.title,
                        fontSize = 20.sp,
                        color = Color.White,
                        textAlign = TextAlign.Left,
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentHeight()
                            .align(Alignment.CenterVertically)
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.loop),
                        contentDescription = "Loop Icon",
                        tint = if (isLoop) Color(0xFF42A5F5) else Color.White,
                        modifier = Modifier
                            .border(2.dp, Color.Blue)
                            .padding(16.dp)
                            .size(30.dp)
                            .clickable {
                                if (isSelected) viewModel.toggleLoop()
                            }
                            .align(Alignment.CenterVertically),
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.timer),
                        contentDescription = "Timer Icon",
                        tint = if (isTimer) Color(0xFF42A5F5) else Color.White,
                        modifier = Modifier
                            .border(2.dp, Color.Blue)
                            .padding(16.dp)
                            .size(30.dp)
                            .clickable {
                                if (isSelected) viewModel.toggleTimer()
                            }
                            .align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}
