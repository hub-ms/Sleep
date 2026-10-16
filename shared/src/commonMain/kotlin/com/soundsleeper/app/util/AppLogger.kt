package com.soundsleeper.app.util

import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog

object AppLogger {
    private var isPlanted = false

    fun plant(isDebug: Boolean) {
        if (isPlanted) return
        if (isDebug) Napier.base(DebugAntilog())
        isPlanted = true
    }
}
