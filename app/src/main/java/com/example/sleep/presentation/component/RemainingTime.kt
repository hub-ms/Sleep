import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun RemainingTime(
    hour: Int,
    minute: Int
) {
    Text(
        text = String.format(Locale.getDefault(), "수면 측정 중..."),
        color = Color.White,
        textAlign = TextAlign.Center,
        fontSize = 12.sp,
    )
    Text(
        text = String.format(
            Locale.getDefault(),
            "남은 수면 시간 ${hour}시간 ${minute}분",
        ),
        color = Color.White,
        textAlign = TextAlign.Center,
        fontSize = 24.sp,
    )
}