package com.sleepytime.shared.platform

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.sleepytime.shared.enum_.PermissionType

@Composable
actual fun rememberPermissionHandler(
    onResult: (PermissionType, Boolean) -> Unit
): PermissionHandler {

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult(PermissionType.ACTIVITY, it) }

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult(PermissionType.AUDIO, it) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult(PermissionType.LOCATION, it) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult(PermissionType.NOTIFICATION, it) }

    return remember {
        object : PermissionHandler {
            override fun request(type: PermissionType) {
                when (type) {
                    PermissionType.ACTIVITY ->
                        activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    PermissionType.AUDIO ->
                        audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    PermissionType.LOCATION ->
                        locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    PermissionType.NOTIFICATION -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }
        }
    }
}