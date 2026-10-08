package com.soundsleeper.app.domain.repository

import com.soundsleeper.app.domain.model.SleepMusic
import com.soundsleeper.app.enum_.MusicCategory
import kotlinx.coroutines.flow.Flow

interface SleepMusicRepository {
    fun getAllMusic(): Flow<List<SleepMusic>>

    suspend fun getMusicByMusicName(musicName: String): SleepMusic?
    fun getMusicByCategory(category: MusicCategory): Flow<List<SleepMusic>>

    fun getFavoriteMusic(): Flow<List<SleepMusic>>

    suspend fun toggleFavorite(musicName: String)

    suspend fun insertAll(musicList: List<SleepMusic>)
}
