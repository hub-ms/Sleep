package com.example.sleep.data.repository

import com.example.sleep.data.local.dao.SleepInterruptionDao
import com.example.sleep.data.mapper.SleepInterruptionMapper
import com.example.sleep.domain.model.SleepInterruption
import com.example.sleep.domain.repository.SleepInterruptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SleepInterruptionRepositoryImpl @Inject constructor(
    private val sleepInterruptionDao: SleepInterruptionDao
) : SleepInterruptionRepository {
    override suspend fun insertSleepInterruption(interruption: SleepInterruption) {
        val entity = SleepInterruptionMapper.fromDomain(interruption)
        sleepInterruptionDao.insertSleepInterruption(entity)
    }

    override suspend fun insertSleepInterruptionAll(data: List<SleepInterruption>) {
        val entities = data.map { SleepInterruptionMapper.fromDomain(it) }
        sleepInterruptionDao.insertSleepInterruptionAll(entities)
    }

    override fun getBySleepInterruptionSessionFlow(sessionId: Long): Flow<List<SleepInterruption>> {
        return sleepInterruptionDao.getBySleepInterruptionSessionFlow(sessionId).map {
            it.map { entity -> SleepInterruptionMapper.toDomain(entity) }
        }
    }

    override suspend fun getBySleepInterruptionSession(sessionId: Long): List<SleepInterruption> {
        return sleepInterruptionDao.getBySleepInterruptionSession(sessionId).map {
            SleepInterruptionMapper.toDomain(it)
        }
    }

    override suspend fun getSleepInterruptionTotalDuration(sessionId: Long): Long? {
        return sleepInterruptionDao.getSleepInterruptionTotalDuration(sessionId)
    }
}
