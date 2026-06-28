package com.sleepytime.shared.ui.onboarding

import com.sleepytime.shared.enum_.OnboardingSelectionMode

data class Question(
    val title: String,
    val options: List<String>,
    val selectionMode: OnboardingSelectionMode
)
