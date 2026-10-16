package com.soundsleeper.app.platform

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.soundsleeper.app.enum_.PermissionType
import androidx.core.net.toUri
import com.soundsleeper.app.ui.onboarding.PermissionContract

class AndroidPermissionHandler(
    private val context: Context,
    private val onLaunchAudio: () -> Unit,
    private val onLaunchNotification: () -> Unit,
    private val onLaunchActivity: () -> Unit,
    private val onLaunchBatteryOptimization: () -> Unit,
    private val onLaunchTrackingPermissions: () -> Unit,
) : PermissionHandler {
    override fun checkPermissionState(): PermissionContract.State {
        fun isGranted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        val pm = context.getSystemService(PowerManager::class.java)
        return PermissionContract.State(
            audio = isGranted(Manifest.permission.RECORD_AUDIO),
            notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isGranted(Manifest.permission.POST_NOTIFICATIONS)
            } else true,
            activity = isGranted(Manifest.permission.ACTIVITY_RECOGNITION),
            batteryOptimizationIgnored = pm.isIgnoringBatteryOptimizations(context.packageName),
        )
    }

    override fun requestTrackingPermissions() = onLaunchTrackingPermissions()

    override fun request(type: PermissionType) {
        Log.d("PermissionCheck", "Requesting permission: $type") // 💡 실행 여부 확인용 로그
        when (type) {
            PermissionType.AUDIO -> onLaunchAudio()
            PermissionType.NOTIFICATION -> onLaunchNotification()
            PermissionType.ACTIVITY_RECOGNITION -> onLaunchActivity()
            PermissionType.BATTERY_OPTIMIZATION -> onLaunchBatteryOptimization()
        }
    }
}

@Composable
actual fun rememberPermissionHandler(
    onResult: (PermissionType, Boolean) -> Unit
): PermissionHandler {
    val context = LocalContext.current

    // 💡 헬퍼: 최신 콜백을 참조하기 위해 rememberUpdatedState 사용
    val currentOnResult by rememberUpdatedState(onResult)

    // 측정에 필요한 런타임 권한 세 가지를 한 번에 요청하는 런처. 예전에는 권한마다
    // 런처를 따로 두고 앱이 만든 카드에서 하나씩 눌러 받게 했다.
    val trackingPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        result.forEach { (permission, granted) ->
            when (permission) {
                Manifest.permission.RECORD_AUDIO -> currentOnResult(PermissionType.AUDIO, granted)
                Manifest.permission.POST_NOTIFICATIONS ->
                    currentOnResult(PermissionType.NOTIFICATION, granted)
                Manifest.permission.ACTIVITY_RECOGNITION ->
                    currentOnResult(PermissionType.ACTIVITY_RECOGNITION, granted)
            }
        }
    }

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { currentOnResult(PermissionType.AUDIO, it) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { currentOnResult(PermissionType.NOTIFICATION, it) }
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { currentOnResult(PermissionType.ACTIVITY_RECOGNITION, it) }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pm = context.getSystemService(PowerManager::class.java)
        val granted = pm.isIgnoringBatteryOptimizations(context.packageName)
        currentOnResult(PermissionType.BATTERY_OPTIMIZATION, granted)
    }

    return remember(
        context,
        audioLauncher,
        notificationLauncher,
        activityLauncher,
        batteryLauncher,
        trackingPermissionsLauncher,
    ) {
        AndroidPermissionHandler(
            context = context,
            onLaunchTrackingPermissions = {
                // POST_NOTIFICATIONS 는 API 33 미만에 존재하지 않는다. 목록에 넣으면
                // 시스템이 통째로 거부하므로 버전에 따라 뺀다.
                val permissions = buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    add(Manifest.permission.ACTIVITY_RECOGNITION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        currentOnResult(PermissionType.NOTIFICATION, true)
                    }
                }
                trackingPermissionsLauncher.launch(permissions.toTypedArray())
            },
            onLaunchAudio = { audioLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onLaunchNotification = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    currentOnResult(PermissionType.NOTIFICATION, true)
                }
            },
            onLaunchActivity = {
                activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            },
            onLaunchBatteryOptimization = {
                val pm = context.getSystemService(PowerManager::class.java)
                if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
                    currentOnResult(PermissionType.BATTERY_OPTIMIZATION, true)
                    return@AndroidPermissionHandler
                }

                val targetIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

                runCatching {
                    batteryLauncher.launch(targetIntent)
                }.onFailure {
                    openBatteryOptimizationSettingsFallback(context, batteryLauncher)
                }
            },
        )
    }
}
actual val isAndroidPlatform: Boolean = true
private fun openBatteryOptimizationSettingsFallback(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    // 1순위: 삼성 스마트 매니저 배터리 설정 화면
    val samsungIntent = Intent().apply {
        component = ComponentName(
            "com.samsung.android.lool",
            "com.samsung.android.sm.ui.battery.BatteryActivity"
        )
    }

    // 2순위: 가장 안전하고 구글이 권장하는 앱 상세 설정 화면
    // (여기서 사용자가 '배터리 -> 제한 없음'으로 변경하도록 UI 문구로 안내해야 합니다)
    val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:${context.packageName}".toUri()
    }

    runCatching {
        launcher.launch(samsungIntent)
    }.onFailure {
        launcher.launch(appDetailsIntent)
    }
}