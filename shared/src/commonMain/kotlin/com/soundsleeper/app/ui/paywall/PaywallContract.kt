package com.soundsleeper.app.ui.paywall

import com.soundsleeper.app.domain.model.BillingProductDetails

object PaywallContract {
    data class State(
        val isLoading: Boolean = true,
        val products: List<BillingProductDetails> = emptyList(),
        /** 선택된 카드의 [BillingProductDetails.planId]. 월간·연간은 productId 가
         *  같으므로 productId 로는 둘을 구분할 수 없다. */
        val selectedPlanId: String = "",
        val isPurchasing: Boolean = false,
        val isRestoring: Boolean = false,
        val isPremium: Boolean = false,
        val isGuest: Boolean = false,
        val errorMessage: String? = null,
    )

    sealed class Intent {
        object LoadPaywall : Intent()
        data class SelectPlan(val planId: String) : Intent()
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
