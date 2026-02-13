package com.example.sleep

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes

data class SleepMusicItem(
    val title: String,
    val description: String,
    @DrawableRes val imageRes: Int,
    @RawRes val musicRes: Int,
    val duration: Long,
    val isLoop: Boolean,
    val isTimer: Boolean,
)