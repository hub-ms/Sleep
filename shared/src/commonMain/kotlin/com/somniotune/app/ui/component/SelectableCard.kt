package com.somniotune.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.somniotune.app.resources.Res
import com.somniotune.app.resources.ic_check
import com.somniotune.app.ui.theme.IconSize
import com.somniotune.app.ui.theme.SleepTheme.primary
import com.somniotune.app.ui.theme.bodyText
import org.jetbrains.compose.resources.painterResource

/**
 * 하나를 고르는 카드. 설문 선택지와 언어 설정에서 쓴다.
 *
 * 예전에는 선택 여부에 따라 면 색이 primary 40% ↔ LightGray 40% 로 함께 바뀌어,
 * 고르는 순간 카드 전체가 다른 색으로 변했다. 선택 표시는 테두리 하나로 충분하고,
 * 면 색까지 바뀌면 목록 안에서 어느 것이 선택됐는지보다 색 변화가 먼저 눈에 들어온다.
 * 면 색은 고정하고 테두리 색만 바꾼다.
 */
@Composable
fun SelectableCard(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton),
        shape = RoundedCornerShape(16.dp),
        // 선택 여부와 무관하게 고정한다.
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(
            width = 2.dp,
            color = if (isSelected) primary else Color.White.copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyText,
                color = Color.White
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isSelected) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_check),
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(IconSize.Default)
                    )
                }
            }
        }
    }
}
