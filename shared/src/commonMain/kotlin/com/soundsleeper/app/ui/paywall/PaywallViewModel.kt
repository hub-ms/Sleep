package com.soundsleeper.app.ui.paywall

import com.soundsleeper.app.ui.common.AppScopedScreenModel
import com.soundsleeper.app.domain.model.BillingProducts
import com.soundsleeper.app.domain.model.BillingPurchaseState
import com.soundsleeper.app.domain.model.User
import com.soundsleeper.app.domain.repository.AuthRepository
import com.soundsleeper.app.domain.repository.BillingRepository
import com.soundsleeper.app.platform.PlayBillingManager
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.paywall_error_load_failed
import com.soundsleeper.app.resources.paywall_error_products_empty
import com.soundsleeper.app.resources.paywall_error_products_partial
import com.soundsleeper.app.resources.paywall_error_purchase_verify_failed
import com.soundsleeper.app.resources.paywall_error_restore_failed
import com.soundsleeper.app.resources.paywall_toast_no_purchase_to_restore
import com.soundsleeper.app.resources.paywall_toast_nothing_to_restore
import com.soundsleeper.app.resources.paywall_toast_restored
import com.soundsleeper.app.resources.paywall_toast_subscribed
import com.soundsleeper.app.util.BillingPeriod
import io.github.aakira.napier.Napier
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PaywallViewModel(
    private val billingRepository: BillingRepository,
    private val playBillingManager: PlayBillingManager,
    private val authRepository: AuthRepository,
)  : AppScopedScreenModel() {

    // 예전에는 여기서 selectedProductId 를 "dive_premium_yearly" 로 못 박았다. 실제로 내려오는
    // 상품 ID 는 "dive_premium" 하나뿐이라 영영 매칭되지 않았고, selectedProduct 가 항상 null
    // 이라 갱신 고지문과 체험 CTA 가 한 번도 렌더되지 않았다. 기본 선택은 상품을 받아 본 뒤
    // loadPaywall() 에서 정한다.
    private val _state = MutableStateFlow(PaywallContract.State())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<PaywallContract.Effect>()
    val effect = _effect.asSharedFlow()

    private val _intentChannel = Channel<PaywallContract.Intent>(Channel.BUFFERED)

    init {
        _intentChannel.receiveAsFlow()
            .onEach { processIntent(it) }
            .launchIn(modelScope)
    }

    fun sendIntent(intent: PaywallContract.Intent) {
        modelScope.launch { _intentChannel.send(intent) }
    }

    private suspend fun processIntent(intent: PaywallContract.Intent) = when (intent) {
        is PaywallContract.Intent.LoadPaywall -> loadPaywall()
        is PaywallContract.Intent.SelectPlan -> {
            _state.update { it.copy(selectedPlanId = intent.planId) }
        }

        is PaywallContract.Intent.SubscribeClicked -> subscribe()
        is PaywallContract.Intent.RestoreClicked -> restore()
        is PaywallContract.Intent.DismissError -> {
            _state.update { it.copy(errorMessage = null) }
        }
    }

    private suspend fun loadPaywall() {
        _state.update { it.copy(isLoading = true) }

        val userContext = authRepository.getUserContext()
        val isGuest = userContext is User.AuthInfo.Guest
        val currentUser = authRepository.getUser()

        val productsResult = runCatching { playBillingManager.queryProductDetails(BillingProducts.ALL_PRODUCTS) }
            .onFailure { Napier.e("구독 상품 조회 실패: ${it.message}", it) }
        // 긴 주기(연간)를 위에 둔다. 추천 플랜을 먼저 보여주는 흔한 배치이고, 할인율 뱃지도
        // 거기 붙는다. 주기를 파싱할 수 없는 항목은 맨 뒤로 보낸다.
        val products = productsResult.getOrDefault(emptyList())
            .sortedByDescending { BillingPeriod.approximateDays(it.billingPeriodIso8601) }

        // 상품 조회가 실패하거나 일부만 돌아오면 플랜 카드와 플랜 비교 영역이 조용히 사라져
        // 화면이 원인 없이 비어 보인다. 실패를 삼키지 말고 사용자에게 드러낸다.
        val missingProducts = BillingProducts.ALL_PRODUCTS.filterNot { id ->
            products.any { it.productId == id }
        }
        val loadErrorMessage = when {
            productsResult.isFailure ->
                getString(Res.string.paywall_error_load_failed)
            products.isEmpty() ->
                getString(Res.string.paywall_error_products_empty)
            missingProducts.isNotEmpty() -> {
                Napier.w("구독 상품 일부 누락: $missingProducts")
                getString(Res.string.paywall_error_products_partial)
            }
            else -> null
        }

        _state.update {
            it.copy(
                isLoading = false,
                products = products,
                isGuest = isGuest,
                isPremium = currentUser?.isPremium == true,
                errorMessage = loadErrorMessage ?: it.errorMessage,
                // 아무것도 선택되지 않은 화면을 보여주지 않는다. 이미 고른 플랜이 여전히
                // 유효하면 지키고, 아니면 연간(정렬상 첫 번째)을 기본으로 고른다.
                selectedPlanId = products
                    .firstOrNull { p -> p.planId == it.selectedPlanId }
                    ?.planId
                    ?: products.firstOrNull()?.planId.orEmpty(),
            )
        }
    }

    private suspend fun subscribe() {
        if (_state.value.isGuest) { _effect.emit(PaywallContract.Effect.RequireLogin); return }
        if (_state.value.isPurchasing) return
        _state.update { it.copy(isPurchasing = true, errorMessage = null) }

        var failed = false
        val purchase = runCatching { playBillingManager.launchPurchaseFlow(_state.value.selectedPlanId) }
            .onFailure { Napier.e("구매 플로우 실패: ${it.message}", it); failed = true }
            .getOrNull()

        // 정상 반환이면 기존 경로, 취소/실패면 Play에 이미 결제된 구독이 있는지 확인한다.
        val targets = if (purchase != null) listOf(purchase)
        else runCatching { playBillingManager.queryActivePurchases() }
            .getOrDefault(emptyList())
            .filter { it.state == BillingPurchaseState.PURCHASED }

        if (targets.isEmpty()) {
            _state.update {
                it.copy(
                    isPurchasing = false,
                    errorMessage = if (failed) getString(Res.string.paywall_error_purchase_verify_failed) else null,
                )
            }
            return
        }

        val result = if (purchase != null) billingRepository.verifyPurchase(purchase)
        else billingRepository.restorePurchases(targets)

        result
            .onSuccess { status ->
                _state.update { it.copy(isPurchasing = false, isPremium = status.isPremium) }
                _effect.emit(PaywallContract.Effect.ShowToast(getString(Res.string.paywall_toast_subscribed)))
                _effect.emit(PaywallContract.Effect.NavigateBack)
            }
            .onFailure { error ->
                _state.update {
                    it.copy(isPurchasing = false,
                        errorMessage = getString(Res.string.paywall_error_purchase_verify_failed))
                }
                Napier.e("구매 검증 실패: ${error.message}", error)
            }
    }

    private suspend fun restore() {
        if (_state.value.isRestoring) return
        _state.update { it.copy(isRestoring = true, errorMessage = null) }

        val purchases = runCatching { playBillingManager.queryActivePurchases() }
            .onFailure { Napier.e("구매 내역 조회 실패: ${it.message}", it) }
            .getOrDefault(emptyList())

        if (purchases.isEmpty()) {
            _state.update { it.copy(isRestoring = false) }
            _effect.emit(PaywallContract.Effect.ShowToast(getString(Res.string.paywall_toast_no_purchase_to_restore)))
            return
        }

        billingRepository.restorePurchases(purchases)
            .onSuccess { status ->
                _state.update { it.copy(isRestoring = false, isPremium = status.isPremium) }
                _effect.emit(
                    PaywallContract.Effect.ShowToast(
                        if (status.isPremium) getString(Res.string.paywall_toast_restored)
                        else getString(Res.string.paywall_toast_nothing_to_restore)
                    )
                )
                if (status.isPremium) _effect.emit(PaywallContract.Effect.NavigateBack)
            }
            .onFailure { error ->
                _state.update { it.copy(isRestoring = false, errorMessage = getString(Res.string.paywall_error_restore_failed)) }
                Napier.e("구매 복원 실패: ${error.message}", error)
            }
    }
}
