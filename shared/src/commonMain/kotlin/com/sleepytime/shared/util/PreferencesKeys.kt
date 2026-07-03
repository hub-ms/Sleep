package com.sleepytime.shared.util

object PreferencesKeys {
    object Auth {
        const val ACCESS_TOKEN = "auth_access_token"
        const val REFRESH_TOKEN = "auth_refresh_token"
        const val SOCIAL_PROVIDER = "auth_social_provider"
    }

    object Alarm {
        const val HOUR = "alarm_hour"
        const val MINUTE = "alarm_minute"
        const val ENABLED = "alarm_enabled"
        const val MUSIC_NAME = "alarm_music_name"
        const val IS_TIMER = "alarm_is_timer"
        const val TIMER_MINUTES = "alarm_timer_minutes"
    }

    object Session {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_START_TIME = "start_time"
        const val KEY_DURATION = "duration"
        const val KEY_MUSIC_TITLE = "music_title"
    }

    object App {
        const val FIRST_LAUNCH = "app_first_launch"
        const val UNIQUE_GUEST_ID = "app_unique_guest_id"
    }
}