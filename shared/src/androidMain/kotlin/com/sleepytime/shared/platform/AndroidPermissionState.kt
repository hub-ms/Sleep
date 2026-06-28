package com.sleepytime.shared.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object AndroidContextProvider {
    lateinit var context: Context
}

actual fun checkPermissionState(): PermissionState {
    val context = AndroidContextProvider.context  // Application Context

    fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED

    return PermissionState(
        activityRecognition = isGranted(Manifest.permission.ACTIVITY_RECOGNITION),
        audio = isGranted(Manifest.permission.RECORD_AUDIO),
        location = isGranted(Manifest.permission.ACCESS_FINE_LOCATION),
        notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isGranted(Manifest.permission.POST_NOTIFICATIONS)
        } else true
    )
}