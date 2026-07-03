package com.sleepytime.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.sleepytime.shared.enum_.PermissionType
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.CoreMotion.CMMotionActivityManager
import platform.Foundation.NSOperationQueue
import platform.UserNotifications.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

class IosPermissionHandler(
    private val onResult: (PermissionType, Boolean) -> Unit
) : PermissionHandler {
    private var lastKnownActivityGranted: Boolean = false
    private val motionActivityManager by lazy { CMMotionActivityManager() }


    override fun checkPermissionState(): PermissionState {
        val audioGranted = when (AVAudioSession.sharedInstance().recordPermission()) {
            AVAudioSessionRecordPermissionGranted -> true
            else -> false
        }

        var notificationGranted = false
        UNUserNotificationCenter.currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                notificationGranted = settings?.authorizationStatus == UNAuthorizationStatusAuthorized
                dispatch_async(dispatch_get_main_queue()) {
                    onResult(PermissionType.NOTIFICATION, notificationGranted)
                }
            }

        return PermissionState(
            audio = audioGranted,
            notification = notificationGranted,
            activity = lastKnownActivityGranted
        )
    }

    override fun request(type: PermissionType) {
        when (type) {
            PermissionType.AUDIO -> {
                AVAudioSession.sharedInstance().requestRecordPermission { granted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        onResult(PermissionType.AUDIO, granted)
                    }
                }
            }
            PermissionType.NOTIFICATION -> {
                UNUserNotificationCenter.currentNotificationCenter()
                    .requestAuthorizationWithOptions(
                        UNAuthorizationOptionAlert or UNAuthorizationOptionSound
                    ) { granted, _ ->
                        dispatch_async(dispatch_get_main_queue()) {
                            onResult(PermissionType.NOTIFICATION, granted)
                        }
                    }
            }
            PermissionType.ACTIVITY_RECOGNITION -> {
                if (!CMMotionActivityManager.isActivityAvailable()) {
                    lastKnownActivityGranted = false
                    onResult(PermissionType.ACTIVITY_RECOGNITION, false)
                    return
                }
                motionActivityManager.startActivityUpdatesToQueue(
                    NSOperationQueue.mainQueue()
                ) { _ ->
                    // 콜백이 호출된다는 것 자체가 승인되었다는 뜻
                    lastKnownActivityGranted = true
                    onResult(PermissionType.ACTIVITY_RECOGNITION, true)
                    motionActivityManager.stopActivityUpdates() // 권한 확인 목적이므로 바로 중단
                }
            }
            PermissionType.BATTERY_OPTIMIZATION -> {
                onResult(PermissionType.BATTERY_OPTIMIZATION, true)   // iOS는 해당 없음
            }
        }
    }
}
@Composable
actual fun rememberPermissionHandler(
    onResult: (PermissionType, Boolean) -> Unit
): PermissionHandler {
    return remember {
        IosPermissionHandler(onResult)
    }
}
actual val isAndroidPlatform: Boolean = false