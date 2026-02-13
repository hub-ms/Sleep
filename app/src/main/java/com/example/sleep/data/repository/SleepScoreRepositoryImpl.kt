package com.example.sleep.data.repository

import com.example.sleep.data.local.dao.SleepScoreDao
import com.example.sleep.data.mapper.SleepScoreMapper
import com.example.sleep.domain.model.SleepScore
import com.example.sleep.domain.repository.SleepScoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SleepScoreRepositoryImpl @Inject constructor(
    private val sleepScoreDao: SleepScoreDao
) : SleepScoreRepository {
    override suspend fun insertSleepScore(score: SleepScore) {
        val entity = SleepScoreMapper.fromDomain(score)
        sleepScoreDao.insertSleepScore(entity)
    }

    override fun getSleepScoreFlow(sessionId: Long): Flow<SleepScore?> {
        return sleepScoreDao.getSleepScoreForSessionFlow(sessionId).map {
            it?.let { entity -> SleepScoreMapper.toDomain(entity) }
        }
    }

    override suspend fun getSleepScore(sessionId: Long): SleepScore? {
        return sleepScoreDao.getSleepScoreForSession(sessionId)?.let {
            SleepScoreMapper.toDomain(it)
        }
    }
}
