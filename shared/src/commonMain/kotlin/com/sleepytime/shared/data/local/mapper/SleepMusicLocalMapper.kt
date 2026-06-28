package com.sleepytime.shared.data.local.mapper

import com.sleepytime.shared.data.local.SleepMusicEntity
import com.sleepytime.shared.domain.model.SleepMusic

fun SleepMusicEntity.toSleepMusicDomain(): SleepMusic {
    return SleepMusic(
        musicName = musicName,
        title = title,
        category = category,
        imageName = imageName,
        duration = duration,
        volume = volume,

        isFavorite = isFavorite,
        isLooping = isLooping,
        isPremium = isPremium
    )
}
fun List<SleepMusicEntity>.toSleepMusicDomainList(): List<SleepMusic> {
    return this.map { entity ->
        SleepMusic(
            musicName = entity.musicName,
            title = entity.title,
            category = entity.category,
            imageName = entity.imageName,
            duration = entity.duration,
            volume = entity.volume,
            isFavorite = entity.isFavorite,
            isLooping = entity.isLooping,
            isPremium = entity.isPremium
        )
    }
}
fun SleepMusic.toEntity(): SleepMusicEntity {
    return SleepMusicEntity(
        musicName = musicName,
        title = title,
        category = category,
        imageName = imageName,
        duration = duration,
        volume = volume,

        isFavorite = isFavorite,
        isLooping = isLooping,
        isPremium = isPremium
    )
}