package com.sleepytime.shared.ui.setting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// AboutLibraries의 자동 라이브러리 탐색(Libs.Builder().withContext(...))은 안드로이드 전용이라
// 데스크톱(JVM)에서는 아직 오픈소스 라이브러리 목록을 지원하지 않는다.
@Composable
actual fun LibraryListSection() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "오픈소스 라이브러리 정보는 이 플랫폼에서 준비 중입니다.",
            color = Color.White
        )
    }
}