package com.sleepytime.shared.platform

import androidx.compose.runtime.Composable

/**
 * 앨범 및 카메라 연동을 위한 플랫폼별 런처 인터페이스
 */
interface ProfileImageLauncher {
    fun launchAlbum()
    fun launchCamera()
}

/**
 * 이미지 선택 또는 촬영 후 이미지 데이터를 반환받는 런처를 생성합니다.
 */
@Composable
expect fun rememberProfileImageLauncher(
    onImageSelected: (ByteArray) -> Unit
): ProfileImageLauncher
