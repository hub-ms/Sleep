package com.soundsleeper.app.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.enum_.AuthProvider
import com.soundsleeper.app.enum_.LoginButtonType
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.ic_email
import com.soundsleeper.app.resources.ic_google
import com.soundsleeper.app.resources.ic_kakao
import com.soundsleeper.app.ui.theme.bodyHighlight
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

data class LoginUi(
    val backgroundColor: Color,
    val iconRes: DrawableResource,
    val contentColor: Color,
)
@Composable
fun LoginButton(
    method: AuthMethod,
    shape: LoginButtonType,
    onClick: () -> Unit
) {
    val ui = method.toUi()
    val modifier = when (shape) {
        LoginButtonType.Circle -> Modifier.size(64.dp)
        LoginButtonType.FullWidth -> Modifier
            .fillMaxWidth()
            .height(56.dp)
    }
    val buttonShape = when (shape) {
        LoginButtonType.Circle -> CircleShape
        LoginButtonType.FullWidth -> RoundedCornerShape(12.dp)
    }
    // style 파라미터는 호출부 호환을 위해 남겨 두지만, 소셜 로그인 버튼은 모두 채워진 스타일로
    // 통일한다. 테두리형과 채움형이 섞여 있으면 어느 쪽이 주된 선택인지 읽히지 않는다.
    Button(
        onClick = onClick,
        modifier = modifier,
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
                modifier = Modifier.size(28.dp)
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
                        .size(24.dp)
                )
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = method.getLabel(),
                    style = MaterialTheme.typography.bodyHighlight,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

sealed class AuthMethod {
    data class Member(val provider: AuthProvider) : AuthMethod()
}

fun AuthMethod.getLabel(): String {
    return when (this) {
        is AuthMethod.Member -> {
            when (provider) {
                AuthProvider.KAKAO -> "카카오톡으로 계속하기"
                AuthProvider.GOOGLE -> "Google로 계속하기"
                AuthProvider.EMAIL -> "이메일로 시작하기"
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
