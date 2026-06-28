package com.sleepytime.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.sleepytime.shared.enum_.PermissionType

@Composable
actual fun rememberPermissionHandler(
    onResult: (PermissionType, Boolean) -> Unit
): PermissionHandler {
    return remember {
        object : PermissionHandler {
            override fun request(type: PermissionType) {

            }
        }
    }
}