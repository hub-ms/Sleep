package com.sleepytime.shared.platform

import com.sleepytime.shared.ui.tracking.TrackingContract
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface TrackingManager {
    val trackingState: StateFlow<TrackingContract.State>
    val wakeAlarmEvent: SharedFlow<String>
    fun attachCallbacks(onNotificationUpdate: (String) -> Unit, onRequestStopForeground: () -> Unit)
    fun start(sessionId: String, durationMillis: Long, musicTitle: String?)
    fun finish(sessionId: String, musicTitle: String?)
    fun discard(sessionId: String, musicTitle: String?)
    fun updateEndTime(hour: Int, minute: Int)
}