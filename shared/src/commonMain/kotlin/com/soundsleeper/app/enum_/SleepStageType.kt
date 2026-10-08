package com.soundsleeper.app.enum_

import kotlinx.serialization.Serializable

@Serializable
enum class SleepStageType {
    AWAKE, LIGHT, DEEP, REM,
}