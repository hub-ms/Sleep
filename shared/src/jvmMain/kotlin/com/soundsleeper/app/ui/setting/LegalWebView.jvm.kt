package com.soundsleeper.app.ui.setting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

// 데스크톱(JVM)에는 쓸 수 있는 내장 웹뷰가 없다. 빈 화면을 띄우는 대신 즉시 실패를 알려
// 호출부가 네이티브 렌더링으로 떨어지게 한다 — JVM 에서는 항상 네이티브 경로를 타게 된다.
@Composable
actual fun LegalWebView(
    url: String,
    onLoadFailed: () -> Unit,
    modifier: Modifier,
) {
    // 컴포지션 중에 바로 호출하면 상태를 그 프레임에 쓰게 되므로 LaunchedEffect 로 미룬다.
    LaunchedEffect(url) { onLoadFailed() }
}
