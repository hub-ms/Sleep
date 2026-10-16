package com.soundsleeper.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.domain.model.SleepAnimalType
import com.soundsleeper.app.enum_.AuthProvider
import com.soundsleeper.app.enum_.LoginButtonType
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.email_awaiting_link_description
import com.soundsleeper.app.resources.email_awaiting_link_title
import com.soundsleeper.app.resources.email_device_account_label
import com.soundsleeper.app.resources.email_open_mail_app
import com.soundsleeper.app.resources.email_save_card_cta
import com.soundsleeper.app.resources.email_use_another_address
import com.soundsleeper.app.resources.ic_email
import com.soundsleeper.app.ui.component.AnimalRevealCard
import com.soundsleeper.app.ui.component.AuthMethod
import com.soundsleeper.app.ui.component.LoginButton
import com.soundsleeper.app.ui.onboarding.OnboardingButton
import com.soundsleeper.app.ui.theme.IconSize
import com.soundsleeper.app.ui.theme.SleepTheme
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.bodyText
import com.soundsleeper.app.ui.theme.caption
import com.soundsleeper.app.ui.theme.sectionTitle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 앱을 깔고 처음 이메일로 가입하는 화면.
 *
 * **텍스트 필드가 없다.** OS 가 알려준 주소(`SocialAuthManager.getDeviceEmailHint`)를
 * 보여주고 버튼 한 번으로 끝낸다. 주소를 가져오지 못하면 호출부가 기존 입력 화면
 * ([EmailAuthContent])으로 폴백하므로 여기서는 [email] 이 항상 있다.
 *
 * ## 왜 "누르면 바로 가입 완료"가 아닌가
 *
 * 클라이언트가 주장한 주소만으로 가입을 끝내면 **아무 주소로나 남의 계정을 만들 수 있다.**
 * 주소 소유 증명이 한 번은 있어야 한다. 그래서 버튼은 인증 메일을 보내고 메일앱을 바로
 * 열어 준다 - 사용자는 메일의 링크를 한 번 누르기만 하면 되고(`sleepapp://auth/auto-complete`
 * 딥링크 → [AuthContract.Intent.DeepLinkAuthSuccess]), 6자리 코드를 외워 옮겨 적을 필요도,
 * 주소를 타이핑할 필요도 없다. 키보드를 한 번도 열지 않는다는 목표는 그대로 지켜진다.
 *
 * 설문에서 나온 동물 캐릭터를 카드로 띄워 두는 것은, 가입 버튼이 "계정을 만든다"가 아니라
 * "지금 보고 있는 내 카드를 저장한다"로 읽히게 하기 위해서다.
 */
@Composable
fun EmailFirstSignUpContent(
    email: String,
    animalType: SleepAnimalType?,
    isCodeSent: Boolean,
    isLoading: Boolean,
    onSaveCard: () -> Unit,
    onOpenMailApp: () -> Unit,
    onUseAnotherAddress: () -> Unit,
    onSocialLogin: (AuthProvider) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepTheme.background)
            .systemBarsPadding()
            // 이 화면에는 입력 필드가 없어 키보드가 뜨지 않는다. 그래도 걸어 두는 이유는
            // 접근성 도구나 외부 키보드가 IME 영역을 차지하는 경우를 대비해서다.
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(16.dp))
            if (animalType != null) {
                AnimalRevealCard(animal = animalType, showSummary = true)
            }
            Spacer(Modifier.height(24.dp))

            if (isCodeSent) {
                AwaitingLinkBlock(email = email, onOpenMailApp = onOpenMailApp)
            } else {
                DeviceAccountRow(email = email)
            }
            Spacer(Modifier.height(16.dp))
        }

        if (!isCodeSent) {
            OnboardingButton(
                text = stringResource(Res.string.email_save_card_cta),
                enabled = !isLoading,
                onClick = onSaveCard,
            )
        }
        Text(
            text = stringResource(Res.string.email_use_another_address),
            style = MaterialTheme.typography.caption,
            color = Color.White.copy(alpha = 0.6f),
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { onUseAnotherAddress() }
                .padding(vertical = 12.dp, horizontal = 8.dp),
        )

        // 이메일이 막혔거나 다른 계정을 쓰고 싶은 사람을 위한 탈출구. 화면 맨 아래
        // (= 입력 필드가 있었다면 키보드가 떴을 자리)에 가로 2열로 둔다.
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoginButton(
                method = AuthMethod.Member(provider = AuthProvider.GOOGLE),
                shape = LoginButtonType.Compact,
                modifier = Modifier.weight(1f),
                onClick = { onSocialLogin(AuthProvider.GOOGLE) },
            )
            LoginButton(
                method = AuthMethod.Member(provider = AuthProvider.KAKAO),
                shape = LoginButtonType.Compact,
                modifier = Modifier.weight(1f),
                onClick = { onSocialLogin(AuthProvider.KAKAO) },
            )
        }
    }
}

/** 가입에 쓸 주소. 고칠 수 없다는 뜻이 보이도록 입력칸이 아니라 읽기 전용 줄로 그린다. */
@Composable
private fun DeviceAccountRow(email: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.email_device_account_label),
            style = MaterialTheme.typography.caption,
            color = Color.White.copy(alpha = 0.6f),
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.06f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_email),
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(IconSize.Inline),
                )
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyText,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

/** 메일을 보낸 뒤. 이제 할 일은 메일함에서 링크를 누르는 것 하나뿐이다. */
@Composable
private fun AwaitingLinkBlock(email: String, onOpenMailApp: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.email_awaiting_link_title),
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = email,
            style = MaterialTheme.typography.bodyText,
            fontWeight = FontWeight.Bold,
            color = primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.email_awaiting_link_description),
            style = MaterialTheme.typography.caption.copy(lineBreak = LineBreak.Paragraph),
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = stringResource(Res.string.email_open_mail_app),
                style = MaterialTheme.typography.bodyText,
                color = primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable { onOpenMailApp() }
                    .padding(vertical = 8.dp, horizontal = 12.dp),
            )
        }
    }
}
