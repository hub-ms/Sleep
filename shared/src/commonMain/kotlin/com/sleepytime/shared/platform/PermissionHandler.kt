package com.sleepytime.shared.platform

import androidx.compose.runtime.Composable
import com.sleepytime.shared.enum_.PermissionType
import com.sleepytime.shared.ui.onboarding.PermissionContract

interface PermissionHandler {
    fun checkPermissionState(): PermissionContract.State
    fun request(type: PermissionType)
}
@Composable
expect fun rememberPermissionHandler(
    onResult: (PermissionType, Boolean) -> Unit
): PermissionHandler

expect val isAndroidPlatform: Boolean