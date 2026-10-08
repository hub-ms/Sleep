package com.soundsleeper.app.data.local.repository

import com.soundsleeper.app.data.local.dao.SleepMusicDao
import com.soundsleeper.app.data.local.mapper.toEntity
import com.soundsleeper.app.data.local.mapper.toSleepMusicDomain
import com.soundsleeper.app.data.local.mapper.toSleepMusicDomainList
import com.soundsleeper.app.domain.repository.SleepMusicRepository
import com.soundsleeper.app.domain.model.SleepMusic
import com.soundsleeper.app.enum_.MusicCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SleepMusicRepositoryImpl(
    private val sleepMusicDao: SleepMusicDao,
) : SleepMusicRepository {

    override fun getAllMusic(): Flow<List<SleepMusic>> =
        sleepMusicDao.getAllMusicFlow().map { it.toSleepMusicDomainList() }

    override suspend fun getMusicByMusicName(musicName: String): SleepMusic? =
        sleepMusicDao.getMusicByName(musicName)?.toSleepMusicDomain()

    override fun getMusicByCategory(category: MusicCategory): Flow<List<SleepMusic>> =
        sleepMusicDao.getMusicByCategoryFlow(category).map { it.toSleepMusicDomainList() }

    override fun getFavoriteMusic(): Flow<List<SleepMusic>> =
        sleepMusicDao.getFavoriteMusicFlow().map { it.toSleepMusicDomainList() }

    override suspend fun toggleFavorite(musicName: String) {
        sleepMusicDao.toggleFavorite(musicName)
    }

    override suspend fun insertAll(musicList: List<SleepMusic>) {
        sleepMusicDao.insertAll(musicList.map { it.toEntity() })
    }
}
