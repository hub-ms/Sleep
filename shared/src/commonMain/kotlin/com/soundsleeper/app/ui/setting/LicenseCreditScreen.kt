package com.soundsleeper.app.ui.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.common_back
import com.soundsleeper.app.resources.ic_caret_left
import com.soundsleeper.app.resources.license_title
import com.soundsleeper.app.ui.theme.SleepTheme
import com.soundsleeper.app.ui.theme.sectionTitle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 오픈소스 라이선스 화면.
 *
 * 예전에는 Scaffold + 상단 탭으로 "오픈소스"/"음원 크레딧" 두 화면을 묶어 뒀다. 음원 크레딧 쪽은
 * 이 파일에 하드코딩된 곡 제목 문자열 목록일 뿐 저자·라이선스 정보가 없어서 크레딧 역할을 하지
 * 못했고, 그 하나 때문에 탭 하나짜리 상단바가 남아 있었다. 크레딧을 걷어내고 라이브러리 목록만
 * 바로 보여준다.
 *
 * 배경은 평면 colorScheme.background 가 아니라 다른 화면과 같은 그라데이션을 쓰고, 상태바
 * 패딩과 제목/뒤로가기를 둔다. 예전에는 셋 다 없어서 이 화면만 앱에서 떨어져 보였다.
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(Res.drawable.ic_caret_left),
                    contentDescription = stringResource(Res.string.common_back),
                    tint = Color.White
                )
            }
            Text(
                text = stringResource(Res.string.license_title),
                style = MaterialTheme.typography.sectionTitle,
                color = Color.White
            )
        }
        LibraryListSection()
    }
}

@Composable
expect fun LibraryListSection()
