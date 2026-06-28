package com.sleepytime.shared.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_caret_right
import com.sleepytime.shared.resources.ic_email
import com.sleepytime.shared.ui.theme.SleepAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun EmailManageContent(
    userType: User.AuthInfo,
    isEmailConnected: Boolean,
    onConnectClicked: () -> Unit,
    onDisconnectClicked: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
            .padding(16.dp)
    ) {
        Text(
            text = "이메일 관리",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_email),
                        contentDescription = null,
                        tint = Color.Unspecified
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "이메일 계정",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = if (isEmailConnected) "연결된 이메일이 있어요" else "이메일이 연결되지 않았어요",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    if (isEmailConnected) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFEFE5FF)
                        ) {
                            Text(
                                text = "연결됨",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = Color(0xFF7B4DFF),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "관리",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column {
                EmailManageItem(
                    title = "이메일 연결 상태",
                    subtitle = if (isEmailConnected) "현재 이메일이 연결되어 있습니다" else "연결된 이메일이 없습니다",
                    onClick = { }
                )

                HorizontalDivider(color = Color(0xFFEAEAEA), thickness = 1.dp)

                EmailManageItem(
                    title = "이메일 변경",
                    subtitle = "다른 이메일로 바꿀 수 있어요",
                    onClick = onConnectClicked
                )

                HorizontalDivider(color = Color(0xFFEAEAEA), thickness = 1.dp)

                EmailManageItem(
                    title = "이메일 연결 해제",
                    subtitle = "연결을 해제하고 싶을 때 사용해요",
                    isDestructive = true,
                    onClick = onDisconnectClicked
                )
            }
        }
    }
}

@Composable
fun EmailManageItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isDestructive) Color.Red else Color.Black
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        Icon(
            painter = painterResource(Res.drawable.ic_caret_right),
            contentDescription = null,
            tint = if (isDestructive) Color.Red else Color.Gray
        )
    }
}

@Preview
@Composable
fun EmailManageScreenPreview() {
    SleepAppTheme {
        EmailManageContent(
            userType = User.AuthInfo.Member(
                memberEmail = "test@example.com",
                id = "test",
                authProvider = AuthProvider.EMAIL
            ),
            isEmailConnected = true,
            onConnectClicked = {},
            onDisconnectClicked = {},
            onBack = {}
        )
    }
}
