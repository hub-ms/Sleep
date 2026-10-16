package com.soundsleeper.app.platform

import androidx.compose.runtime.Composable

/**
 * 지금 충전기가 꽂혀 있는지.
 *
 * 권한 안내 화면에서 "충전기가 분리되어 있어요" 경고를 띄울지 결정하는 데만 쓴다.
 * 감지할 수 없는 플랫폼은 true(=꽂혀 있음)로 답해서, 확인되지 않은 상태를 근거로
 * 사용자에게 잘못된 경고를 띄우지 않는다.
 */
@Composable
expect fun rememberIsCharging(): Boolean
