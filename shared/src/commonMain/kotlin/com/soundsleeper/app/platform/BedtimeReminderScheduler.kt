package com.soundsleeper.app.platform

interface BedtimeReminderScheduler {
    fun schedule(hour: Int, minute: Int)
    fun cancel()
}
