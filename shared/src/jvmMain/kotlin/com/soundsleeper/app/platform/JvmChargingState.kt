package com.soundsleeper.app.platform

import androidx.compose.runtime.Composable

/** 데스크톱(프리뷰·테스트)에서는 충전 여부를 알 수 없으므로 경고를 띄우지 않는다. */
@Composable
actual fun rememberIsCharging(): Boolean = true
