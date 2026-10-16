package com.soundsleeper.app.ui.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.license_title
import com.soundsleeper.app.ui.component.ScreenTopBar
import com.soundsleeper.app.ui.theme.SleepTheme
import org.jetbrains.compose.resources.stringResource

/**
 * 오픈소스 라이선스 화면.
 *
 * 예전에는 Scaffold + 상단 탭으로 "오픈소스"/"음원 크레딧" 두 화면을 묶어 뒀다. 음원 크레딧 쪽은
 * 이 파일에 하드코딩된 곡 제목 문자열 목록일 뿐 저자·라이선스 정보가 없어서 크레딧 역할을 하지
 * 못했고, 그 하나 때문에 탭 하나짜리 상단바가 남아 있었다. 크레딧을 걷어내고 라이브러리 목록만
 * 바로 보여준다.
 *
 * 배경은 다른 화면과 같은 SleepTheme.background 를 쓰고, 상태바 패딩과 상단바를 둔다.
 * 예전에는 셋 다 없어서 이 화면만 앱에서 떨어져 보였다.
 */
@Composable
fun LicenseCreditContent(onBackClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepTheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ScreenTopBar(
            title = stringResource(Res.string.license_title),
            onBackClick = onBackClick,
        )
        LibraryListSection()
    }
}

@Composable
expect fun LibraryListSection()
