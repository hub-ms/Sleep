package com.sleepytime.shared.platform

import androidx.compose.runtime.Composable
import com.sleepytime.shared.enum_.PermissionType

data class PermissionState(
    val audio: Boolean = false,
    val notification: Boolean = false,
    val activity: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false
) {
    fun isAllGranted(): Boolean =
        audio && notification && activity && batteryOptimizationIgnored
}
interface PermissionHandler {
    fun checkPermissionState(): PermissionState
    fun request(type: PermissionType)
}
@Composable
expect fun rememberPermissionHandler(
    onResult: (PermissionType, Boolean) -> Unit
): PermissionHandler

expect val isAndroidPlatform: Boolean