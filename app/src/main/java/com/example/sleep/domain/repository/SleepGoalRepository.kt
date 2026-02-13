package com.example.sleep.domain.repository

import com.example.sleep.domain.model.SleepGoal
import kotlinx.coroutines.flow.Flow

interface SleepGoalRepository {
    suspend fun insertSleepGoal(goal: SleepGoal)

    fun getSleepGoalFlow(userId: Long): Flow<SleepGoal?>

    suspend fun getSleepGoal(userId: Long): SleepGoal?
}
