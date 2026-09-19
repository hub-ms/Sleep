package com.sleepytime.shared.platform

interface BedtimeReminderScheduler {
    fun schedule(hour: Int, minute: Int)
    fun cancel()
}
