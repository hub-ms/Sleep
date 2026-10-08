package com.somniotune.app.platform

import androidx.compose.runtime.Composable
import com.somniotune.app.enum_.PermissionType
import com.somniotune.app.ui.onboarding.PermissionContract

class JvmPermissionHandler: PermissionHandler {
    override fun checkPermissionState(): PermissionContract.State = PermissionContract.State()
    override fun request(type: PermissionType) {}
    override fun requestTrackingPermissions() {}
}

@Composable
actual fun rememberPermissionHandler(
    onResult: (PermissionType, Boolean) -> Unit
): PermissionHandler = JvmPermissionHandler()

actual val isAndroidPlatform: Boolean = true
