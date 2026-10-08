package com.somniotune.app.ui.setting

import android.graphics.Color as AndroidColor
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.aakira.napier.Napier

@Composable
actual fun LegalWebView(
    url: String,
    onLoadFailed: () -> Unit,
    modifier: Modifier,
) {
    // WebViewClient 는 AndroidView factory 안에서 한 번만 만들어지므로, 콜백이 재구성 때마다
    // 바뀌어도 최신 것을 보게 rememberUpdatedState 로 감싼다.
    val latestOnLoadFailed by rememberUpdatedState(onLoadFailed)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                // 흰 배경이 한 번 번쩍이지 않게 앱 배경색으로 맞춰 둔다.
                setBackgroundColor(AndroidColor.parseColor("#030418"))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        // 하위 리소스(이미지·폰트) 실패로 문서 전체를 포기하지는 않는다.
                        if (request?.isForMainFrame != true) return
                        Napier.w("법적 고지 웹뷰 로딩 실패: url=$url, code=${error?.errorCode}")
                        latestOnLoadFailed()
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        if (request?.isForMainFrame != true) return
                        // 문서를 아직 공개 발행하지 않았으면 Notion 이 404 를 준다.
                        // 폴백과 구분이 안 되면 원인 파악이 어려우므로 상태 코드를 남긴다.
                        Napier.w(
                            "법적 고지 웹뷰 HTTP 오류: url=$url, status=${errorResponse?.statusCode}"
                        )
                        latestOnLoadFailed()
                    }
                }
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url) webView.loadUrl(url)
        },
    )
}
