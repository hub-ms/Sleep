package com.sleepytime.shared.ui.setting

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.LibraryDefaults

// 안드로이드에서는 AboutLibraries Gradle 플러그인이 빌드 시 생성한 res/raw/aboutlibraries.json을
// LibrariesContainer의 기본 librariesBlock(Libs.Builder().withContext(context).build())이 Context를
// 통해 자동으로 탐색해 읽어온다. 별도의 JSON 로딩 코드 없이 이 오버로드를 사용하는 것만으로 충분하다.
@Composable
actual fun LibraryListSection() {
    LibrariesContainer(
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