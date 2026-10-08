package com.soundsleeper.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.enum_.AuthProvider
import com.soundsleeper.app.enum_.LoginButtonType
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.auth_email_login
import com.soundsleeper.app.resources.auth_guest_browse
import com.soundsleeper.app.resources.auth_terms_prefix
import com.soundsleeper.app.resources.auth_terms_suffix
import com.soundsleeper.app.resources.bg_splash
import com.soundsleeper.app.resources.common_background_image_description
import com.soundsleeper.app.resources.common_privacy_policy
import com.soundsleeper.app.resources.common_terms_of_service
import com.soundsleeper.app.resources.onboarding_cta_last
import com.soundsleeper.app.resources.onboarding_page3_description
import com.soundsleeper.app.resources.onboarding_page3_title
import com.soundsleeper.app.resources.onboarding_skip
import com.soundsleeper.app.ui.component.AuthMethod
import com.soundsleeper.app.ui.component.LoginButton
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.caption
import com.soundsleeper.app.ui.theme.bodyText
import com.soundsleeper.app.ui.theme.sectionTitle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 가치 제안 화면. 예전에는 3장짜리 페이저였지만, 처음 보는 사람에게는 "그래서 무엇을
 * 하면 되는지"(해결) 한 장만으로도 충분하고, 장이 많을수록 설문·가입 전에 이탈할 자리가
 * 늘어난다. 로그인은 별도 화면이고, 그 사이에 설문이 들어간다.
 *
 * 문구와 버튼은 화면 아래쪽에 모아 둔다. 위에 붙여 두면 배경 일러스트를 가리고,
 * 엄지가 닿는 자리에서 멀어진다.
 */
@Composable
fun OnboardingContent(
    onStartSurvey: () -> Unit,
    onSkip: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingBackground()
        Column(
            modifier = Modifier
                // systemBars 가 내비게이션 바까지 포함하므로 navigationBarsPadding 을
                // 따로 겹쳐 주지 않는다 — 예전에는 둘을 함께 적용해 아래 여백이 두 번 들어갔다.
                .systemBarsPadding()
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 위쪽을 비워 문구+CTA 블록을 아래로 내린다.
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.onboarding_page3_title),
                style = MaterialTheme.typography.sectionTitle,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(Res.string.onboarding_page3_description),
                style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Paragraph),
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            OnboardingButton(
                text = stringResource(Res.string.onboarding_cta_last),
                onClick = onStartSurvey
            )
            Text(
                text = stringResource(Res.string.onboarding_skip),
                style = MaterialTheme.typography.bodyText,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .clickable { onSkip() }
                    .padding(vertical = 10.dp, horizontal = 4.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 온보딩 흐름 전체(가치 제안 → 설문 → 진단 결과 → 가입)가 공유하는 배경.
 *
 * 스플래시와 같은 이미지를 쓴다. 예전에는 `bg_onboarding` 을 따로 두어 앱을 켠 직후
 * 배경이 한 번 바뀌었다 — 같은 장면이 이어지는 편이 흐름이 끊기지 않는다.
 */
@Composable
fun OnboardingBackground() {
    Image(
        painter = painterResource(Res.drawable.bg_splash),
        contentDescription = stringResource(Res.string.common_background_image_description),
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

/**
 * 가입/로그인 화면의 로그인 블록.
 *
 * 카카오/구글은 풀폭 CTA로 올려 위계를 주고, 이메일과 게스트 시작은 텍스트 링크로 내렸다.
 * 애플 로그인 버튼은 온보딩에서만 제거했다 — 계정 설정의 애플 연동과 로그인 혜택 화면은 그대로다.
 *
 * 예전에는 `Modifier.fillMaxSize()` 만 걸려 있어 로그인 버튼이 화면 최상단에 붙었다.
 * 아래로 모으고 좌우·하단 여백을 준다.
 */
@Composable
fun AuthActions(
    onGuestLogin: () -> Unit,
    onSocialLogin: (AuthProvider) -> Unit,
    onEmailLogin: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .systemBarsPadding()
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoginButton(
            method = AuthMethod.Member(provider = AuthProvider.KAKAO),
            shape = LoginButtonType.FullWidth,
            onClick = {
                onSocialLogin(AuthProvider.KAKAO)
            }
        )
        LoginButton(
            method = AuthMethod.Member(provider = AuthProvider.GOOGLE),
            shape = LoginButtonType.FullWidth,
            onClick = {
                onSocialLogin(AuthProvider.GOOGLE)
            }
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.auth_email_login),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onEmailLogin() }
            )
            Text(
                text = stringResource(Res.string.auth_guest_browse),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onGuestLogin() }
            )
        }

        // 별도 체크박스 없이 진행만으로 동의한 것으로 보므로, 그 사실과 원문 링크를
        // 로그인 수단 바로 아래에 둔다.
        //
        // 예전에는 Row 안에 Text 세 개를 늘어놓고 "에 동의하는 것으로 간주합니다"만 Row
        // 바깥 아래에 따로 뒀다. Row 는 줄바꿈을 못 해서 좁은 화면에서는 조각들이 눌려
        // 잘렸고, 넓은 화면에서도 문장이 늘 같은 자리에서 강제로 끊겼다. 한 문장을 하나의
        // AnnotatedString 으로 만들면 자연스럽게 줄이 나뉘고, 링크는 LinkAnnotation 이
        // 글자 단위로 터치를 받으므로 clickable+padding 트릭도 필요 없다.
        val linkStyle = SpanStyle(
            color = Color.White.copy(alpha = 0.9f),
            textDecoration = TextDecoration.Underline
        )
        val termsLabel = stringResource(Res.string.common_terms_of_service)
        val privacyLabel = stringResource(Res.string.common_privacy_policy)
        Text(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            text = buildAnnotatedString {
                append(stringResource(Res.string.auth_terms_prefix))
                withLink(LinkAnnotation.Clickable("terms") { onNavigateToTerms() }) {
                    withStyle(linkStyle) { append(termsLabel) }
                }
                append(", ")
                withLink(LinkAnnotation.Clickable("privacy") { onNavigateToPrivacy() }) {
                    withStyle(linkStyle) { append(privacyLabel) }
                }
                append(stringResource(Res.string.auth_terms_suffix))
            },
            style = MaterialTheme.typography.caption,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OnboardingButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = primary,
            contentColor = Color.White,
            // 조건을 못 채웠을 때 "눌러도 안 된다"가 보이게 한다. 예전에는 흐린 primary 라
            // 활성 버튼의 옅은 버전처럼 보여서 눌리는 버튼으로 읽혔다. 무채색으로 바꿔
            // 비활성임을 분명히 한다.
            disabledContainerColor = Color.White.copy(alpha = 0.10f),
            disabledContentColor = Color.White.copy(alpha = 0.35f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.sectionTitle,
            textAlign = TextAlign.Center
        )
    }
}
