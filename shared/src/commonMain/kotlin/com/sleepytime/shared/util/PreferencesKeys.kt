package com.sleepytime.shared.util

object PreferencesKeys {
    object Auth {
        const val ACCESS_TOKEN = "auth_access_token"
        const val REFRESH_TOKEN = "auth_refresh_token"
        const val SOCIAL_PROVIDER = "auth_social_provider"
    }
    object SleepMusic {
        const val LAST_PLAYED_MUSIC_NAME = "last_played_sleep_music_name"
        const val LAST_PLAYED_POSITION_SECONDS = "last_played_sleep_music_position"
        const val WAS_PLAYING = "last_sleep_music_was_playing"
    }
    object Alarm {
        const val HOUR = "alarm_hour"
        const val MINUTE = "alarm_minute"
        const val ENABLED = "alarm_enabled"
        const val ALARM_NAME = "alarm_music_name"
        const val IS_TIMER = "alarm_is_timer"
        const val TIMER_MINUTES = "alarm_timer_minutes"
        const val VIBRATION = "alarm_vibration"
        const val SMART_ALARM = "alarm_smart_alarm"
        const val SMART_ALARM_RANGE = "alarm_smart_alarm_range"
        const val VOLUME = "alarm_volume"
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
        const val PERMISSION_ONBOARDING_DONE = "app_permission_onboarding_done"
    }

    object Settings {
        const val KEY_PUSH_ENABLED = "is_notification_enabled"
        const val KEY_REMINDER_ENABLED = "is_sleep_reminder_enabled"
        const val KEY_WEEKLY_REPORT_ENABLED = "is_weekly_report_enabled"
        const val KEY_UPDATE_ENABLED  = "is_update_enabled"
        const val KEY_REMINDER_HOUR = "reminder_hour"
        const val KEY_REMINDER_MINUTE = "reminder_minute"
        const val KEY_REPORT_DELIVERY_METHOD = "report_delivery_method"
    }
}