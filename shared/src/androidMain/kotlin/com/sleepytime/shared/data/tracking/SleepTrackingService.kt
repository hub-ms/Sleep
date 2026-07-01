package com.sleepytime.shared.data.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import com.sleepytime.shared.MainActivity
import com.sleepytime.shared.R
import com.sleepytime.shared.platform.AndroidTrackingManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@UnstableApi
class SleepTrackingService : Service(), KoinComponent {
    private val trackingManager: AndroidTrackingManager by inject()
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    companion object {
        const val ACTION_START = "com.sleepytime.app.ACTION_START_TRACKING"
        const val ACTION_DISCARD = "com.sleepytime.app.ACTION_DISCARD_TRACKING"
        const val ACTION_FINISH = "com.sleepytime.app.ACTION_FINISH_TRACKING"

        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_MUSIC_TITLE = "extra_music_title"

        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "sleep_tracking_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        trackingManager.attachCallbacks(
            onNotificationUpdate = { text ->
                notificationManager.notify(NOTIFICATION_ID, createNotification(text))
            },
            onRequestStopForeground = {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent == null) {
            if (trackingManager.trackingState.value.isTracking) {
                startForeground(NOTIFICATION_ID, createNotification("수면 측정 중.."))
            }
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification("수면 측정 중.."))
                if (!trackingManager.trackingState.value.isTracking) {
                    val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_STICKY
                    val duration = intent.getIntExtra(EXTRA_DURATION, 480)
                    val musicTitle = intent.getStringExtra(EXTRA_MUSIC_TITLE)
                    trackingManager.start(sessionId, duration, musicTitle)
                }
            }
            ACTION_FINISH -> {
                trackingManager.finish()
            }
            ACTION_DISCARD -> {
                trackingManager.discard()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "수면 측정",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "수면 측정 진행 상태"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    private fun createNotification(contentText: String): Notification {
        val openPending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val finishPending = PendingIntent.getService(
            this,
            1,
            Intent(this, SleepTrackingService::class.java).apply {
                action = ACTION_FINISH
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("수면 측정")
            .setContentText(contentText)
            .addAction(R.drawable.ic_close, "수면 종료", finishPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setSmallIcon(R.drawable.ic_sleep)
            .setContentIntent(openPending)
            .build()
    }
}