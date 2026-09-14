package com.sleepytime.shared.platform

import android.content.Context
import com.russhwolf.settings.ObservableSettings
import com.sleepytime.shared.util.PreferencesKeys
import com.sleepytime.shared.util.PreferencesKeys.Session.KEY_DURATION
import com.sleepytime.shared.util.PreferencesKeys.Session.KEY_MUSIC_TITLE
import com.sleepytime.shared.util.PreferencesKeys.Session.KEY_SESSION_ID
import com.sleepytime.shared.util.PreferencesKeys.Session.KEY_START_TIME

class ActiveSessionStore(
    private val settings: ObservableSettings
) {
    fun save(
        sessionId: String,
        startTimeMillis: Long,
        duration: Int,
        musicTitle: String?
    ) {
        settings.putString(KEY_SESSION_ID, sessionId)
        settings.putLong(KEY_START_TIME, startTimeMillis)
        settings.putInt(KEY_DURATION, duration)
        musicTitle?.let { settings.putString(KEY_MUSIC_TITLE, it) }
            ?: settings.remove(KEY_MUSIC_TITLE)
    }
    fun clear() {
        settings.remove(KEY_SESSION_ID)
        settings.remove(KEY_START_TIME)
        settings.remove(KEY_DURATION)
        settings.remove(KEY_MUSIC_TITLE)
    }
    fun getActiveSessionId(): String? =
        if (settings.hasKey(KEY_SESSION_ID)) settings.getString(KEY_SESSION_ID, "") else null
    fun getStartTimeMillis(): Long = settings.getLong(KEY_START_TIME, 0L)
    fun getDuration(): Int = settings.getInt(KEY_DURATION, 0)
    fun getMusicTitle(): String? = settings.getStringOrNull(KEY_MUSIC_TITLE)
}