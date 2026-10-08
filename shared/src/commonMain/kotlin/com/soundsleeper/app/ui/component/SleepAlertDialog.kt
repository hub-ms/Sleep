package com.soundsleeper.app.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.SurfaceDark
import com.soundsleeper.app.ui.theme.bodyText
import com.soundsleeper.app.ui.theme.caption

private val DIALOG_SHAPE = RoundedCornerShape(24.dp)

/**
 * 앱 공통 확인 대화상자.
 *
 * 측정 종료와 짧은 수면 경고가 같은 모양을 각자 복사해 두고 있었다. 둘 다
 * `modifier.background(gradients.surface, RoundedCornerShape(16.dp))` 에 `shape = 24.dp` 를
 * 함께 줘서 바탕과 테두리의 반경이 어긋났고, 그래디언트 끝이 40% 알파라 측정 화면의 검은
 * 배경 위에서는 대화상자 아래쪽이 배경에 녹아 경계가 사라졌다. 불투명 면 + 얇은 테두리로
 * 바꾸고 반경을 하나로 맞춘다.
 *
 * [isDestructive] 가 true 면 확인 버튼을 error 색으로 칠한다(기록을 버리는 선택).
 */
@Composable
fun SleepAlertDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false,
) {
    AlertDialog(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = primary.copy(alpha = 0.2f),
                shape = DIALOG_SHAPE
            ),
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = DIALOG_SHAPE,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyText,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyText,
                color = Color.White
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    style = MaterialTheme.typography.caption,
                    color = if (isDestructive) MaterialTheme.colorScheme.error
                    else primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissText,
                    style = MaterialTheme.typography.caption,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}
