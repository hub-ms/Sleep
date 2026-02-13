package com.example.sleep.core.util

object DateTimeUtils {
    fun formatSleepDuration(duration: Long): String {
        val hours=duration/3600000
        return "${hours}시간"
    }
}