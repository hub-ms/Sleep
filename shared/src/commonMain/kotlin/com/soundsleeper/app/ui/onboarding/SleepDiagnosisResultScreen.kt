package com.soundsleeper.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.diagnosis_result_cta
import com.soundsleeper.app.resources.diagnosis_result_description
import com.soundsleeper.app.resources.diagnosis_result_title
import com.soundsleeper.app.ui.theme.sectionTitle
import org.jetbrains.compose.resources.stringResource

/**
 * 설문이 끝난 뒤, 로그인 전 거치는 간단한 전환 화면.
 *
 * 아직 로그인·실측 데이터가 없는 온보딩 단계라 실제 수면 분석은 불가능하다. 그래서 설문
 * 답변을 분석한 것처럼 보이는 가짜 결과를 보여주지 않고, 기대감만 조성한 뒤 로그인으로
 * 넘긴다 — 실제 리포트는 가입하고 측정을 시작해야 볼 수 있다.
 */
@Composable
fun SleepDiagnosisResultContent(
    onContinue: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingBackground()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.systemBars)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.diagnosis_result_title),
                style = MaterialTheme.typography.sectionTitle,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.diagnosis_result_description),
                style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Paragraph),
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter)
        ) {
            OnboardingButton(
                text = stringResource(Res.string.diagnosis_result_cta),
                onClick = onContinue
            )
        }
    }
}
