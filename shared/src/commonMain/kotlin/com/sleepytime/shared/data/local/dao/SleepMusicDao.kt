package com.sleepytime.shared.data.local.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.sleepytime.shared.data.local.SleepMusicEntity
import com.sleepytime.shared.data.local.generated.SleepDatabase
import com.sleepytime.shared.data.local.mapper.toDomain
import com.sleepytime.shared.data.local.mapper.toEntity
import com.sleepytime.shared.domain.model.SleepMusic
import com.sleepytime.shared.enum_.MusicCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class SleepMusicDao(db: SleepDatabase) {

    private val queries = db.sleepMusicEntityQueries

    fun insertAll(musicList: List<SleepMusicEntity>) {
        queries.transaction {
            musicList.forEach { music ->
                queries.insertOrIgnoreMusic(music)
            }
        }
    }
    fun getAllMusicFlow(): Flow<List<SleepMusicEntity>> =
        queries.getAllMusic().asFlow().mapToList(Dispatchers.IO)

    fun getMusicByCategoryFlow(category: MusicCategory): Flow<List<SleepMusicEntity>> =
        queries.getMusicByCategory(category).asFlow().mapToList(Dispatchers.IO)

    fun getFavoriteMusicFlow(): Flow<List<SleepMusicEntity>> =
        queries.getFavoriteMusic().asFlow().mapToList(Dispatchers.IO)
    fun toggleFavorite(musicName: String) {
        queries.toggleFavorite(musicName)
    }
    fun getMusicByName(musicName: String): SleepMusicEntity? {
        return queries.getMusicByName(musicName)
            .executeAsOneOrNull()
    }
}