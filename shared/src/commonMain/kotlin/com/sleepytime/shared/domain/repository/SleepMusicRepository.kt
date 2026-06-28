package com.sleepytime.shared.domain.repository

import com.sleepytime.shared.domain.model.SleepMusic
import kotlinx.coroutines.flow.Flow

// SleepMusicRepository.kt
interface SleepMusicRepository {
    suspend fun getAllMusic(): Flow<List<SleepMusic>>

    suspend fun getMusicByMusicName(musicName: String): SleepMusic?
}
