package com.example.sleep.domain.repository

import com.example.sleep.domain.model.SleepInterruption
import kotlinx.coroutines.flow.Flow

interface SleepInterruptionRepository {
    suspend fun insertSleepInterruption(interruption: SleepInterruption)

    suspend fun insertSleepInterruptionAll(data: List<SleepInterruption>)

    fun getBySleepInterruptionSessionFlow(sessionId: Long): Flow<List<SleepInterruption>>

    suspend fun getBySleepInterruptionSession(sessionId: Long): List<SleepInterruption>

    suspend fun getSleepInterruptionTotalDuration(sessionId: Long): Long?
}
