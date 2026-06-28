package com.sleepytime.shared.data.local.repository

import com.sleepytime.shared.data.local.dao.SleepMusicDao
import com.sleepytime.shared.data.local.mapper.toDomain
import com.sleepytime.shared.data.local.mapper.toSleepMusicDomain
import com.sleepytime.shared.data.local.mapper.toSleepMusicDomainList
import com.sleepytime.shared.domain.repository.SleepMusicRepository
import com.sleepytime.shared.domain.model.SleepMusic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.collections.map

class SleepMusicRepositoryImpl(
    private val sleepMusicDao: SleepMusicDao,
) : SleepMusicRepository {
    override suspend fun getAllMusic(): Flow<List<SleepMusic>> =
        flowOf(sleepMusicDao.getAllMusic()).map {
            it.toSleepMusicDomainList()
        }
    override suspend fun getMusicByMusicName(musicName: String): SleepMusic? =
        sleepMusicDao.getMusicByName(musicName)?.toSleepMusicDomain()
}
