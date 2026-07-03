package com.sleepytime.shared.platform

import com.sleepytime.shared.ui.tracking.TrackingContract
import kotlinx.coroutines.flow.StateFlow

interface TrackingManager {
    val trackingState: StateFlow<TrackingContract.State>
    fun attachCallbacks(onNotificationUpdate: (String) -> Unit, onRequestStopForeground: () -> Unit)
    fun start(sessionId: String, duration: Int, musicTitle: String?)
    fun discard()
    fun finish()
    fun updateEndTime(hour: Int, minute: Int)
}