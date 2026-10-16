package com.soundsleeper.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import com.soundsleeper.app.util.PreferencesKeys

@UnstableApi
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("sleepytime_prefs", Context.MODE_PRIVATE)
        val isReminderEnabled = prefs.getBoolean(PreferencesKeys.Settings.KEY_REMINDER_ENABLED, true)
        if (!isReminderEnabled) return

        val hour = prefs.getInt(PreferencesKeys.Settings.KEY_REMINDER_HOUR, 23)
        val minute = prefs.getInt(PreferencesKeys.Settings.KEY_REMINDER_MINUTE, 0)
        AndroidBedtimeReminderScheduler(context.applicationContext).schedule(hour, minute)
    }
}