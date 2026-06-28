package com.sleepytime.shared.ui.environment


object EnvironmentContract {
    sealed class State {
        data class Success(
            val temperature: Float,
            val humidity: Float,
            val precipitation: Float,
            val nx: String,
            val ny: String,
        ) : State()
        data class Error(val message: String) : State()
    }
}
