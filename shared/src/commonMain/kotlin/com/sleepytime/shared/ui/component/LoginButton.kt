package com.sleepytime.shared.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_apple
import com.sleepytime.shared.resources.ic_email
import com.sleepytime.shared.resources.ic_google
import com.sleepytime.shared.resources.ic_kakao
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

data class LoginUi(
    val backgroundColor: Color,
    val iconRes: DrawableResource,
)

enum class LoginButtonType {
    Circle,
    FullWidth
}

@Preview
@Composable
fun LoginButtonPreview() {
    LoginButton(
        method = AuthMethod.Member(AuthProvider.KAKAO),
        type = LoginButtonType.FullWidth,
        onClick = {}
    )
}

@Composable
fun LoginButton(
    method: AuthMethod,
    type: LoginButtonType,
    onClick: () -> Unit,
) {
    val ui = method.toUi()

    Button(
        onClick = onClick,
        modifier = when (type) {
            LoginButtonType.Circle -> Modifier.size(64.dp)
            LoginButtonType.FullWidth -> Modifier
                .fillMaxWidth()
                .height(56.dp)
        },
        shape = when (type) {
            LoginButtonType.Circle -> CircleShape
            LoginButtonType.FullWidth -> RoundedCornerShape(12.dp)
        },
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ui.backgroundColor
        )
    ) {

        when (type) {
            LoginButtonType.Circle -> {
                Image(
                    painter = painterResource(ui.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            }

            LoginButtonType.FullWidth -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Image(
                        painter = painterResource(ui.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = method.getLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }
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
            when(provider) {
                AuthProvider.KAKAO -> "카카오로 계속하기"
                AuthProvider.GOOGLE -> "Google로 시작하기"
                AuthProvider.APPLE -> "Apple로 시작하기"
                AuthProvider.EMAIL -> "이메일로 시작하기"
            }
        }
    }
}

fun AuthMethod.toUi(): LoginUi {
    return when (this) {
        is AuthMethod.Member -> {
            when(provider) {
                AuthProvider.KAKAO -> LoginUi(
                    backgroundColor = Color(0xFFFEE500),
                    iconRes = Res.drawable.ic_kakao
                )
                AuthProvider.GOOGLE -> LoginUi(
                    backgroundColor = Color.White,
                    iconRes = Res.drawable.ic_google
                )
                AuthProvider.APPLE -> LoginUi(
                    backgroundColor = Color.White,
                    iconRes = Res.drawable.ic_apple
                )
                AuthProvider.EMAIL -> LoginUi(
                    backgroundColor = Color.White,
                    iconRes = Res.drawable.ic_email
                )
            }
        }
    }
}
