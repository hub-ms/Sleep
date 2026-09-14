package com.sleepytime.shared.data.remote.mapper

import com.sleepytime.shared.data.remote.dto.request.SleepMusicRequest
import com.sleepytime.shared.data.remote.dto.response.SleepMusicResponse
import com.sleepytime.shared.domain.model.SleepMusic
import com.sleepytime.shared.enum_.MusicCategory

fun SleepMusicResponse.toDomain(): SleepMusic = SleepMusic(
    musicName = musicName,
    isFavorite = isFavorite,

    title = "",
    category = MusicCategory.NATURE,
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