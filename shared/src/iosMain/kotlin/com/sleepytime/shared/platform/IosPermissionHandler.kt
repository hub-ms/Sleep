package com.sleepytime.shared.platform

import androidx.compose.runtime.Composable
import com.sleepytime.shared.enum_.PermissionType
import com.sleepytime.shared.ui.onboarding.PermissionContract

class IosPermissionHandler: PermissionHandler {
    override fun checkPermissionState(): PermissionContract.State = PermissionContract.State()
    override fun request(type: PermissionType) {}
}

@Composable
actual fun rememberPermissionHandler(
    onResult: (PermissionType, Boolean) -> Unit
): PermissionHandler = IosPermissionHandler()
actual val isAndroidPlatform: Boolean = true
