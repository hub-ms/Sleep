package com.sleepytime.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.sleepytime.shared.enum_.PermissionType
import platform.AVFAudio.AVAudioSession
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.UserNotifications.*

@Composable
actual fun rememberPermissionHandler(
    onResult: (PermissionType, Boolean) -> Unit
): PermissionHandler {
    return remember {
        object : PermissionHandler {
            override fun request(type: PermissionType) {
                when (type) {
                    PermissionType.ACTIVITY -> {
                        onResult(PermissionType.ACTIVITY, true)
                    }
                    PermissionType.AUDIO -> {
                        AVAudioSession.sharedInstance()
                            .requestRecordPermission { granted ->
                                onResult(PermissionType.AUDIO, granted)
                            }
                    }
                    PermissionType.LOCATION -> {
                        val manager = CLLocationManager()
                        manager.requestWhenInUseAuthorization()
                        val granted = manager.authorizationStatus ==
                                kCLAuthorizationStatusAuthorizedWhenInUse ||
                                manager.authorizationStatus ==
                                kCLAuthorizationStatusAuthorizedAlways
                        onResult(PermissionType.LOCATION, granted)
                    }
                    PermissionType.NOTIFICATION -> {
                        UNUserNotificationCenter.currentNotificationCenter()
                            .requestAuthorizationWithOptions(
                                UNAuthorizationOptionAlert or UNAuthorizationOptionSound
                            ) { granted, _ ->
                                onResult(PermissionType.NOTIFICATION, granted)
                            }
                    }
                }
            }
        }
    }
}