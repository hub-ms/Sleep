package com.sleepytime.shared.data.remote.mapper

import com.sleepytime.shared.data.remote.dto.request.SleepMusicRequest
import com.sleepytime.shared.data.remote.dto.response.SleepMusicResponse
import com.sleepytime.shared.domain.model.SleepMusic

fun SleepMusicResponse.toDomain(): SleepMusic = SleepMusic(
    musicName = musicName,
    isFavorite = isFavorite,

    title = "알 수 없는 음악",
    category = "ALL",
    imageName = "",
    duration = 1800,
    volume = 0.8f,
    isLooping = true,
    isPremium = false
)
fun SleepMusic.toRequest(): SleepMusicRequest = SleepMusicRequest(
    musicName = this.musicName,
    isFavorite = this.isFavorite
)