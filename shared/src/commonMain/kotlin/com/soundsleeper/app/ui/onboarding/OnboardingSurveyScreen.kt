package com.soundsleeper.app.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.common_next
import com.soundsleeper.app.resources.ic_caret_left
import com.soundsleeper.app.resources.survey_done
import com.soundsleeper.app.resources.survey_opt_6_7h
import com.soundsleeper.app.resources.survey_opt_7_8h
import com.soundsleeper.app.resources.survey_opt_deeper_sleep
import com.soundsleeper.app.resources.survey_opt_faster_sleep_onset
import com.soundsleeper.app.resources.survey_opt_frequent_waking
import com.soundsleeper.app.resources.survey_opt_over_8h
import com.soundsleeper.app.resources.survey_opt_reduce_snoring_goal
import com.soundsleeper.app.resources.survey_opt_refreshed_waking
import com.soundsleeper.app.resources.survey_opt_regular_pattern
import com.soundsleeper.app.resources.survey_opt_sleep_latency
import com.soundsleeper.app.resources.survey_opt_snoring
import com.soundsleeper.app.resources.survey_opt_tired_on_waking
import com.soundsleeper.app.resources.survey_opt_under_6h
import com.soundsleeper.app.resources.survey_previous_question
import com.soundsleeper.app.resources.survey_q1_description
import com.soundsleeper.app.resources.survey_q1_title
import com.soundsleeper.app.resources.survey_q2_description
import com.soundsleeper.app.resources.survey_q2_title
import com.soundsleeper.app.resources.survey_q3_description
import com.soundsleeper.app.resources.survey_q3_title
import com.soundsleeper.app.ui.component.SelectableCard
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.caption
import com.soundsleeper.app.ui.theme.sectionTitle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch

/**
 * 온보딩 설문 한 문항.
 *
 * 답은 추천 로직이 아직 쓰지 않는다. 지금은 가입 전에 사용자가 자기 문제를 한 번 말하게 해
 * 이어지는 화면들이 남 얘기처럼 느껴지지 않게 하는 것이 목적이고, 저장해 두면 나중에
 * 리포트·알람 기본값을 맞추는 데 쓸 수 있다.
 */
/** 선택지 한 칸. [key] 는 저장·비교에 쓰는 안정적인 식별자이고, [labelRes] 는 화면에 뭐라고
 * 적을지다. 예전에는 화면에 그려지는 한국어 문장 그 자체가 저장값이자 비교값이었다 —
 * 언어가 바뀌면 이미 고른 답이 더는 "같은 답"으로 인식되지 않는, HomeTab 과 같은 종류의
 * 버그였다. */
data class SurveyOption(
    val key: String,
    val labelRes: StringResource,
)

data class SurveyQuestion(
    val key: String,
    val titleRes: StringResource,
    val descriptionRes: StringResource,
    val options: List<SurveyOption>,
    /** 여러 개를 고를 수 있는 문항인지. 불편한 점은 보통 하나가 아니다. */
    val allowMultiple: Boolean = false,
    /** 다음으로 넘어가는 데 필요한 최소 선택 수. */
    val minSelection: Int = 1,
)

val onboardingSurveyQuestions = listOf(
    SurveyQuestion(
        key = "sleep_concern",
        titleRes = Res.string.survey_q1_title,
        // 불편한 점은 보통 한 가지가 아니다. 하나만 고르게 하면 나머지는 없는 일이 된다.
        descriptionRes = Res.string.survey_q1_description,
        options = listOf(
            SurveyOption("sleep_latency", Res.string.survey_opt_sleep_latency),
            SurveyOption("frequent_waking", Res.string.survey_opt_frequent_waking),
            SurveyOption("tired_on_waking", Res.string.survey_opt_tired_on_waking),
            SurveyOption("snoring", Res.string.survey_opt_snoring),
        ),
        allowMultiple = true,
    ),
    SurveyQuestion(
        key = "sleep_goal",
        titleRes = Res.string.survey_q2_title,
        descriptionRes = Res.string.survey_q2_description,
        options = listOf(
            SurveyOption("under_6h", Res.string.survey_opt_under_6h),
            SurveyOption("6_7h", Res.string.survey_opt_6_7h),
            SurveyOption("7_8h", Res.string.survey_opt_7_8h),
            SurveyOption("over_8h", Res.string.survey_opt_over_8h),
        ),
    ),
    // 3번째 문항. 앞의 두 문항은 "지금 겪는 문제"를 말하고, 이 문항은 "앞으로 바라는 결과"를
    // 말한다 — 이후 결과 화면·리포트에서 이 답을 중심으로 보여줄 자리를 남겨 둔다.
    SurveyQuestion(
        key = "desired_change",
        titleRes = Res.string.survey_q3_title,
        descriptionRes = Res.string.survey_q3_description,
        options = listOf(
            SurveyOption("deeper_sleep", Res.string.survey_opt_deeper_sleep),
            SurveyOption("refreshed_waking", Res.string.survey_opt_refreshed_waking),
            SurveyOption("faster_sleep_onset", Res.string.survey_opt_faster_sleep_onset),
            SurveyOption("regular_pattern", Res.string.survey_opt_regular_pattern),
            SurveyOption("reduce_snoring", Res.string.survey_opt_reduce_snoring_goal),
        ),
    ),
)

/**
 * 설문 3페이지.
 *
 * 건너뛰기는 이제 이 화면에 없다 — 온보딩 가치 제안 화면의 건너뛰기가 설문까지 함께
 * 건너뛰므로, 여기까지 들어온 사용자에게는 뒤로 가기만 있으면 된다.
 */
@Composable
fun OnboardingSurveyContent(
    onFinish: (Map<String, Set<String>>) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { onboardingSurveyQuestions.size })
    val scope = rememberCoroutineScope()
    // 문항마다 답이 여러 개일 수 있으므로 String 하나가 아니라 Set 을 담는다.
    val answers = remember { mutableStateMapOf<String, Set<String>>() }
    val currentQuestion = onboardingSurveyQuestions[pagerState.currentPage]
    val currentAnswers = answers[currentQuestion.key].orEmpty()
    val canProceed = currentAnswers.size >= currentQuestion.minSelection

    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingBackground()
        // 가치 제안 화면과 같은 어두운 스크림. 설문도 그 화면과 이어지는 하나의 어두운
        // 플로우로 보이게 한다 — 시스템이 라이트 모드여도 배경이 밝아지지 않는다.
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
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 탭하면 바로 다음 장으로 넘어가던 때는 되돌아갈 방법이 아예 없었다.
                // 이제 다음 버튼으로 넘기므로 앞 문항을 고쳐 볼 수 있어야 한다.
                if (pagerState.currentPage > 0) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_caret_left),
                        contentDescription = stringResource(Res.string.survey_previous_question),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }
                            .padding(end = 12.dp)
                            .size(20.dp)
                    )
                }
                SurveyProgress(
                    currentPage = pagerState.currentPage,
                    pageCount = onboardingSurveyQuestions.size,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                // 아래 다음 버튼으로만 넘어간다. 스와이프로 건너뛰면 조건을 우회하게 된다.
                userScrollEnabled = false
            ) { page ->
                val question = onboardingSurveyQuestions[page]
                SurveyQuestionPage(
                    question = question,
                    selected = answers[question.key].orEmpty(),
                    onSelect = { option ->
                        val current = answers[question.key].orEmpty()
                        answers[question.key] = when {
                            // 다중 선택은 토글. 잘못 누른 것을 되돌릴 수 없으면 "모두 고르세요"가
                            // 함정이 된다.
                            !question.allowMultiple -> setOf(option)
                            option in current -> current - option
                            else -> current + option
                        }
                    }
                )
            }

            // 예전에는 답을 고르는 순간 다음 장으로 넘어갔다. 그래서 여러 개를 고를 수 없었고,
            // 잘못 눌러도 되돌릴 수 없었다. 넘기는 일은 이 버튼 하나가 맡는다.
            OnboardingButton(
                text = if (pagerState.currentPage == onboardingSurveyQuestions.lastIndex) stringResource(Res.string.survey_done)
                else stringResource(Res.string.common_next),
                enabled = canProceed,
                onClick = {
                    if (pagerState.currentPage == onboardingSurveyQuestions.lastIndex) {
                        onFinish(answers.toMap())
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                }
            )
        }
    }
}

/** 트랙을 따라 진행률만큼 색이 채워지는 바. 문항이 몇 개 남았는지보다 "얼마나 왔는지"가
 * 더 잘 읽히도록 점/필 방식에서 바꿨다. */
@Composable
private fun SurveyProgress(currentPage: Int, pageCount: Int, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(targetValue = (currentPage + 1) / pageCount.toFloat())
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(primary, RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun SurveyQuestionPage(
    question: SurveyQuestion,
    selected: Set<String>,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(question.titleRes),
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White
        )
        Text(
            text = stringResource(question.descriptionRes),
            style = MaterialTheme.typography.caption,
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(12.dp))
        question.options.forEach { option ->
            SelectableCard(
                text = stringResource(option.labelRes),
                isSelected = option.key in selected,
                onClick = { onSelect(option.key) },
            )
        }
    }
}
