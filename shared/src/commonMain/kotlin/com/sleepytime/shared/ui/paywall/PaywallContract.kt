package com.sleepytime.shared.ui.paywall

import com.sleepytime.shared.domain.model.BillingProductDetails

object PaywallContract {
    data class State(
        val isLoading: Boolean = true,
        val products: List<BillingProductDetails> = emptyList(),
        val selectedProductId: String = "",
        val isPurchasing: Boolean = false,
        val isRestoring: Boolean = false,
        val isPremium: Boolean = false,
        val isGuest: Boolean = false,
        val errorMessage: String? = null,
    )

    sealed class Intent {
        object LoadPaywall : Intent()
        data class SelectPlan(val productId: String) : Intent()
        object SubscribeClicked : Intent()
        object RestoreClicked : Intent()
        object DismissError : Intent()
    }

    sealed class Effect {
        object NavigateBack : Effect()
        object RequireLogin : Effect()
        data class ShowToast(val message: String) : Effect()
    }
}
