package com.sleepytime.shared.domain.repository

import com.sleepytime.shared.domain.model.SleepMusic
import com.sleepytime.shared.enum_.MusicCategory
import kotlinx.coroutines.flow.Flow

interface SleepMusicRepository {
    suspend fun getAllMusic(): Flow<List<SleepMusic>>

    suspend fun getMusicByMusicName(musicName: String): SleepMusic?
    suspend fun getMusicByCategory(category: MusicCategory): Flow<List<SleepMusic>>

    suspend fun getFavoriteMusic(): Flow<List<SleepMusic>>

    suspend fun toggleFavorite(musicName: String)

    suspend fun insertAll(musicList: List<SleepMusic>)
}
