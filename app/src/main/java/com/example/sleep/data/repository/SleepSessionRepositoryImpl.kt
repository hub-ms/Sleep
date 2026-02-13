package com.example.sleep.data.repository

import com.example.sleep.data.local.dao.SleepSessionDao
import com.example.sleep.data.mapper.SleepSessionMapper
import com.example.sleep.domain.model.SleepSession
import com.example.sleep.domain.repository.SleepSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SleepSessionRepositoryImpl @Inject constructor(
    private val sleepSessionDao: SleepSessionDao
) : SleepSessionRepository {
    override suspend fun insertSleepSession(session: SleepSession): Long {
        val entity = SleepSessionMapper.fromDomain(session)
        return sleepSessionDao.insertSleepSession(entity)
    }

    override suspend fun updateSleepSession(session: SleepSession) {
        val entity = SleepSessionMapper.fromDomain(session)
        sleepSessionDao.updateSleepSession(entity)
    }

    override fun getSleepSessionFlow(id: Long): Flow<SleepSession?> {
        return sleepSessionDao.getSleepSessionFlow(id).map {
            it?.let { entity -> SleepSessionMapper.toDomain(entity) }
        }
    }

    override suspend fun getSleepSession(id: Long): SleepSession? {
        return sleepSessionDao.getSleepSession(id)?.let {
            SleepSessionMapper.toDomain(it)
        }
    }

    override fun getAllSleepSessionsFlow(): Flow<List<SleepSession>> {
        return sleepSessionDao.getAllSleepSessionsFlow().map {
            it.map { entity -> SleepSessionMapper.toDomain(entity) }
        }
    }

    override suspend fun getAllSleepSessions(): List<SleepSession> {
        return sleepSessionDao.getAllSleepSessions().map {
            SleepSessionMapper.toDomain(it)
        }
    }
}
