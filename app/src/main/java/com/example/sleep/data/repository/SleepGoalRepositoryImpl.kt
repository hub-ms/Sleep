package com.example.sleep.data.repository

import com.example.sleep.data.local.dao.SleepGoalDao
import com.example.sleep.data.mapper.SleepGoalMapper
import com.example.sleep.domain.model.SleepGoal
import com.example.sleep.domain.repository.SleepGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SleepGoalRepositoryImpl @Inject constructor(
    private val sleepGoalDao: SleepGoalDao
) : SleepGoalRepository {
    override suspend fun insertSleepGoal(goal: SleepGoal) {
        val entity = SleepGoalMapper.fromDomain(goal)
        sleepGoalDao.insertSleepGoal(entity)
    }

    override fun getSleepGoalFlow(userId: Long): Flow<SleepGoal?> {
        return sleepGoalDao.getSleepGoalFlow(userId).map {
            it?.let { entity -> SleepGoalMapper.toDomain(entity) }
        }
    }

    override suspend fun getSleepGoal(userId: Long): SleepGoal? {
        return sleepGoalDao.getSleepGoal(userId)?.let {
            SleepGoalMapper.toDomain(it)
        }
    }
}
