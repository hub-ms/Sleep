package com.somniotune.app.domain.model

import com.somniotune.app.enum_.MusicCategory

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

