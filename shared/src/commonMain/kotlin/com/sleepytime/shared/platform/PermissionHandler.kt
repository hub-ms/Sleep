package com.sleepytime.shared.platform

import androidx.compose.runtime.Composable
import com.sleepytime.shared.enum_.PermissionType

interface PermissionHandler {
    fun request(type: PermissionType)
}

@Composable
expect fun rememberPermissionHandler(
    onResult: (PermissionType, Boolean) -> Unit
): PermissionHandler