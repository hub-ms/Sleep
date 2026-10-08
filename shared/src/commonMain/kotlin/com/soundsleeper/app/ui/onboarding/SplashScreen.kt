package com.soundsleeper.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.bg_splash
import com.soundsleeper.app.resources.splash_title
import com.soundsleeper.app.ui.theme.SleepTheme
import com.soundsleeper.app.ui.theme.sectionTitle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 앱을 켜면 가장 먼저 보이는 화면.
 *
 * 예전에는 안드로이드가 그려 주는 기본 스플래시(검은 배경 + 런처 아이콘)가 전부였고, 곧바로
 * 온보딩 페이저가 떴다. 로그인된 사용자는 그 온보딩이 스쳐 지나간 뒤에야 홈에 닿아서
 * 매번 잠깐 깜빡였다. 이 화면이 로그인 상태가 정해질 때까지 머물며 그 깜빡임을 없앤다.
 *
 * 예전에는 같은 문구를 Text 두 개로 겹쳐 그리고 있었다(한쪽만 textAlign 이 달랐다).
 * 둘 다 Box 중앙 정렬이라 글자가 겹쳐 두껍게 번져 보였다 — 하나만 남긴다.
 *
 * 캐릭터 일러스트도 함께 걷어냈다. `files/splash_character.json` 이 실제로는 없어서
 * Lottie 분기는 늘 null 이었고, 폴백 Canvas 는 크기 지정이 없어 0×0 으로 아무것도
 * 그리지 않았다. 지금은 배경 이미지와 문구만으로 화면을 구성한다.
 */
@Composable
fun SplashContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepTheme.background),
        contentAlignment = Alignment.Center
    ) {
        // contentScale 기본값인 Fit 은 이미지 비율이 화면과 다를 때 위아래에 배경색 띠를
        // 남긴다. Crop 으로 화면을 꽉 채운다 — 온보딩 배경도 같은 방식을 쓴다.
        Image(
            painter = painterResource(Res.drawable.bg_splash),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Text(
            text = stringResource(Res.string.splash_title),
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White,
            textAlign = TextAlign.Center,
            // 긴 번역문이 화면 양 끝에 닿지 않게 한다.
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
