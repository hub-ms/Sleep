package com.sleepytime.shared.ui.onboarding

object OnboardingContract {
    data class State(
        val step: Int = 0,
        val answers: Map<Int, Set<Int>> = emptyMap(),
        val isLastPage: Boolean = false,
        val selectedProvider: String? = null,
        val showMoreLoginOption: Boolean = false
    )

    sealed class Intent {
        object NextStep : Intent()
        object PermissionGranted: Intent()
        object PermissionDenied: Intent()


//    data class SelectOption(val page: Int, val index: Int) : Intent()
//    data class OnboardingButtonClicked(val isLastPage: Boolean) : Intent()
    }

    sealed class Effect {
        object NavigateToAnalyzing : Effect()
    }
}

