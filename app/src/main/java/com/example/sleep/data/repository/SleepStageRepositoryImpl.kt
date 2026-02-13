package com.example.sleep.data.repository

import com.example.sleep.data.local.dao.SleepStageDao
import com.example.sleep.data.mapper.SleepStageMapper
import com.example.sleep.domain.model.SleepStage
import com.example.sleep.domain.repository.SleepStageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SleepStageRepositoryImpl @Inject constructor(
    private val sleepStageDao: SleepStageDao
) : SleepStageRepository {
    override suspend fun insertSleepStage(stage: SleepStage, sessionId: Long, startTime: Long, endTime: Long) {
        val entity = SleepStageMapper.fromDomain(stage, sessionId, startTime, endTime)
        sleepStageDao.insertSleepStage(entity)
    }

    override suspend fun insertSleepStages(stages: List<SleepStage>, sessionId: Long) {
        // This is a simplified implementation. You might need a more complex logic
        // to associate start and end times with each stage.
        var lastTime = 0L
        val entities = stages.map {
            val now = System.currentTimeMillis()
            val entity = SleepStageMapper.fromDomain(it, sessionId, lastTime, now)
            lastTime = now
            entity
        }
        sleepStageDao.insertSleepStages(entities)
    }

    override fun getSleepStagesForSessionFlow(sessionId: Long): Flow<List<SleepStage>> {
        return sleepStageDao.getSleepStagesForSessionFlow(sessionId).map {
            it.map { entity -> SleepStageMapper.toDomain(entity) }
        }
    }

    override suspend fun getSleepStagesForSession(sessionId: Long): List<SleepStage> {
        return sleepStageDao.getSleepStagesForSession(sessionId).map {
            SleepStageMapper.toDomain(it)
        }
    }
}
