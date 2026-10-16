package com.soundsleeper.app.platform

import androidx.compose.runtime.Composable

/** JVM 타깃은 테스트·Spring 용이라 UI 가 뜨지 않는다. 가로챌 뒤로가기도 없다. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
