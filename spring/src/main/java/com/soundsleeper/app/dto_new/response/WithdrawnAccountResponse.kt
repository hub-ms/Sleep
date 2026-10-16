package com.soundsleeper.app.dto_new.response

import java.time.LocalDateTime

data class WithdrawnAccountResponse(
    val message: String,
    val isRestorable: Boolean,
    val restorableUntil: LocalDateTime?,
)
