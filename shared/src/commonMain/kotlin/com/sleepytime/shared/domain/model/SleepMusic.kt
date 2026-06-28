package com.sleepytime.shared.domain.model

data class SleepMusic(
    val musicName: String,
    val title: String,
    val category: String,
    val imageName: String,
    val duration: Long,
    val volume: Float,

    val isFavorite: Boolean,
    val isLooping: Boolean,
    val isPremium: Boolean
)

