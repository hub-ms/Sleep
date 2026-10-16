package com.soundsleeper.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.soundsleeper.app.resources.bg_layer
import com.soundsleeper.app.resources.common_background_image_description
import com.soundsleeper.app.resources.splash_layer
import com.soundsleeper.app.resources.splash_subtitle
import com.soundsleeper.app.resources.splash_title
import com.soundsleeper.app.ui.theme.SleepTheme
import com.soundsleeper.app.ui.theme.bodyText
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
 * 배경과 캐릭터를 **두 장으로 분리**해 둔다. `bg_layer` 는 별과 스포트라이트만 있는 밤하늘
 * (1080×2400, 화면 비율)이고 그 위에 고래 `splash_layer` 를 얹는다. 한 장으로 합쳐 두면
 * 화면 비율이 다를 때 Crop 이 고래까지 잘라먹는다 — 배경만 Crop 하고 고래는 폭 기준으로
 * 따로 맞추면 어떤 기기에서도 고래가 온전히 보인다. 온보딩 3장도 같은 구성을 쓴다.
 *
 * 캐릭터 일러스트를 Lottie 로 넣으려던 과거 시도는 걷어냈다. `files/splash_character.json`
 * 이 실제로는 없어서 Lottie 분기는 늘 null 이었고, 폴백 Canvas 는 크기 지정이 없어 0×0 으로
 * 아무것도 그리지 않았다.
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
        // 남긴다. Crop 으로 화면을 꽉 채운다 - 온보딩 배경도 같은 방식을 쓴다.
        Image(
            painter = painterResource(Res.drawable.bg_layer),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 긴 번역문이 화면 양 끝에 닿지 않게 한다.
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Image(
                painter = painterResource(Res.drawable.splash_layer),
                contentDescription = stringResource(Res.string.common_background_image_description),
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    // 원본이 정사각(512×512)이다. 비율을 고정해 두지 않으면 긴 화면에서
                    // 세로로 늘어난다.
                    .aspectRatio(1f),
                contentScale = ContentScale.Fit
            )
            Text(
                text = stringResource(Res.string.splash_title),
                style = MaterialTheme.typography.sectionTitle,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(Res.string.splash_subtitle),
                style = MaterialTheme.typography.bodyText,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
