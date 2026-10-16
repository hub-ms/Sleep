package com.soundsleeper.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * [Intent.ACTION_BATTERY_CHANGED] 를 구독해 충전 여부를 따라간다.
 *
 * 이 브로드캐스트는 sticky 라서 registerReceiver 가 "현재 값"이 담긴 Intent 를 바로 돌려준다.
 * 덕분에 초기값을 따로 조회할 필요가 없고, 화면을 보고 있는 중에 충전기를 꽂거나 빼면
 * 곧바로 반영된다.
 */
@Composable
actual fun rememberIsCharging(): Boolean {
    val context = LocalContext.current
    // 초기값을 true 로 둬서, 첫 프레임에 경고가 번쩍 떴다가 사라지는 일이 없게 한다.
    var isCharging by remember { mutableStateOf(true) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let { isCharging = it.isPluggedIn() }
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sticky?.let { isCharging = it.isPluggedIn() }

        onDispose { context.unregisterReceiver(receiver) }
    }

    return isCharging
}

/** AC·USB·무선 중 무엇이든 연결돼 있으면 0이 아닌 값이 온다. */
private fun Intent.isPluggedIn(): Boolean =
    getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
