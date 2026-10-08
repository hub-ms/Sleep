package com.soundsleeper.app.platform

import androidx.compose.runtime.Composable
import com.soundsleeper.app.enum_.PermissionType
import com.soundsleeper.app.ui.onboarding.PermissionContract

interface PermissionHandler {
    fun checkPermissionState(): PermissionContract.State
    fun request(type: PermissionType)

    /**
     * 측정에 필요한 런타임 권한을 시스템 대화상자 한 번으로 모두 요청한다.
     *
     * 권한마다 따로 요청하면 대화상자가 연달아 뜨고, 그 사이를 앱이 만든 안내 화면이 메워야
     * 했다. 배터리 최적화 제외는 시스템 대화상자가 없어 여기 포함되지 않는다.
     */
    fun requestTrackingPermissions()
}
@Composable
expect fun rememberPermissionHandler(
    onResult: (PermissionType, Boolean) -> Unit
): PermissionHandler

expect val isAndroidPlatform: Boolean