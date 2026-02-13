package com.example.sleep.domain.repository

import com.example.sleep.domain.model.SleepStage
import kotlinx.coroutines.flow.Flow

interface SleepStageRepository {
    suspend fun insertSleepStage(stage: SleepStage, sessionId: Long, startTime: Long, endTime: Long)

    suspend fun insertSleepStages(stages: List<SleepStage>, sessionId: Long)

    fun getSleepStagesForSessionFlow(sessionId: Long): Flow<List<SleepStage>>

    suspend fun getSleepStagesForSession(sessionId: Long): List<SleepStage>
}
