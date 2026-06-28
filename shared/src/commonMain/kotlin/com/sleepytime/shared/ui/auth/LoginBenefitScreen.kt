package com.sleepytime.shared.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.ui.component.AuthMethod
import com.sleepytime.shared.ui.component.LoginButton
import com.sleepytime.shared.ui.component.LoginButtonType
import com.sleepytime.shared.ui.theme.SleepAppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun LoginBenefitContent(
    onSocialLogin: (AuthProvider) -> Unit,
    onEmailLogin: () -> Unit,
    onLater: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "수면 기록이 사라질 수 있어요",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "로그인하면 안전하게 저장됩니다",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )

        Spacer(Modifier.height(48.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoginButton(
                method = AuthMethod.Member(
                    AuthProvider.KAKAO
                ),
                type = LoginButtonType.FullWidth,
                onClick = { onSocialLogin(AuthProvider.KAKAO) }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                LoginButton(
                    method = AuthMethod.Member(
                        AuthProvider.GOOGLE
                    ),
                    type = LoginButtonType.Circle,
                    onClick = { onSocialLogin(AuthProvider.GOOGLE) }
                )

                Spacer(Modifier.width(12.dp))

                LoginButton(
                    method = AuthMethod.Member(
                        AuthProvider.APPLE
                    ),
                    type = LoginButtonType.Circle,
                    onClick = { onSocialLogin(AuthProvider.APPLE) }
                )

                Spacer(Modifier.width(12.dp))

                LoginButton(
                    method = AuthMethod.Member(
                        AuthProvider.EMAIL
                    ),
                    type = LoginButtonType.Circle,
                    onClick = onEmailLogin
                )
            }

            Spacer(Modifier.height(32.dp))

            TextButton(onClick = onLater) {
                Text(
                    text = "나중에 할게요",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Preview
@Composable
fun LoginBenefitScreenPreview() {
    SleepAppTheme {
        LoginBenefitContent(
            onSocialLogin = {},
            onEmailLogin = {},
            onLater = {}
        )
    }
}
