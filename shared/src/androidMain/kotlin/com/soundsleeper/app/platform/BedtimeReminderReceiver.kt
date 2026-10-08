package com.soundsleeper.app.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.UnstableApi
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import com.russhwolf.settings.ExperimentalSettingsApi
import com.soundsleeper.app.MainActivity
import com.soundsleeper.app.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.time.ExperimentalTime

@UnstableApi
@OptIn(
    ExperimentalTime::class,
    ExperimentalMaterial3Api::class,
    InternalVoyagerApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalSettingsApi::class
)
class BedtimeReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        createNotificationChannel(context)

        val openPending = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // setSmallIcon이 없으면 notify()가 IllegalArgumentException을 던진다.
            .setSmallIcon(R.drawable.ic_sleep)
            .setAutoCancel(true)
            .setContentTitle("취침 시각이에요")
            .setContentText("수면 준비를 시작해보세요")
            .setContentIntent(openPending)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "취침 시각 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "설정한 취침 시각에 맞춰 알림을 보내드립니다"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "bedtime_reminder_channel"
    }
}