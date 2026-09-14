package com.sleepytime.shared.ui.onboarding

object PermissionContract {
    data class State(
        val audio: Boolean = false,
        val notification: Boolean = false,
        val batteryOptimizationIgnored: Boolean = false,
        val activity: Boolean = false
    ) {
        fun isAllGranted(): Boolean = audio && notification && activity && batteryOptimizationIgnored
    }
    sealed class Intent {
        object PermissionGranted: Intent()
        object PermissionDenied: Intent()
    }
}

