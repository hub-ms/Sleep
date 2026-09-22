package com.sleepytime.shared.ui.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.domain.model.BillingProductDetails
import com.sleepytime.shared.domain.model.BillingProducts
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_caret_left
import com.sleepytime.shared.resources.ic_close
import com.sleepytime.shared.resources.ic_music_note
import com.sleepytime.shared.resources.ic_report
import com.sleepytime.shared.ui.theme.SleepTheme
import com.sleepytime.shared.ui.theme.caption
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

private fun planLabel(productId: String): String = when (productId) {
    BillingProducts.MONTHLY -> "월간 플랜"
    BillingProducts.YEARLY -> "연간 플랜"
    else -> productId
}

private data class PaywallFeature(
    val icon: DrawableResource,
    val title: String,
    val description: String,
)

private val paywallFeatures = listOf(
    PaywallFeature(Res.drawable.ic_music_note, "프리미엄 수면 음악", "더 다양한 사운드로 잠들기"),
    PaywallFeature(Res.drawable.ic_report, "상세 리포트 · 트렌드 분석", "수면 단계와 주간 변화를 한눈에"),
)

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
    onRestoreClicked: () -> Unit,
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
            .background(SleepTheme.gradients.background)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(Res.drawable.ic_caret_left),
                contentDescription = "뒤로가기",
                tint = SleepTheme.textColors.primary
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "SleepyTime 프리미엄",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = SleepTheme.textColors.primary
            )
            Text(
                text = "프리미엄 수면 음악과 상세 리포트로 더 깊은 잠을 준비하세요",
                style = MaterialTheme.typography.bodyMedium,
                color = SleepTheme.textColors.secondary
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            paywallFeatures.forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Icon(
                            painter = painterResource(feature.icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp).size(20.dp)
                        )
                    }
                    Column {
                        Text(feature.title, color = SleepTheme.textColors.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(feature.description, color = SleepTheme.textColors.secondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (state.products.isEmpty() && state.isLoading) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = SleepTheme.textColors.primary.copy(alpha = 0.05f)
                    ) {}
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.products.forEach { product ->
                    PlanCard(
                        product = product,
                        isSelected = product.productId == state.selectedProductId,
                        isRecommended = product.productId == BillingProducts.YEARLY,
                        savingsPercent = if (product.productId == BillingProducts.YEARLY) savingsPercent else null,
                        onClick = { onSelectPlan(product.productId) }
                    )
                }
            }
        }

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
                            contentDescription = "닫기",
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
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (state.isPurchasing) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(
                    text = when {
                        state.isPremium -> "이미 프리미엄 이용 중"
                        selectedProduct != null -> "${selectedProduct.formattedPrice} 구독 시작"
                        else -> "구독하기"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = "언제든 설정에서 자동 갱신을 해지할 수 있어요",
            style = MaterialTheme.typography.caption,
            color = SleepTheme.textColors.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        TextButton(
            onClick = onRestoreClicked,
            enabled = !state.isRestoring,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (state.isRestoring) "복원 중..." else "구매 복원",
                color = SleepTheme.textColors.secondary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "이용약관",
                style = MaterialTheme.typography.caption,
                textDecoration = TextDecoration.Underline,
                color = SleepTheme.textColors.secondary,
                modifier = Modifier.clickable { onNavigateToTerms() }
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
            Text(
                text = "개인정보 처리방침",
                style = MaterialTheme.typography.caption,
                textDecoration = TextDecoration.Underline,
                color = SleepTheme.textColors.secondary,
                modifier = Modifier.clickable { onNavigateToPrivacy() }
            )
        }
    }
}

@Composable
private fun PlanCard(
    product: BillingProductDetails,
    isSelected: Boolean,
    isRecommended: Boolean,
    savingsPercent: Int?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else SleepTheme.textColors.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else SleepTheme.textColors.primary.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(planLabel(product.productId), color = SleepTheme.textColors.primary, fontWeight = FontWeight.Bold)
                    if (isRecommended) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary) {
                            Text(
                                "추천",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = product.formattedPrice,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                savingsPercent?.let {
                    Text(
                        text = "월 환산 $it% 절약",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
