package com.sleepytime.shared.ui.paywall

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.sleepytime.shared.domain.model.BillingProducts
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.domain.repository.AuthRepository
import com.sleepytime.shared.domain.repository.BillingRepository
import com.sleepytime.shared.platform.PlayBillingManager
import io.github.aakira.napier.Napier
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
) : ScreenModel {

    private val _state = MutableStateFlow(PaywallContract.State(selectedProductId = BillingProducts.YEARLY))
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<PaywallContract.Effect>()
    val effect = _effect.asSharedFlow()

    private val _intentChannel = Channel<PaywallContract.Intent>(Channel.BUFFERED)

    init {
        _intentChannel.receiveAsFlow()
            .onEach { processIntent(it) }
            .launchIn(screenModelScope)
    }

    fun sendIntent(intent: PaywallContract.Intent) {
        screenModelScope.launch { _intentChannel.send(intent) }
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

        val products = runCatching { playBillingManager.queryProductDetails(BillingProducts.ALL) }
            .onFailure { Napier.e("구독 상품 조회 실패: ${it.message}", it) }
            .getOrDefault(emptyList())

        _state.update {
            it.copy(
                isLoading = false,
                products = products,
                isGuest = isGuest,
                isPremium = currentUser?.isPremium == true,
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
                _effect.emit(PaywallContract.Effect.ShowToast("구독이 완료되었습니다"))
                _effect.emit(PaywallContract.Effect.NavigateBack)
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        isPurchasing = false,
                        errorMessage = "구매 확인에 실패했습니다. 잠시 후 다시 시도해주세요.",
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
            _effect.emit(PaywallContract.Effect.ShowToast("복원할 구매 내역이 없습니다"))
            return
        }

        billingRepository.restorePurchases(purchases)
            .onSuccess { status ->
                _state.update { it.copy(isRestoring = false, isPremium = status.isPremium) }
                _effect.emit(
                    PaywallContract.Effect.ShowToast(
                        if (status.isPremium) "구독이 복원되었습니다" else "복원할 구독이 없습니다"
                    )
                )
                if (status.isPremium) _effect.emit(PaywallContract.Effect.NavigateBack)
            }
            .onFailure { error ->
                _state.update { it.copy(isRestoring = false, errorMessage = "구매 복원에 실패했습니다.") }
                Napier.e("구매 복원 실패: ${error.message}", error)
            }
    }
}
