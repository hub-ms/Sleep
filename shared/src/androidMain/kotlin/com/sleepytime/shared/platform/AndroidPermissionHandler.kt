package com.sleepytime.shared.platform

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.sleepytime.shared.enum_.PermissionType
import androidx.core.net.toUri

class AndroidPermissionHandler(
    private val context: Context,
    private val onLaunchAudio: () -> Unit,
    private val onLaunchNotification: () -> Unit,
    private val onLaunchActivity: () -> Unit,
    private val onLaunchBatteryOptimization: () -> Unit
) : PermissionHandler {
    override fun checkPermissionState(): PermissionState {
        fun isGranted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        val pm = context.getSystemService(PowerManager::class.java)
        return PermissionState(
            audio = isGranted(Manifest.permission.RECORD_AUDIO),
            notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isGranted(Manifest.permission.POST_NOTIFICATIONS)
            } else true,
            activity = isGranted(Manifest.permission.ACTIVITY_RECOGNITION),
            batteryOptimizationIgnored = pm.isIgnoringBatteryOptimizations(context.packageName)
        )
    }

    override fun request(type: PermissionType) {
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

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult(PermissionType.AUDIO, it) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult(PermissionType.NOTIFICATION, it) }
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult(PermissionType.ACTIVITY_RECOGNITION, it) }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pm = context.getSystemService(PowerManager::class.java)
        val granted = pm.isIgnoringBatteryOptimizations(context.packageName)
        onResult(PermissionType.BATTERY_OPTIMIZATION, granted)
    }

    return remember(context) {
        AndroidPermissionHandler(
            context = context,
            onLaunchAudio = { audioLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onLaunchNotification = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onResult(PermissionType.NOTIFICATION, true)
                }
            },
            onLaunchActivity = {
                activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            },
            onLaunchBatteryOptimization = {
                val pm = context.getSystemService(PowerManager::class.java)
                if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
                    onResult(PermissionType.BATTERY_OPTIMIZATION, true)
                    return@AndroidPermissionHandler
                }

                // ⚠️ 플레이 스토어 정책 위반(REQUEST_IGNORE...) 팝업 대신
                // 안전한 전체 배터리 최적화 설정 목록 화면으로 이동
                val targetIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

                runCatching {
                    batteryLauncher.launch(targetIntent)
                }.onFailure {
                    // 기기 호환성 문제 발생 시 제조사 커스텀 설정 또는 앱 상세 페이지로 폴백
                    openBatteryOptimizationSettingsFallback(context, batteryLauncher)
                }
            }
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