package com.soundsleeper.app.ui.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.util.BillingPeriod
import com.soundsleeper.app.domain.model.BillingProductDetails
import com.soundsleeper.app.domain.model.BillingProducts
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.common_back
import com.soundsleeper.app.resources.common_close
import com.soundsleeper.app.resources.common_privacy_policy
import com.soundsleeper.app.resources.common_terms_of_service
import com.soundsleeper.app.resources.ic_check
import com.soundsleeper.app.resources.ic_close
import com.soundsleeper.app.resources.paywall_badge_recommended
import com.soundsleeper.app.resources.paywall_comparison_free_header
import com.soundsleeper.app.resources.paywall_comparison_premium_header
import com.soundsleeper.app.resources.paywall_comparison_title
import com.soundsleeper.app.resources.paywall_cta_already_premium
import com.soundsleeper.app.resources.paywall_cta_start_trial_7day
import com.soundsleeper.app.resources.paywall_cta_subscribe
import com.soundsleeper.app.resources.paywall_cycle_monthly
import com.soundsleeper.app.resources.paywall_cycle_quarterly
import com.soundsleeper.app.resources.paywall_cycle_semiannual
import com.soundsleeper.app.resources.paywall_cycle_weekly
import com.soundsleeper.app.resources.paywall_cycle_yearly
import com.soundsleeper.app.resources.paywall_feature_available_free
import com.soundsleeper.app.resources.paywall_feature_available_premium
import com.soundsleeper.app.resources.paywall_feature_basic_music
import com.soundsleeper.app.resources.paywall_feature_basic_report
import com.soundsleeper.app.resources.paywall_feature_premium_music
import com.soundsleeper.app.resources.paywall_feature_sleep_tracking
import com.soundsleeper.app.resources.paywall_feature_smart_alarm
import com.soundsleeper.app.resources.paywall_feature_unavailable_free
import com.soundsleeper.app.resources.paywall_header_subtitle
import com.soundsleeper.app.resources.paywall_header_title
import com.soundsleeper.app.resources.paywall_plan_monthly
import com.soundsleeper.app.resources.paywall_plan_yearly
import com.soundsleeper.app.resources.paywall_renewal_notice_default
import com.soundsleeper.app.resources.paywall_renewal_notice_standard
import com.soundsleeper.app.resources.paywall_renewal_notice_trial
import com.soundsleeper.app.resources.paywall_savings_monthly_equivalent
import com.soundsleeper.app.resources.report_ai_advice_title
import com.soundsleeper.app.resources.report_self_comparison_title
import com.soundsleeper.app.resources.report_snoring_analysis_title
import com.soundsleeper.app.ui.theme.SleepTheme
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.bodyText
import com.soundsleeper.app.ui.theme.caption
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
private fun planLabel(productId: String): String = when (productId) {
    BillingProducts.MONTHLY -> stringResource(Res.string.paywall_plan_monthly)
    BillingProducts.YEARLY -> stringResource(Res.string.paywall_plan_yearly)
    else -> productId
}

@Composable
private fun billingCycleLabel(iso: String): String = when (iso.uppercase()) {
    "P1W" -> stringResource(Res.string.paywall_cycle_weekly)
    "P1M" -> stringResource(Res.string.paywall_cycle_monthly)
    "P3M" -> stringResource(Res.string.paywall_cycle_quarterly)
    "P6M" -> stringResource(Res.string.paywall_cycle_semiannual)
    "P1Y" -> stringResource(Res.string.paywall_cycle_yearly)
    else -> "-"
}

/** 연간 플랜의 월 환산 절약률(%). 두 플랜 가격을 모두 조회하지 못했으면 null. */
private fun yearlySavingsPercent(products: List<BillingProductDetails>): Int? {
    val monthly = products.find { it.productId == BillingProducts.MONTHLY } ?: return null
    val yearly = products.find { it.productId == BillingProducts.YEARLY } ?: return null
    if (monthly.priceAmountMicros <= 0) return null

    val yearlyMonthlyEquivalent = yearly.priceAmountMicros / 12.0
    val percent = ((1.0 - yearlyMonthlyEquivalent / monthly.priceAmountMicros) * 100).roundToInt()
    return percent.takeIf { it > 0 }
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
    val selectedProduct = state.products.find { it.productId == state.selectedProductId }
    val savingsPercent = yearlySavingsPercent(state.products)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepTheme.background)
            // 스크롤되는 내용이 상태바 밑으로 지나가면 시계·아이콘과 글자가 겹친다.
            .statusBarsPadding()
            .navigationBarsPadding()
            // 스크롤 없이 한 화면에 모두 들어오도록 간격을 촘촘히 둔다.
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                modifier = Modifier.align(Alignment.TopEnd),
                onClick = onBackClick
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.common_back),
                    tint = Color.White
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.paywall_header_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = stringResource(Res.string.paywall_header_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }

        // 여기 있던 "프리미엄 수면 음악 / 상세 리포트" 아이콘 블록은 아래 비교표가 같은
        // 내용을 더 정확하게(무료에서도 되는지까지) 말하고 있어 같은 말을 두 번 하는
        // 셈이었다. 헤드라인에서 바로 가격으로 이어진다.

        // 월간/연간을 위아래가 아니라 나란히 놓는다. 두 가격이 한눈에 비교되고,
        // 아래 비교표와 좌우 위치도 맞는다.
        if (state.products.isEmpty() && state.isLoading) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) {
                    Surface(
                        modifier = Modifier.weight(1f).height(120.dp),
                        shape = RoundedCornerShape(16.dp),
                        Color.White.copy(alpha = 0.05f)
                    ) {}
                }
            }
        } else {
            // 연간 카드에만 배지와 절약률이 붙어 그대로 두면 두 카드 높이가 어긋난다.
            // IntrinsicSize.Min 으로 둘 중 큰 쪽에 높이를 맞춘다.
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.products.forEach { product ->
                    PlanCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        product = product,
                        isSelected = product.productId == state.selectedProductId,
                        isRecommended = product.productId == BillingProducts.YEARLY,
                        savingsPercent = if (product.productId == BillingProducts.YEARLY) savingsPercent else null,
                        onClick = { onSelectPlan(product.productId) }
                    )
                }
            }
        }

        PlanComparison()

        state.errorMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
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
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                // 체험이 붙은 상품일 때만 체험 문구를 띄운다. Play 가 체험 offer 를 주지
                // 않는 경우(이미 써버린 재구독자 등)에는 "구독 시작하기"로 떨어져야 거짓
                // 약속이 되지 않는다. 실제 체험 기간은 바로 아래 고지문이 Play 가 내려준
                // 값으로 적으므로, 버튼의 "7일"과 어긋나지 않도록 Play Console 의 체험
                // 기간을 7일로 맞춰 두어야 한다.
                val hasTrial = selectedProduct
                    ?.let { BillingPeriod.toLocalizedLabel(it.freeTrialPeriodIso8601) } != null
                Text(
                    text = when {
                        state.isPremium -> stringResource(Res.string.paywall_cta_already_premium)
                        hasTrial -> stringResource(Res.string.paywall_cta_start_trial_7day)
                        else -> stringResource(Res.string.paywall_cta_subscribe)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 무료체험이 붙은 상품이면 "언제부터 얼마가 빠져나가는지"를 분명히 적는다.
        // 체험만 강조하고 결제 시점을 흐리면 나중에 환불 요청과 불만으로 돌아온다.
        val trialLabel = selectedProduct
            ?.let { BillingPeriod.toLocalizedLabel(it.freeTrialPeriodIso8601) }
        val billingCycleText = selectedProduct?.let { billingCycleLabel(it.billingPeriodIso8601) }
        Text(
            text = when {
                state.isPremium -> stringResource(Res.string.paywall_renewal_notice_default)
                selectedProduct == null -> stringResource(Res.string.paywall_renewal_notice_default)
                trialLabel != null -> stringResource(
                    Res.string.paywall_renewal_notice_trial,
                    trialLabel,
                    selectedProduct.formattedPrice,
                    billingCycleText ?: "",
                )
                else -> stringResource(
                    Res.string.paywall_renewal_notice_standard,
                    selectedProduct.formattedPrice,
                    billingCycleText ?: "",
                )
            },
            style = MaterialTheme.typography.caption,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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

/**
 * 플랜 카드 한 장.
 *
 * 월간/연간을 가로로 나란히 놓기 때문에 카드 하나가 화면 절반 폭밖에 쓰지 못한다. 그래서
 * 내용은 좌/우로 가르지 않고 위에서 아래로 쌓는다(플랜명 → 배지 → 가격 → 절약률).
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
    Surface(
        modifier = modifier
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) primary else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) primary.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.05f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = planLabel(product.productId),
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
//            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
//                if (product.hasFreeTrial) {
//                    Surface(
//                        shape = RoundedCornerShape(6.dp),
//                        color = secondary
//                    ) {
//                        Text(
//                            text = stringResource(Res.string.paywall_badge_free_trial),
//                            modifier = Modifier.padding(
//                                horizontal = 6.dp,
//                                vertical = 2.dp
//                            ),
//                            style = MaterialTheme.typography.labelSmall,
//                            color = MaterialTheme.colorScheme.onSecondary
//                        )
//                    }
//                }
//            }
            Text(
                text = product.formattedPrice,
                color = primary,
                fontWeight = FontWeight.Bold
            )
            savingsPercent?.let {
                Text(
                    text = stringResource(Res.string.paywall_savings_monthly_equivalent, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = primary
                )
            }
        }
    }
}

/** 무료와 프리미엄에서 각각 쓸 수 있는지. 실제 앱의 잠금 상태와 일치해야 한다. */
private data class FeatureComparison(
    val labelRes: StringResource,
    val free: Boolean,
)

/**
 * 비교표에 적는 내용은 코드의 실제 잠금과 맞춰야 한다.
 * - 유료 잠금은 ReportScreen 의 PremiumReportSections(코골이 분석 / AI 수면 개선 조언 /
 *   나의 최근 수면과 비교)와 음원의 SleepMusic.isPremium 두 군데다.
 * - 나머지(측정·기본 리포트·알람)는 무료로 열려 있다.
 */
private val featureComparisons = listOf(
    FeatureComparison(Res.string.paywall_feature_sleep_tracking, free = true),
    FeatureComparison(Res.string.paywall_feature_basic_report, free = true),
    FeatureComparison(Res.string.paywall_feature_smart_alarm, free = true),
    FeatureComparison(Res.string.paywall_feature_basic_music, free = true),
    FeatureComparison(Res.string.paywall_feature_premium_music, free = false),
    FeatureComparison(Res.string.report_snoring_analysis_title, free = false),
    FeatureComparison(Res.string.report_ai_advice_title, free = false),
    FeatureComparison(Res.string.report_self_comparison_title, free = false),
)

/**
 * 무료와 프리미엄에서 쓸 수 있는 기능을 한눈에 비교한다.
 *
 * 예전에는 월간/연간의 금액·주기·해지 조건을 늘어놓았는데, 그건 바로 위 플랜 카드가 이미
 * 보여주는 정보라 같은 말을 두 번 하고 있었다. 결제 전에 실제로 궁금한 건 "돈을 내면 무엇이
 * 열리는가"다.
 */
@Composable
private fun PlanComparison() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        Color.White.copy(alpha = 0.05f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(Res.string.paywall_comparison_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            ComparisonHeaderRow()
            featureComparisons.forEach { feature ->
                ComparisonFeatureRow(
                    label = stringResource(feature.labelRes),
                    freeAvailable = feature.free,
                )
            }
        }
    }
}

/** 비교표의 "무료 / 프리미엄" 머리줄. 여기만 글자이고 아래는 모두 아이콘이다. */
@Composable
private fun ComparisonHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.weight(1.4f))
        Text(
            text = stringResource(Res.string.paywall_comparison_free_header),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyText,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = stringResource(Res.string.paywall_comparison_premium_header),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyText,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * 기능 한 줄.
 *
 * 예전에는 되는 칸에 "○"(U+25CB), 안 되는 칸에 "–"를 적었다. 둘 다 글리프라 폰트에 따라
 * 크기와 두께가 달라지고, 특히 "○"는 "비어 있음"으로도 읽혀 뜻이 뒤집힐 수 있었다.
 * 프리미엄 칸은 어차피 전부 되므로 체크로 통일한다.
 */
@Composable
private fun ComparisonFeatureRow(
    label: String,
    freeAvailable: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodyText,
            color = Color.White
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (freeAvailable) {
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = stringResource(Res.string.paywall_feature_available_free),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.paywall_feature_unavailable_free),
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = stringResource(Res.string.paywall_feature_available_premium),
                tint = primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
