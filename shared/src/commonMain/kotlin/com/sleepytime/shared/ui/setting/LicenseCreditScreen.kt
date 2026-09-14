package com.sleepytime.shared.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.LibraryDefaults
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

@Composable
fun LibraryListSection() {
    LibrariesContainer(
        null,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        colors = LibraryDefaults.libraryColors(
            backgroundColor = Color.Transparent,
            contentColor = Color.White,
            badgeBackgroundColor = MaterialTheme.colorScheme.primary.copy(0.4f),
            badgeContentColor = MaterialTheme.colorScheme.primary,
        )
    )
}

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
