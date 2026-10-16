package com.soundsleeper.app.platform

import androidx.compose.runtime.Composable

/**
 * 시스템 뒤로가기를 화면 안에서 가로챈다.
 *
 * Voyager 는 안드로이드에서 시스템 뒤로가기를 받으면 현재 Screen 을 pop 한다. 한 Screen
 * 안에서 페이저로 여러 단계를 넘기는 화면(온보딩 설문)에서는 그 기본 동작이 틀렸다 —
 * 2번 문항에서 뒤로 가면 1번 문항으로 가야지, 설문 전체를 빠져나가면 안 된다.
 *
 * Compose Multiplatform 의 `androidx.compose.ui.backhandler.BackHandler` 를 쓰지 않는다.
 * 별도 아티팩트(`ui-backhandler`)인 데다 1.12 에서 이미 통째로 deprecated 되었고
 * (`NavigationEventHandler` 로 대체 예정), 그 대체 API 는 또 다른 의존성에 있다.
 * 실제로 출시하는 타깃이 안드로이드뿐이므로 플랫폼의 안정 API 를 그대로 쓴다.
 *
 * @param enabled false 면 가로채지 않고 뒤로가기가 평소대로 동작한다.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
