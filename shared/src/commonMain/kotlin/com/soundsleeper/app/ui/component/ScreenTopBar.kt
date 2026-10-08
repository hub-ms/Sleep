package com.soundsleeper.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.common_back
import com.soundsleeper.app.resources.ic_caret_left
import com.soundsleeper.app.ui.theme.IconSize
import com.soundsleeper.app.ui.theme.sectionTitle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 하위 화면의 상단바 — 왼쪽에 뒤로가기 화살표, 가운데에 화면 이름.
 *
 * 예전에는 화면마다 Row 로 "화살표 + 왼쪽 정렬 제목"을 직접 조립했고, 수면 설정 화면에는
 * 아예 상단바가 없어 시스템 뒤로가기 말고는 나갈 길이 없었다. 같은 코드가 다섯 군데에
 * 복제돼 있던 것을 하나로 모은다.
 *
 * 제목을 [Box] 중앙에 두고 화살표를 그 위에 겹쳐 놓는 이유: Row 로 늘어놓으면 제목이
 * 화살표 폭만큼 오른쪽으로 밀려 화면 중앙이 아니게 된다.
 */
@Composable
fun ScreenTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                // 긴 제목이 화살표 위로 겹쳐 그려지지 않게 양쪽을 비워 둔다.
                .padding(horizontal = 56.dp)
        )
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_caret_left),
                contentDescription = stringResource(Res.string.common_back),
                tint = Color.White,
                modifier = Modifier.size(IconSize.Default)
            )
        }
    }
}
