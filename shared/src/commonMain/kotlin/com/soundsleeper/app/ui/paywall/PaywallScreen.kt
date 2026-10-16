package com.soundsleeper.app.ui.paywall

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.AppConfig
import com.soundsleeper.app.domain.model.BillingProductDetails
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.paywall_feature_ai_advice
import com.soundsleeper.app.resources.common_back
import com.soundsleeper.app.resources.common_close
import com.soundsleeper.app.resources.common_privacy_policy
import com.soundsleeper.app.resources.common_terms_of_service
import com.soundsleeper.app.resources.ic_check
import com.soundsleeper.app.resources.bg_splash
import com.soundsleeper.app.resources.common_background_image_description
import com.soundsleeper.app.resources.ic_close
import com.soundsleeper.app.resources.paywall_badge_recommended
import com.soundsleeper.app.resources.paywall_cta_start_trial_7day
import com.soundsleeper.app.resources.paywall_cta_subscribe
import com.soundsleeper.app.resources.paywall_cycle_monthly
import com.soundsleeper.app.resources.paywall_cycle_yearly
import com.soundsleeper.app.resources.paywall_header_title
import com.soundsleeper.app.resources.paywall_plan_monthly
import com.soundsleeper.app.resources.paywall_plan_yearly
import com.soundsleeper.app.resources.paywall_renewal_notice_standard
import com.soundsleeper.app.resources.paywall_renewal_notice_trial
import com.soundsleeper.app.resources.paywall_review_1
import com.soundsleeper.app.resources.paywall_review_2
import com.soundsleeper.app.resources.paywall_review_3
import com.soundsleeper.app.resources.paywall_review_4
import com.soundsleeper.app.resources.paywall_review_5
import com.soundsleeper.app.resources.paywall_review_6
import com.soundsleeper.app.resources.paywall_reviews_title
import com.soundsleeper.app.resources.paywall_placeholder_badge
import com.soundsleeper.app.resources.paywall_price_suffix_monthly
import com.soundsleeper.app.resources.paywall_price_suffix_yearly
import com.soundsleeper.app.resources.paywall_stat_rank_label
import com.soundsleeper.app.resources.paywall_stat_rank_value
import com.soundsleeper.app.resources.paywall_stat_users_label
import com.soundsleeper.app.resources.paywall_stat_users_value
import com.soundsleeper.app.resources.paywall_savings_percent
import com.soundsleeper.app.resources.paywall_feature_premium_music
import com.soundsleeper.app.resources.paywall_feature_self_comparison
import com.soundsleeper.app.resources.paywall_feature_snoring
import com.soundsleeper.app.ui.theme.IconSize
import com.soundsleeper.app.ui.theme.SleepTheme
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.bodyHighlight
import com.soundsleeper.app.ui.theme.bodyText
import com.soundsleeper.app.ui.theme.caption
import com.soundsleeper.app.util.BillingPeriod
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 플랜 이름은 **결제 주기**로 고른다.
 *
 * 예전에는 productId 로 월간/연간을 갈랐는데, Play 가 내려주는 productId 는 구독 상품
 * ID(`dive_premium`) 하나뿐이라 두 분기 모두 빗나가고 `else -> productId` 가 걸려
 * 화면에 `dive_premium` 이 그대로 찍혔다. 주기는 Play Console 의 base plan ID 철자에
 * 의존하지 않아 더 안전하기도 하다.
 */
@Composable
private fun planLabel(billingPeriodIso: String): String = when (billingPeriodIso.uppercase()) {
    "P1M" -> stringResource(Res.string.paywall_plan_monthly)
    "P1Y" -> stringResource(Res.string.paywall_plan_yearly)
    else -> billingCycleLabel(billingPeriodIso)
}

/**
 * 가격 옆에 붙는 접미사(`/년`, `/월`).
 *
 * 예전에는 플랜 이름 아래에 "1년" / "1달" 이 따로 한 줄 있었다. 가격과 떨어져 있으니
 * 9,900원이 한 달 값인지 1년 값인지 두 군데를 번갈아 봐야 했다. 가격 바로 옆에 붙인다.
 */
@Composable
private fun pricePeriodSuffix(iso: String): String? = when (iso.uppercase()) {
    "P1M" -> stringResource(Res.string.paywall_price_suffix_monthly)
    "P1Y" -> stringResource(Res.string.paywall_price_suffix_yearly)
    else -> null
}

@Composable
private fun billingCycleLabel(iso: String): String = when (iso.uppercase()) {
    "P1M" -> stringResource(Res.string.paywall_cycle_monthly)
    "P1Y" -> stringResource(Res.string.paywall_cycle_yearly)
    else -> "-"
}
@Composable
fun PaywallContent(
    state: PaywallContract.State,
    onSelectPlan: (String) -> Unit,
    onSubscribeClicked: () -> Unit,
    onDismissError: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onBackClick: () -> Unit,
) {
    val selectedProduct = state.products.find { it.planId == state.selectedPlanId }
    // 연간이 월간 대비 몇 % 싼지. 두 주기가 모두 내려왔을 때만 계산된다.
    val savingsPercent = rememberYearlySavingsPercent(state.products)

    Box(modifier = Modifier.fillMaxSize().background(SleepTheme.background)) {
        PaywallBackground()

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 스크롤되는 내용이 상태바 밑으로 지나가면 시계·아이콘과 글자가 겹친다.
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.paywall_header_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(
                modifier = Modifier.align(Alignment.TopEnd),
                onClick = onBackClick
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.common_back),
                    tint = Color.White,
                    modifier = Modifier.size(IconSize.Default)
                )
            }
        }
        // 플랜 카드와 비교표는 이 안에 둔다. 예전에는 루트가 스크롤되지 않아서 큰 시스템
        // 폰트나 영어 문자열에서 아래 내용이 잘려 보이지도 않고 닿을 수도 없었다.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 가짜 수치·후기는 디버그 빌드에서만 그린다. 실제로 없는 순위나 사용자 수를
            // 스토어에 올라가는 빌드에서 표기하면 Play 정책 위반 소지가 있고, 지어낸 문장을
            // 후기처럼 보여주는 것은 기만이다. 실제 데이터로 교체할 때 이 가드를 걷어낸다.
            val showPlaceholderProof = koinInject<AppConfig>().isDebug
            if (showPlaceholderProof) SocialProofCard()
            PlanComparison()
            if (state.products.isEmpty() && state.isLoading) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(72.dp),
                            shape = RoundedCornerShape(16.dp),
                            Color.White.copy(alpha = 0.05f)
                        ) {}
                    }
                }
            } else {
                // 세로로 쌓으면 카드 하나가 화면 폭을 다 쓴다. 가로로 나란히 놓던 시절의
                // IntrinsicSize.Min 높이 맞춤은 필요 없어졌다.
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.products.forEach { product ->
                        val isYearly = product.billingPeriodIso8601.equals("P1Y", ignoreCase = true)
                        PlanCard(
                            modifier = Modifier.fillMaxWidth(),
                            product = product,
                            isSelected = product.planId == state.selectedPlanId,
                            isRecommended = isYearly,
                            savingsPercent = savingsPercent.takeIf { isYearly },
                            onClick = { onSelectPlan(product.planId) }
                        )
                    }
                }
            }
            // 후기는 플랜 카드 **아래**에 둔다. 가격을 본 직후가 "남들도 쓰나?"를 확인하고
            // 싶은 순간이라, 가격보다 위에 있으면 그냥 지나친다.
            if (showPlaceholderProof) ReviewCarousel()
        }

        state.errorMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissError, modifier = Modifier.size(32.dp)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.common_close),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Button(
            onClick = onSubscribeClicked,
            enabled = !state.isPurchasing && !state.isPremium,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primary)
        ) {
            if (state.isPurchasing) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                // 체험이 붙은 상품일 때만 체험 문구를 띄운다. Play 가 체험 offer 를 주지
                // 않는 경우(이미 써버린 재구독자 등)에는 "구독 시작하기"로 떨어져야 거짓
                // 약속이 되지 않는다. 실제 체험 기간은 바로 아래 고지문이 Play 가 내려준
                // 값으로 적으므로, 버튼의 "7일"과 어긋나지 않도록 Play Console 의 체험
                // 기간을 7일로 맞춰 두어야 한다.
                //
                // 연간을 고른 사람에게만 체험 문구를 띄운다. 다만 `hasTrial` 조건은 그대로
                // 둔다 - Play 가 체험 offer 를 주지 않는 경우(이미 써버린 재구독자 등)에
                // "7일 무료체험"을 띄우면 거짓 약속이 된다. 체험이 없는 연간은 평소 문구다.
                val hasTrial = selectedProduct
                    ?.let { BillingPeriod.toLocalizedLabel(it.freeTrialPeriodIso8601) } != null
                val isYearlySelected =
                    selectedProduct?.billingPeriodIso8601.equals("P1Y", ignoreCase = true)
                Text(
                    text = when {
                        isYearlySelected && hasTrial ->
                            stringResource(Res.string.paywall_cta_start_trial_7day)
                        else -> stringResource(Res.string.paywall_cta_subscribe)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
        val trialLabel = selectedProduct
            ?.let { BillingPeriod.toLocalizedLabel(it.freeTrialPeriodIso8601) }
        val billingCycleText = selectedProduct?.let { billingCycleLabel(it.billingPeriodIso8601) }
        selectedProduct?.let { product ->
            Text(
                text = when {
                    trialLabel != null -> stringResource(
                        Res.string.paywall_renewal_notice_trial,
                        trialLabel,
                        product.formattedPrice,
                        billingCycleText ?: "",
                    )

                    else -> stringResource(
                        Res.string.paywall_renewal_notice_standard,
                        product.formattedPrice,
                        billingCycleText ?: "",
                    )
                },
                style = MaterialTheme.typography.caption,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
        ) {
            Text(
                text = stringResource(Res.string.common_terms_of_service),
                style = MaterialTheme.typography.bodyText,
                textDecoration = TextDecoration.Underline,
                color = Color.White,
                modifier = Modifier
                    .clickable { onNavigateToTerms() }
                    .padding(vertical = 4.dp, horizontal = 4.dp)
            )
            Text(
                text = stringResource(Res.string.common_privacy_policy),
                style = MaterialTheme.typography.bodyText,
                textDecoration = TextDecoration.Underline,
                color = Color.White,
                modifier = Modifier
                    .clickable { onNavigateToPrivacy() }
                    .padding(vertical = 4.dp, horizontal = 4.dp)
            )
        }
    }
    }
}

/**
 * 페이월 배경.
 *
 * 레포에 있는 전면 배경 에셋은 `bg_splash` 하나뿐이라 스플래시·온보딩과 같은 그림을 쓴다
 * (OnboardingBackground 와 같은 Crop 패턴). 사진 위에 흰 글씨를 얹으면 밝은 영역에서
 * 읽히지 않으므로, CurrentMusicCard 가 쓰는 세로 그라디언트 스크림을 함께 깐다.
 */
@Composable
private fun PaywallBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(Res.drawable.bg_splash),
            contentDescription = stringResource(Res.string.common_background_image_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.85f),
                        )
                    )
                )
        )
    }
}

/**
 * 연간이 월간보다 몇 % 싼지 계산한다. 두 주기가 모두 내려왔을 때만 값이 나온다.
 *
 * 이 계산은 예전에 있다가 화면 재작성(`b29cdce`) 때 유실됐다. 가격은 Play 가 로캘별로
 * 내려주므로 formattedPrice 가 아니라 priceAmountMicros 로 견줘야 한다.
 */
@Composable
private fun rememberYearlySavingsPercent(products: List<BillingProductDetails>): Int? =
    remember(products) {
        val monthly = products.firstOrNull { it.billingPeriodIso8601.equals("P1M", true) }
        val yearly = products.firstOrNull { it.billingPeriodIso8601.equals("P1Y", true) }
        if (monthly == null || yearly == null) null
        else BillingPeriod.savingsPercent(
            monthlyMicrosPerMonth = monthly.priceAmountMicros,
            yearlyMicrosPerYear = yearly.priceAmountMicros,
        )
    }

/**
 * 다운로드 순위·사용자 수 카드.
 *
 * ⚠ **지금 값은 전부 지어낸 것이다.** 호출부가 `isDebug` 로 감싸고 있어 릴리스 빌드에는
 * 그려지지 않는다. 실제로 없는 순위나 사용자 수를 스토어에 올라가는 빌드에서 표기하면
 * Play 정책 위반 소지가 있다. 실제 수치가 생기면 문자열을 바꾸고 호출부의 가드를 없앤다.
 */
@Composable
private fun SocialProofCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            PlaceholderBadge(modifier = Modifier.padding(start = 12.dp, bottom = 10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    modifier = Modifier.weight(1f),
                    value = stringResource(Res.string.paywall_stat_rank_value),
                    label = stringResource(Res.string.paywall_stat_rank_label),
                )
                StatCell(
                    modifier = Modifier.weight(1f),
                    value = stringResource(Res.string.paywall_stat_users_value),
                    label = stringResource(Res.string.paywall_stat_users_label),
                )
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

/** 이 영역이 실제 데이터가 아님을 화면에서도 알린다 - 디버그 빌드에서만 보인다. */
@Composable
private fun PlaceholderBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            text = stringResource(Res.string.paywall_placeholder_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * 사용자 후기 가로 스크롤.
 *
 * ⚠ **지금 문구는 전부 지어낸 것이다.** 호출부가 `isDebug` 로 감싸고 있어 릴리스 빌드에는
 * 그려지지 않는다. 지어낸 문장을 후기처럼 보여주는 것은 기만이고 스토어 심사 대상이다.
 * 플레이스토어 실제 리뷰를 strings.xml 에 채운 뒤 호출부의 가드를 없앤다.
 *
 * 빈 항목을 건너뛰는 필터는 2차 안전장치로 남겨 둔다 - 문자열을 다시 비우는 것만으로도
 * 이 영역이 사라진다.
 */
@Composable
private fun ReviewCarousel() {
    val reviews = listOf(
        stringResource(Res.string.paywall_review_1),
        stringResource(Res.string.paywall_review_2),
        stringResource(Res.string.paywall_review_3),
        stringResource(Res.string.paywall_review_4),
        stringResource(Res.string.paywall_review_5),
        stringResource(Res.string.paywall_review_6),
    ).filter { it.isNotBlank() }

    if (reviews.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.paywall_reviews_title),
                style = MaterialTheme.typography.bodyHighlight,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            PlaceholderBadge()
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(reviews) { review ->
                Surface(
                    modifier = Modifier.width(240.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.08f),
                ) {
                    Text(
                        text = review,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyText,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * 플랜 카드 한 장.
 *
 * 월간/연간을 **세로로** 쌓기 때문에 카드 하나가 화면 폭을 다 쓴다. 그래서 내용은 좌(플랜명·
 * 주기)/우(가격)로 가르고, 연간에만 붙는 할인율 뱃지는 카드 밖 왼쪽 위에 겹쳐 놓는다.
 * 카드를 가로로 나란히 놓던 시절에는 폭이 절반뿐이라 위아래로 쌓아야 했다.
 */
@Composable
private fun PlanCard(
    product: BillingProductDetails,
    isSelected: Boolean,
    isRecommended: Boolean,
    savingsPercent: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // 뱃지가 카드 위로 걸쳐 나오므로 그만큼 위를 비워 둔다.
                .padding(top = 8.dp)
                .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) primary else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            color = if (isSelected) primary.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.05f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = planLabel(product.billingPeriodIso8601),
                            style = MaterialTheme.typography.bodyText,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        if (isRecommended) {
                            Surface(shape = RoundedCornerShape(6.dp), color = primary) {
                                Text(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    text = stringResource(Res.string.paywall_badge_recommended),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = product.formattedPrice,
                        style = MaterialTheme.typography.bodyHighlight,
                        color = primary,
                        fontWeight = FontWeight.Bold
                    )
                    pricePeriodSuffix(product.billingPeriodIso8601)?.let { suffix ->
                        Text(
                            text = suffix,
                            style = MaterialTheme.typography.caption,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        if (savingsPercent != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.error,
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    text = stringResource(Res.string.paywall_savings_percent, savingsPercent),
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

data class PremiumFeatures(
    val icon: DrawableResource,
    val label: StringResource,
)

private val premiumFeatures = listOf(
    PremiumFeatures(Res.drawable.ic_check, Res.string.paywall_feature_premium_music),
    PremiumFeatures(Res.drawable.ic_check, Res.string.paywall_feature_snoring),
    PremiumFeatures(Res.drawable.ic_check, Res.string.paywall_feature_ai_advice),
    PremiumFeatures(Res.drawable.ic_check, Res.string.paywall_feature_self_comparison),
)

@Composable
private fun PlanComparison() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        Color.White.copy(alpha = 0.05f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            premiumFeatures.forEach { feature ->
                PremiumFeatureRow(
                    icon = painterResource(feature.icon),
                    label = stringResource(feature.label),
                )
            }
        }
    }
}

@Composable
private fun PremiumFeatureRow(
    icon: Painter,
    label: String,
) {
    // SpaceBetween 이었을 때는 체크 아이콘이 왼쪽 끝, 글자가 오른쪽 끝으로 찢어져
    // 둘이 한 항목으로 읽히지 않았다(에뮬레이터에서 확인). 붙여 놓는다.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(IconSize.Inline)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyHighlight,
            color = Color.White
        )
    }
}
