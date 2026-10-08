package com.somniotune.app.ui.setting

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 법적 고지 문서를 웹으로 띄운다.
 *
 * 약관 원문은 Notion 에서 관리하므로, 앱 배포 없이 문구를 고칠 수 있는 웹뷰를 먼저 보여주고
 * 실패하면 번들된 JSON 기반 네이티브 화면으로 떨어진다.
 *
 * @param onLoadFailed 페이지를 띄우지 못했을 때 호출된다. 네트워크 단절, 404(문서를 아직
 *   발행하지 않은 경우), 웹뷰를 제공하지 않는 플랫폼이 모두 이 경로로 들어온다.
 *   호출부는 이 신호를 받으면 네이티브 렌더링으로 전환한다.
 */
@Composable
expect fun LegalWebView(
    url: String,
    onLoadFailed: () -> Unit,
    modifier: Modifier = Modifier,
)
