package com.sleepytime.shared.platform

data class PermissionState(
    val activityRecognition: Boolean = false,
    val audio: Boolean = false,
    val location: Boolean = false,
    val notification: Boolean = false
) {
    fun isAllGranted(): Boolean =
        activityRecognition && audio && location && notification
}

expect fun checkPermissionState(): PermissionState