package com.soundsleeper.app.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * 업로드 전 축소 기준. 서버 업로드 한도가 5MB(LocalFileStorage.MAX_BYTES)인데 요즘 폰의
 * 원본 촬영본은 10MB를 넘기기 일쑤라, 원본을 그대로 보내면 카메라 경로가 항상 거부된다.
 */
private const val MAX_IMAGE_DIMENSION = 1024
private const val JPEG_QUALITY = 85

@Composable
actual fun rememberProfileImageLauncher(
    onImageSelected: (ByteArray) -> Unit
): ProfileImageLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 런처 객체는 한 번만 만들고 재사용하므로, 콜백은 최신 참조로 따로 들고 있는다.
    val currentOnImageSelected by rememberUpdatedState(onImageSelected)

    // 카메라 앱이 떠 있는 동안 우리 프로세스가 죽을 수 있다. remember로 두면 복귀했을 때
    // uri가 null이라 촬영 결과가 조용히 버려지므로 rememberSaveable로 저장한다.
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val deliver: (Uri) -> Unit = { uri ->
        scope.launch {
            // 디코딩/압축을 메인 스레드에서 하면 큰 사진에서 ANR이 난다.
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.readScaledJpeg(uri) }
                    .onFailure { Napier.e("프로필 이미지 처리 실패: ${it.message}", it) }
                    .getOrNull()
            }
            bytes?.let { currentOnImageSelected(it) }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let(deliver) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri?.let(Uri::parse)
        pendingCameraUri = null
        if (success && uri != null) deliver(uri) else Napier.d("촬영 취소 또는 저장 실패")
    }

    return remember {
        object : ProfileImageLauncher {
            override fun launchAlbum() {
                galleryLauncher.launch("image/*")
            }

            override fun launchCamera() {
                val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
                imagesDir.listFiles()?.forEach { it.delete() }   // 이전 임시 촬영본 정리

                // 고정 파일명을 쓰면 촬영에 실패했을 때 직전 사진을 다시 읽어버린다.
                val file = File(imagesDir, "capture_${System.currentTimeMillis()}.jpg")
                    .apply { createNewFile() }   // 일부 카메라 앱은 대상 파일이 없으면 저장에 실패한다
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                pendingCameraUri = uri.toString()
                cameraLauncher.launch(uri)
            }
        }
    }
}

/** 원본을 [MAX_IMAGE_DIMENSION] 이내로 줄이고 회전을 바로잡아 JPEG 바이트로 만든다. */
private fun Context.readScaledJpeg(uri: Uri): ByteArray {
    // 1) 크기만 먼저 읽어 축소 배율을 정한다 (원본 전체를 메모리에 올리지 않기 위함).
    //    inJustDecodeBounds 디코딩은 성공해도 항상 null을 반환하므로, 성공 여부는
    //    반환값이 아니라 bounds에 채워진 크기로 판단해야 한다.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    check(bounds.outWidth > 0 && bounds.outHeight > 0) {
        "이미지 크기를 읽을 수 없습니다(손상되었거나 빈 파일): $uri"
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
    }
    val decoded = openStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("이미지를 디코딩할 수 없습니다: $uri")

    // 2) 카메라 사진은 픽셀은 그대로 두고 EXIF에 회전 정보만 담는 경우가 많다.
    //    보정하지 않으면 업로드된 프로필이 옆으로 누워 보인다.
    val upright = decoded.applyExifRotation(this, uri)

    return try {
        ByteArrayOutputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    } finally {
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
    }
}

private fun Context.openStream(uri: Uri): InputStream =
    contentResolver.openInputStream(uri) ?: error("이미지를 열 수 없습니다: $uri")

private fun calculateInSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (width / sample > MAX_IMAGE_DIMENSION || height / sample > MAX_IMAGE_DIMENSION) {
        sample *= 2
    }
    return sample
}

private fun Bitmap.applyExifRotation(context: Context, uri: Uri): Bitmap {
    val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
        ExifInterface(input).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    } ?: ExifInterface.ORIENTATION_NORMAL

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}