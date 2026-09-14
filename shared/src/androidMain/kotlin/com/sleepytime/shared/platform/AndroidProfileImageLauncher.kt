package com.sleepytime.shared.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberProfileImageLauncher(
    onImageSelected: (ByteArray) -> Unit
): ProfileImageLauncher {
    val context = LocalContext.current
    
    // 앨범 런처
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.use { input ->
                input.readBytes()
            }
            bytes?.let(onImageSelected)
        }
    }

    // 카메라 런처
    var tempUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempUri?.let { uri ->
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                }
                bytes?.let(onImageSelected)
            }
        }
    }

    return remember {
        object : ProfileImageLauncher {
            override fun launchAlbum() {
                galleryLauncher.launch("image/*")
            }

            override fun launchCamera() {
                val imagesDir = File(context.cacheDir, "images")
                if (!imagesDir.exists()) imagesDir.mkdirs()
                
                val file = File(imagesDir, "temp_profile.jpg")
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                tempUri = uri
                cameraLauncher.launch(uri)
            }
        }
    }
}
