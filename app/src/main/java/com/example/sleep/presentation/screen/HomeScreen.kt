package com.example.sleep.presentation.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.sleep.R
import java.util.*
import kotlin.math.abs

@Composable
//홈 화면
fun HomeScreen(navController: NavHostController) {
    //선택된 탭
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    //탭 리스트
    val tabs = listOf(
        stringResource(R.string.tab_sleep),
        stringResource(R.string.tab_report),
        stringResource(R.string.tab_setting)
    )
    //아이콘 리스트
    val icons = listOf(
        R.drawable.sleep,
        R.drawable.report,
        R.drawable.setting
    )
    //모드(AM/PM)
    var mode by rememberSaveable { mutableIntStateOf(R.string.am) }

    //시간 인덱스
    var hourIdx by rememberSaveable { mutableIntStateOf(0) }
    //전체 시간(1~12) 리스트
    val hourList = (1..12).toList()
    //현재 선택된 시간
    val selectedHour = hourList[hourIdx]

    //분 인덱스
    var minuteIdx by rememberSaveable { mutableIntStateOf(0) }
    //전체 분(0~59) 리스트
    val minuteList = (0..59).toList()
    //현재 선택된 분
    val selectedMinute = minuteList[minuteIdx]

    Scaffold(
        bottomBar = {
            BottomTabBar(
                tabs = tabs,
                icons = icons,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .background(Color(0xFF0A021D))
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (selectedTab) {
                0 -> {
                    TimePicker(
                        hourIdx = hourIdx,
                        setHourIdx = { hourIdx = it },
                        hourList = hourList,
                        minuteIdx = minuteIdx,
                        setMinuteIdx = { minuteIdx = it },
                        minuteList = minuteList,
                        mode = mode,
                        changeMode = { mode = it }
                    )
                    SleepButton(
                        navController = navController,
                        mode = stringResource(mode),
                        selectedHour = selectedHour,
                        selectedMinute = selectedMinute
                    )
                }

                1 -> ReportScreen()
            }
        }
    }
}

@Composable
fun TimePicker(
    hourIdx: Int,
    setHourIdx: (Int) -> Unit,
    hourList: List<Int>,
    minuteIdx: Int,
    setMinuteIdx: (Int) -> Unit,
    minuteList: List<Int>,
    mode: Int,
    changeMode: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .border(2.dp, Color.Green)
            .height(200.dp)
            .fillMaxWidth(0.8f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SwitchMode(mode = mode, changeMode = changeMode)
        HourPicker(
            hourIdx = hourIdx,
            setHourIdx = setHourIdx,
            hourList = hourList
        )
        Text(
            text = ":",
            fontSize = 45.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .wrapContentHeight()
                .align(Alignment.CenterVertically)
        )
        MinutePicker(
            minuteIdx = minuteIdx,
            setMinuteIdx = setMinuteIdx,
            minuteList = minuteList
        )
    }
}


//AM/PM 변경
@Composable
fun SwitchMode(mode: Int, changeMode: (Int) -> Unit) {
    val currentMode by rememberUpdatedState(mode)
    LocalContext.current
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clickable {
                val newMode = if (mode == R.string.am) R.string.pm else R.string.am
                changeMode(newMode)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(currentMode),
            color = Color(0xFF2962FF),
            fontSize = 30.sp
        )
    }
}


//시간 선택
@Composable
fun HourPicker(
    hourIdx: Int,
    setHourIdx: (Int) -> Unit,
    hourList: List<Int>,
) {
    val displayedHour = List(3) { i ->
        hourList[(hourIdx + i - 1 + hourList.size) % hourList.size]
    }
    var dragOffsetHour by remember { mutableFloatStateOf(0f) }
    val animatedDragOffsetHour by animateFloatAsState(targetValue = dragOffsetHour, label = "dragOffsetHour")

    val currentHourIdxState = rememberUpdatedState(hourIdx)
    val currentSetHourIdxState = rememberUpdatedState(setHourIdx)

    val density = LocalDensity.current
    val itemHeight = 50.dp
    val itemHeightPx = with(density) { itemHeight.toPx() }

    rememberCoroutineScope()

    Box(
        modifier = Modifier
            .width(80.dp)
            .clipToBounds()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragOffsetHour = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetHour += dragAmount
                        val currentIdx = currentHourIdxState.value
                        val setIdx = currentSetHourIdxState.value
                        if (dragOffsetHour > 25f) {
                            setIdx((currentIdx - 1 + hourList.size) % hourList.size)
                            dragOffsetHour = 0f
                        } else if (dragOffsetHour < -25f) {
                            setIdx((currentIdx + 1) % hourList.size)
                            dragOffsetHour = 0f
                        }
                    },
                    onDragEnd = { dragOffsetHour = 0f }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .border(1.dp, Color.Red)
                .fillMaxWidth()
                .graphicsLayer { translationY = animatedDragOffsetHour }
                .verticalScroll(rememberScrollState(), enabled = false),
            verticalArrangement = Arrangement.spacedBy(15.dp, Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            displayedHour.forEachIndexed { index, hour ->
                // 중앙(index 1)으로부터의 거리 계산
                // index 1일 때 offset은 0, index 0은 -itemHeightPx, index 2는 +itemHeightPx
                // 여기에 현재 드래그 오프셋(animatedDragOffsetHour)을 더해서 실제 시각적 거리를 구함
                val itemBaseOffset = (index - 1) * itemHeightPx
                val currentOffset = itemBaseOffset + animatedDragOffsetHour
                val distance = abs(currentOffset)

                // 거리에 따른 비율 계산 (0일 때 1.0, itemHeightPx일 때 0.0)
                val fraction = (1f - (distance / itemHeightPx)).coerceIn(0f, 1f)

                // 크기와 투명도 보간
                val fontSize = (30 + (15 * fraction)).sp // 30.sp ~ 45.sp
                val alpha = 0.4f + (0.6f * fraction) // 0.4 ~ 1.0
                val color = Color.White.copy(alpha = alpha)

                Text(
                    text = String.format(Locale.US, stringResource(R.string.time_format), hour),
                    fontSize = fontSize,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Yellow)
                        .height(itemHeight)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
    }
}

//분 선택
@Composable
fun MinutePicker(
    minuteIdx: Int,
    setMinuteIdx: (Int) -> Unit,
    minuteList: List<Int>,
) {
    val displayedMinute = List(3) { i ->
        minuteList[(minuteIdx + i - 1 + minuteList.size) % minuteList.size]
    }
    var dragOffsetMinute by remember { mutableFloatStateOf(0f) }
    val animatedDragOffsetMinute by animateFloatAsState(targetValue = dragOffsetMinute, label = "dragOffsetMinute")

    val currentMinuteIdxState = rememberUpdatedState(minuteIdx)
    val currentSetMinuteIdxState = rememberUpdatedState(setMinuteIdx)

    val density = LocalDensity.current
    val itemHeight = 50.dp
    val itemHeightPx = with(density) { itemHeight.toPx() }

    rememberCoroutineScope()

    Column(
        modifier = Modifier
            .border(3.dp,Color.Blue)
            .wrapContentHeight()
            .fillMaxWidth()
            .graphicsLayer { translationY = animatedDragOffsetMinute }
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(15.dp, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // 크기와 투명도 보간
        val fontSize = (30 + (15 * 0.5)).sp
        val alpha = 0.4f + (0.6f * 0.5)
        repeat(minuteList.size) {
            Text(
                text = String.format(Locale.US, stringResource(R.string.time_format), minuteList[it]),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
        }
//        displayedMinute.forEachIndexed { index, minute ->
//            // 중앙(index 1)으로부터의 거리 계산
//            val itemBaseOffset = (index - 1) * itemHeightPx
//            val currentOffset = itemBaseOffset + animatedDragOffsetMinute
//            val distance = abs(currentOffset)
//
//            // 거리에 따른 비율 계산
//            val fraction = (1f - (distance / itemHeightPx)).coerceIn(0f, 1f)
//
//            // 크기와 투명도 보간
//            val fontSize = (30 + (15 * fraction)).sp
//            val alpha = 0.4f + (0.6f * fraction)
//            val color = Color.White.copy(alpha = alpha)
//
//            Text(
//                text = String.format(Locale.US, stringResource(R.string.time_format), minute),
//                fontSize = fontSize,
//                color = color,
//                fontWeight = FontWeight.Bold,
//                textAlign = TextAlign.Center,
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(itemHeight)
//                    .wrapContentHeight(Alignment.CenterVertically),
//            )
//        }
    }
}

@Composable
fun SleepButton(
    navController: NavHostController,
    mode: String,
    selectedHour: Int,
    selectedMinute: Int
) {
    val measureRoute = stringResource(R.string.route_measure, mode, selectedHour, selectedMinute)
    Box(
        modifier = Modifier
            .wrapContentHeight()
            .border(2.dp, Color.Red)
            .fillMaxWidth()
            .background(Color(0xFF0A021D))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    )
    {
        Button(
            onClick = {
                navController.navigate(measureRoute)
            },
            modifier = Modifier
                .border(2.dp, Color.Green)
                .fillMaxWidth()
                .height(90.dp)
                .padding(16.dp)
                .background(Color(0xFF0A021D)),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2962FF),
                contentColor = Color.White
            )
        ) {
            Text(text = stringResource(R.string.button_sleep), fontSize = 28.sp, textAlign = TextAlign.Center)
        }
    }
}

//하단 탭 바
@Composable
fun BottomTabBar(
    tabs: List<String>,
    icons: List<Int>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    NavigationBar(
        modifier = Modifier
            .border(2.dp, Color.Green)
            .wrapContentHeight(),
        containerColor = Color(0xFF25066C),
        contentColor = Color.White,
    ) {
        tabs.forEachIndexed { index, tab ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onTabSelected(index) }
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = icons[index]),
                        contentDescription = tab,
                        modifier = Modifier.size(36.dp),
                        tint = if (selectedTab == index) Color.White else Color(0xbdc0cbe6)
                    )
                    Text(
                        text = tab,
                        fontSize = 12.sp,
                        color = if (selectedTab == index) Color.White else Color(0xbdc0cbe6)
                    )
                }
            }
        }
    }
}
