package com.sleepytime.shared.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.ui.theme.sectionTitle

private val sleepMusicCredits = listOf(
    "Rain on Roof",
    "Ocean Waves",
    "Forest Stream",
    "Summer Night Crickets",
    "Ambient Relaxation",
    "Deep Space Ambient",
    "Theta Waves",
    "Underwater Dream"
)

private val alarmMusicCredits = listOf(
    "Morning Birds",
    "Gentle Waves",
    "Soft Piano",
    "Upbeat Morning",
    "Field Crickets"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseCreditContent(
    sleepCredits: List<String> = sleepMusicCredits,
    alarmCredits: List<String> = alarmMusicCredits,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("오픈소스", "음원 크레딧")

    Scaffold(
        topBar = {
            Column {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> LibraryListSection()
                1 -> SoundCreditSection(sleepCredits, alarmCredits)
            }
        }
    }
}

// 오픈소스 라이브러리 목록을 실제로 불러와 표시하는 부분은 플랫폼마다 구현이 다르다.
// (안드로이드: AboutLibraries가 빌드 시 생성한 res/raw/aboutlibraries.json을 Context로 자동 탐색해 읽어온다.)
// 이전에는 LibrariesContainer(null, ...)을 직접 호출했는데, 이 시그니처는 아무 데이터도 자동으로
// 불러오지 않고 항상 빈 목록을 그리는 공통(commonMain) 오버로드로 연결되어, 탭을 눌러도 빈 화면만
// 보이는 원인이었다.
@Composable
expect fun LibraryListSection()

@Composable
fun SoundCreditSection(
    sleepCredits: List<String>,
    alarmCredits: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "수면 유도 음악",
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White
        )
        SettingCard {
            sleepCredits.forEach { title ->
                SoundCreditItem(title)
            }
        }

        Text(
            text = "기상 알람 음악",
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White
        )
        SettingCard {
            alarmCredits.forEach { title ->
                SoundCreditItem(title)
            }
        }
    }
}

@Composable
private fun SoundCreditItem(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
