package com.soundsleeper.app.ui.paywall

import com.soundsleeper.app.ui.common.AppScopedScreenModel
import com.soundsleeper.app.domain.model.BillingProducts
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

    private val _state = MutableStateFlow(PaywallContract.State(selectedProductId = BillingProducts.YEARLY))
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
            _state.update { it.copy(selectedProductId = intent.productId) }
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

        val productsResult = runCatching { playBillingManager.queryProductDetails(BillingProducts.ALL) }
            .onFailure { Napier.e("구독 상품 조회 실패: ${it.message}", it) }
        val products = productsResult.getOrDefault(emptyList())

        // 상품 조회가 실패하거나 일부만 돌아오면 플랜 카드와 플랜 비교 영역이 조용히 사라져
        // 화면이 원인 없이 비어 보인다. 실패를 삼키지 말고 사용자에게 드러낸다.
        val missingProducts = BillingProducts.ALL.filterNot { id ->
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
            )
        }
    }

    private suspend fun subscribe() {
        if (_state.value.isGuest) {
            _effect.emit(PaywallContract.Effect.RequireLogin)
            return
        }
        if (_state.value.isPurchasing) return

        _state.update { it.copy(isPurchasing = true, errorMessage = null) }

        val purchase = runCatching { playBillingManager.launchPurchaseFlow(_state.value.selectedProductId) }
            .onFailure { Napier.e("구매 플로우 실패: ${it.message}", it) }
            .getOrNull()

        if (purchase == null) {
            _state.update { it.copy(isPurchasing = false) }
            return
        }

        billingRepository.verifyPurchase(purchase)
            .onSuccess { status ->
                _state.update { it.copy(isPurchasing = false, isPremium = status.isPremium) }
                _effect.emit(PaywallContract.Effect.ShowToast(getString(Res.string.paywall_toast_subscribed)))
                _effect.emit(PaywallContract.Effect.NavigateBack)
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        isPurchasing = false,
                        errorMessage = getString(Res.string.paywall_error_purchase_verify_failed),
                    )
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
