package com.sleepytime.shared.data.local.dao

import com.sleepytime.shared.data.local.SleepMusicEntity
import com.sleepytime.shared.data.local.generated.SleepDatabase
import com.sleepytime.shared.data.local.mapper.toDomain
import com.sleepytime.shared.data.local.mapper.toEntity
import com.sleepytime.shared.domain.model.SleepMusic

class SleepMusicDao(db: SleepDatabase) {

    private val queries = db.sleepMusicEntityQueries

    fun insertAll(musicList: List<SleepMusicEntity>) {
        queries.transaction {
            musicList.forEach { music ->
                queries.insertOrIgnoreMusic(music)
            }
        }
    }
    fun getAllMusic(): List<SleepMusicEntity> {
        return queries.getAllMusic()
            .executeAsList()
    }
    fun getMusicByCategory(category: String): List<SleepMusicEntity> {
        return queries.getMusicByCategory(category)
            .executeAsList()
    }
    fun getFavoriteMusic(): List<SleepMusicEntity> {
        return queries.getFavoriteMusic()
            .executeAsList()
    }
    fun toggleFavorite(musicName: String) {
        queries.toggleFavorite(musicName)
    }
    fun getMusicByName(musicName: String): SleepMusicEntity? {
        return queries.getMusicByName(musicName)
            .executeAsOneOrNull()
    }
}