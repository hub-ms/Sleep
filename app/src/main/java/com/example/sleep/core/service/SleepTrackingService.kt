package com.example.sleep.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.sleep.R

class SleepTrackingService : Service(), SensorEventListener {

    private lateinit var sensorManagerWrapper: SensorManagerWrapper

    override fun onCreate() {
        super.onCreate()
        sensorManagerWrapper = SensorManagerWrapper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                startForeground(NOTIFICATION_ID, createNotification())
                sensorManagerWrapper.startSensors(this)
            }
            ACTION_STOP_TRACKING -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManagerWrapper.stopSensors(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // Handle sensor data
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes
    }

    private fun createNotification(): Notification {
        val channelId = "sleep_tracking_channel"
        val channelName = "Sleep Tracking"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sleep Tracking")
            .setContentText("Tracking your sleep...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with a real icon
            .build()
    }

    companion object {
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
        private const val NOTIFICATION_ID = 1
    }
}
