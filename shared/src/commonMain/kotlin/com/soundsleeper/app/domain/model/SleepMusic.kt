package com.soundsleeper.app.domain.model

import com.soundsleeper.app.enum_.MusicCategory

data class SleepMusic(
    val musicName: String,
    val title: String,
    val category: MusicCategory,
    val imageName: String,
    val duration: Long,
    val volume: Float,

    val isFavorite: Boolean = false,
    val isLooping: Boolean,
    val isPremium: Boolean
)

