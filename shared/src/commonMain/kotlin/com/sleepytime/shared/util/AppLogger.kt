package com.sleepytime.shared.util

import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog

object AppLogger {
    private var isPlanted = false

    fun plant() {
        if (isPlanted) return
        Napier.base(DebugAntilog())
        isPlanted = true
    }
}