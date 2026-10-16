package com.soundsleeper.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.domain.model.SleepAnimalType
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.animal_locked_hint
import com.soundsleeper.app.resources.animal_reveal_eyebrow
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.bodyText
import com.soundsleeper.app.ui.theme.caption
import com.soundsleeper.app.ui.theme.sectionTitle
import com.soundsleeper.app.util.ResourceMapper
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 설문 결과로 나온 수면 동물 유형을 보여주는 카드.
 *
 * 두 화면이 같은 카드를 쓴다. 가입 화면에서는 유형 이름만 보여주고 "자세한 건 가입해야
 * 볼 수 있다"고 말해 가입을 유도하고([showSummary] = false), 가입 직전 이메일 화면에서는
 * 이미 가입할 마음을 먹은 사람이므로 요약 한 줄까지 보여준다([showSummary] = true).
 * 두 화면에서 같은 그림·같은 이름이 보여야 "내 카드"라는 감각이 이어진다.
 *
 * 캐릭터 아트는 아직 없다. [ResourceMapper.getAnimalImageRes] 가 폴백 아이콘을 돌려주며,
 * `drawable/animal_*.webp` 가 추가되면 이 화면은 손대지 않아도 그림만 바뀐다.
 */
@Composable
fun AnimalRevealCard(
    animal: SleepAnimalType,
    modifier: Modifier = Modifier,
    showSummary: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, primary.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.animal_reveal_eyebrow),
                style = MaterialTheme.typography.caption,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    // 아트가 들어오기 전 폴백은 24dp 아이콘이라 비율을 고정해 두지 않으면
                    // 카드 높이가 아트 유무에 따라 크게 달라진다.
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                // 폴백 아이콘은 자기 색(검정)을 들고 있어 어두운 배경에서 보이지 않는다.
                // 진짜 아트가 들어오면 덧칠을 멈춰야 하므로 ResourceMapper 에게 묻는다.
                val hasArt = ResourceMapper.hasAnimalArt(animal.imageName)
                Image(
                    painter = painterResource(ResourceMapper.getAnimalImageRes(animal.imageName)),
                    contentDescription = stringResource(animal.titleRes),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (hasArt) null else ColorFilter.tint(primary),
                )
            }
            Text(
                text = stringResource(animal.titleRes),
                style = MaterialTheme.typography.sectionTitle,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            if (showSummary) {
                Text(
                    text = stringResource(animal.summaryRes),
                    style = MaterialTheme.typography.bodyText.copy(lineBreak = LineBreak.Paragraph),
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = stringResource(Res.string.animal_locked_hint),
                    style = MaterialTheme.typography.caption,
                    color = primary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
