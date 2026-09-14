package com.sleepytime.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberProfileImageLauncher(
    onImageSelected: (ByteArray) -> Unit
): ProfileImageLauncher {
    return remember {
        object : ProfileImageLauncher {
            override fun launchAlbum() {}
            override fun launchCamera() {}
        }
    }
}
