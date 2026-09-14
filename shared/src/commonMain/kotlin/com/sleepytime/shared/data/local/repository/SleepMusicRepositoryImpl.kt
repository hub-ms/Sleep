package com.sleepytime.shared.data.local.repository

import com.sleepytime.shared.data.local.dao.SleepMusicDao
import com.sleepytime.shared.data.local.mapper.toEntity
import com.sleepytime.shared.data.local.mapper.toSleepMusicDomain
import com.sleepytime.shared.data.local.mapper.toSleepMusicDomainList
import com.sleepytime.shared.domain.repository.SleepMusicRepository
import com.sleepytime.shared.domain.model.SleepMusic
import com.sleepytime.shared.enum_.MusicCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SleepMusicRepositoryImpl(
    private val sleepMusicDao: SleepMusicDao,
) : SleepMusicRepository {

    override suspend fun getAllMusic(): Flow<List<SleepMusic>> =
        sleepMusicDao.getAllMusicFlow().map { it.toSleepMusicDomainList() }

    override suspend fun getMusicByMusicName(musicName: String): SleepMusic? =
        sleepMusicDao.getMusicByName(musicName)?.toSleepMusicDomain()

    override suspend fun getMusicByCategory(category: MusicCategory): Flow<List<SleepMusic>> =
        sleepMusicDao.getMusicByCategoryFlow(category).map { it.toSleepMusicDomainList() }

    override suspend fun getFavoriteMusic(): Flow<List<SleepMusic>> =
        sleepMusicDao.getFavoriteMusicFlow().map { it.toSleepMusicDomainList() }

    override suspend fun toggleFavorite(musicName: String) {
        sleepMusicDao.toggleFavorite(musicName)
    }

    override suspend fun insertAll(musicList: List<SleepMusic>) {
        sleepMusicDao.insertAll(musicList.map { it.toEntity() })
    }
}
