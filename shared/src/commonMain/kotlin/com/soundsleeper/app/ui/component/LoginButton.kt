package com.soundsleeper.app.ui.component

import com.soundsleeper.app.resources.auth_continue_email
import com.soundsleeper.app.resources.auth_continue_google
import com.soundsleeper.app.resources.auth_continue_kakao
import com.soundsleeper.app.resources.auth_provider_kakao
import com.soundsleeper.app.resources.auth_provider_google
import com.soundsleeper.app.resources.auth_provider_email
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.enum_.AuthProvider
import com.soundsleeper.app.enum_.LoginButtonType
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.ic_email
import com.soundsleeper.app.resources.ic_google
import com.soundsleeper.app.resources.ic_kakao
import com.soundsleeper.app.ui.theme.IconSize
import com.soundsleeper.app.ui.theme.bodyHighlight
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

data class LoginUi(
    val backgroundColor: Color,
    val iconRes: DrawableResource,
    val contentColor: Color,
)
/**
 * @param modifier 바깥에서 폭을 정해야 할 때 쓴다(예: 두 개를 가로로 반씩 나눠 놓는 경우).
 *   기본값일 때의 폭·높이는 [shape] 가 정한다.
 */
@Composable
fun LoginButton(
    method: AuthMethod,
    shape: LoginButtonType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = method.toUi()
    val shapeModifier = when (shape) {
        LoginButtonType.Circle -> Modifier.size(64.dp)
        LoginButtonType.FullWidth, LoginButtonType.Compact -> Modifier
            .fillMaxWidth()
            .height(56.dp)
    }
    val buttonShape = when (shape) {
        LoginButtonType.Circle -> CircleShape
        LoginButtonType.FullWidth, LoginButtonType.Compact -> RoundedCornerShape(12.dp)
    }
    // style 파라미터는 호출부 호환을 위해 남겨 두지만, 소셜 로그인 버튼은 모두 채워진 스타일로
    // 통일한다. 테두리형과 채움형이 섞여 있으면 어느 쪽이 주된 선택인지 읽히지 않는다.
    Button(
        onClick = onClick,
        modifier = modifier.then(shapeModifier),
        shape = buttonShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ui.backgroundColor,
            contentColor = ui.contentColor
        )
    ) {
        LoginButtonContent(
            shape = shape,
            method = method,
            ui = ui
        )
    }
}

@Composable
private fun LoginButtonContent(
    shape: LoginButtonType,
    method: AuthMethod,
    ui: LoginUi
) {
    when (shape) {
        LoginButtonType.Circle -> {
            Image(
                painter = painterResource(ui.iconRes),
                contentDescription = null,
                modifier = Modifier.size(IconSize.Default)
            )
        }
        LoginButtonType.FullWidth -> {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(ui.iconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        // contentPadding 이 0이라 아이콘이 버튼 왼쪽 모서리에 붙어 있었다.
                        .padding(start = 20.dp)
                        .size(IconSize.Default)
                )
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = method.getLabel(),
                    style = MaterialTheme.typography.bodyHighlight,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        LoginButtonType.Compact -> {
            // 반 폭에서는 "카카오톡으로 계속하기" 같은 긴 라벨이 들어가지 않는다.
            // 짧은 공급자 이름을 쓰고 아이콘과 한 줄에 나란히 둔다.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(ui.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Default)
                )
                Text(
                    text = method.getShortLabel(),
                    style = MaterialTheme.typography.bodyHighlight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 반 폭 버튼에 들어갈 짧은 이름("구글"/"카카오"). 전체 문장 라벨은 [getLabel]. */
@Composable
fun AuthMethod.getShortLabel(): String = when (this) {
    is AuthMethod.Member -> when (provider) {
        AuthProvider.KAKAO -> stringResource(Res.string.auth_provider_kakao)
        AuthProvider.GOOGLE -> stringResource(Res.string.auth_provider_google)
        AuthProvider.EMAIL -> stringResource(Res.string.auth_provider_email)
    }
}

sealed class AuthMethod {
    data class Member(val provider: AuthProvider) : AuthMethod()
}

// 라벨이 하드코딩 한국어였을 때는 영어 기기에서도 "카카오톡으로 계속하기"가 그대로 떴다.
// 문자열 리소스를 읽게 되면서 @Composable 이 됐다.
@Composable
fun AuthMethod.getLabel(): String {
    return when (this) {
        is AuthMethod.Member -> {
            when (provider) {
                AuthProvider.KAKAO -> stringResource(Res.string.auth_continue_kakao)
                AuthProvider.GOOGLE -> stringResource(Res.string.auth_continue_google)
                AuthProvider.EMAIL -> stringResource(Res.string.auth_continue_email)
            }
        }
    }
}

fun AuthMethod.toUi(): LoginUi {
    return when (this) {
        is AuthMethod.Member -> {
            when (provider) {
                AuthProvider.KAKAO -> LoginUi(
                    backgroundColor = Color(0xFFFEE500),
                    iconRes = Res.drawable.ic_kakao,
                    contentColor = Color(0xD9000000)
                )

                AuthProvider.GOOGLE -> LoginUi(
                    backgroundColor = Color.White,
                    iconRes = Res.drawable.ic_google,
                    contentColor = Color(0xFF1F1F1F)
                )
                AuthProvider.EMAIL -> LoginUi(
                    backgroundColor = Color.White,
                    iconRes = Res.drawable.ic_email,
                    contentColor = Color(0xFF1F1F1F)
                )
            }
        }
    }
}
