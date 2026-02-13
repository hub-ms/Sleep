package com.example.sleep.core.util

import android.Manifest
import android.app.Activity
import androidx.core.app.ActivityCompat

object PermissionsUtils {
    fun requestSensorPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(activity,arrayOf(Manifest.permission.BODY_SENSORS),1)
    }
}