package com.soundsleeper.app.platform

import androidx.compose.runtime.Composable
import com.soundsleeper.app.enum_.PermissionType
import com.soundsleeper.app.ui.onboarding.PermissionContract

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
