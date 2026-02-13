package com.example.sleep.presentation.contract

interface TrackingContract {
    sealed class TrackingIntent {
        data object StartTracking : TrackingIntent()
        data object StopTracking : TrackingIntent()
        data object FinishTracking : TrackingIntent()
        data object SelectMusic : TrackingIntent()
    }

    data class TrackingState(
        val remainingHour: Int = 0,
        val remainingMinute: Int = 0,
        val musicTitle: String = "",
        val musicImageRes: Int = 0,
        val isPlaying: Boolean = false,
        val isFinished: Boolean = false,
        val isTracking: Boolean = false
    )
}