package com.soundsleeper.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
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
import com.soundsleeper.app.resources.bg_layer
import com.soundsleeper.app.resources.common_background_image_description
import com.soundsleeper.app.resources.common_next
import com.soundsleeper.app.resources.common_privacy_policy
import com.soundsleeper.app.resources.common_terms_of_service
import com.soundsleeper.app.resources.onboarding_advice_layer
import com.soundsleeper.app.resources.onboarding_analysis_layer
import com.soundsleeper.app.resources.onboarding_cta_last
import com.soundsleeper.app.resources.onboarding_music_layer
import com.soundsleeper.app.resources.onboarding_page1_description
import com.soundsleeper.app.resources.onboarding_page1_title
import com.soundsleeper.app.resources.onboarding_page2_description
import com.soundsleeper.app.resources.onboarding_page2_title
import com.soundsleeper.app.resources.onboarding_page3_description
import com.soundsleeper.app.resources.onboarding_page3_title
import com.soundsleeper.app.resources.onboarding_skip
import com.soundsleeper.app.ui.component.AuthMethod
import com.soundsleeper.app.ui.component.LoginButton
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.bodyText
import com.soundsleeper.app.ui.theme.caption
import com.soundsleeper.app.ui.theme.sectionTitle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 가치 제안 3장.
 *
 * 한 장으로 줄였던 적이 있다. 장이 많을수록 설문·가입 전에 이탈할 자리가 늘어난다는 이유였는데,
 * 그때는 보여줄 일러스트가 공용 배경 한 장뿐이라 장을 늘려도 같은 그림에 글자만 바뀌었다.
 * 지금은 기능별 일러스트 세 장(음악/분석/조언)이 있어서, 장마다 **다른 그림으로 다른 약속**을
 * 보여줄 수 있다.
 *
 * 문구와 버튼은 화면 아래쪽에 모아 둔다. 위에 붙여 두면 일러스트를 가리고, 엄지가 닿는
 * 자리에서 멀어진다.
 */
private data class OnboardingPage(
    val image: DrawableResource,
    val titleRes: StringResource,
)

private val onboardingPages = listOf(
    OnboardingPage(
        image = Res.drawable.onboarding_music_layer,
        titleRes = Res.string.onboarding_page1_title,
    ),
    OnboardingPage(
        image = Res.drawable.onboarding_analysis_layer,
        titleRes = Res.string.onboarding_page2_title,
    ),
    OnboardingPage(
        image = Res.drawable.onboarding_advice_layer,
        titleRes = Res.string.onboarding_page3_title,
    ),
)

@Composable
fun OnboardingContent(
    onStartSurvey: () -> Unit,
    onSkip: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingBackground()

        // 일러스트는 페이저 안에서 글자와 **함께** 넘어가야 한다. 배경(별 하늘)만 고정이고
        // 캐릭터+문구가 한 덩어리로 스와이프되어야 장이 바뀐 것으로 읽힌다.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            OnboardingPageContent(page = onboardingPages[page])
        }
        TextButton(
            modifier = Modifier
                .systemBarsPadding()
                .align(Alignment.TopEnd),
            onClick = {
                onSkip()
            }
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                text = stringResource(Res.string.onboarding_skip),
                style = MaterialTheme.typography.bodyText,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            PagerDots(
                currentPage = pagerState.currentPage,
                pageCount = onboardingPages.size,
            )
            OnboardingButton(
                text = if (isLastPage) stringResource(Res.string.onboarding_cta_last)
                else stringResource(Res.string.common_next),
                onClick = {
                    if (isLastPage) onStartSurvey()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            )

        }
    }
}

/**
 * 한 장의 일러스트 + 문구.
 *
 * 아래 CTA 블록이 화면 하단에 겹쳐 떠 있으므로, 문구가 그 밑으로 숨지 않도록
 * [BOTTOM_CONTROLS_RESERVE] 만큼 아래를 비워 둔다.
 */
private val BOTTOM_CONTROLS_RESERVE = 190.dp

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .systemBarsPadding()
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = BOTTOM_CONTROLS_RESERVE),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(page.image),
                contentDescription = stringResource(Res.string.common_background_image_description),
                modifier = Modifier.fillMaxSize(),
                // Crop 하면 일러스트 가장자리의 고래 지느러미나 음표가 잘린다. Fit 으로
                // 전체를 보여주고 남는 자리는 배경 하늘이 채우게 둔다.
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

/** 몇 장 중 몇 번째인지. 설문의 진행률 바와 달리 여기서는 장 수가 적어 점이 더 읽기 쉽다. */
@Composable
private fun PagerDots(currentPage: Int, pageCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pageCount) { index ->
            val color by animateColorAsState(
                targetValue = if (index == currentPage) primary else Color.White.copy(alpha = 0.25f),
                label = "pagerDot"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

/**
 * 온보딩 흐름 전체(가치 제안 → 설문 → 가입)가 공유하는 배경.
 *
 * 스플래시와 같은 밤하늘을 쓴다. 예전에는 `bg_onboarding` 을 따로 두어 앱을 켠 직후 배경이
 * 한 번 바뀌었다 - 같은 장면이 이어지는 편이 흐름이 끊기지 않는다.
 */
@Composable
fun OnboardingBackground() {
    Image(
        painter = painterResource(Res.drawable.bg_layer),
        contentDescription = stringResource(Res.string.common_background_image_description),
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

/**
 * 가입/로그인 화면의 로그인 블록.
 *
 * 카카오/구글은 풀폭 CTA로 올려 위계를 주고, 이메일과 게스트 시작은 텍스트 링크로 내렸다.
 * 애플 로그인 버튼은 온보딩에서만 제거했다 - 계정 설정의 애플 연동과 로그인 혜택 화면은 그대로다.
 *
 * 높이를 `fillMaxSize` 로 잡지 않는다. 가입 화면이 위쪽 절반을 동물 캐릭터 카드에 내주고
 * 이 블록을 아래쪽 절반에 넣기 때문에, 자기 내용만큼만 차지하고 배치는 호출부에 맡긴다.
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
            .fillMaxWidth()
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
