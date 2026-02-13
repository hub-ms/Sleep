package com.example.sleep.domain.repository

import com.example.sleep.domain.model.SleepScore
import kotlinx.coroutines.flow.Flow

interface SleepScoreRepository {
    suspend fun insertSleepScore(score: SleepScore)

    fun getSleepScoreFlow(sessionId: Long): Flow<SleepScore?>

    suspend fun getSleepScore(sessionId: Long): SleepScore?
}
