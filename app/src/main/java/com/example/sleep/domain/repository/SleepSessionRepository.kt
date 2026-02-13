package com.example.sleep.domain.repository

import com.example.sleep.domain.model.SleepSession
import kotlinx.coroutines.flow.Flow

interface SleepSessionRepository {
    suspend fun insertSleepSession(session: SleepSession): Long

    suspend fun updateSleepSession(session: SleepSession)

    fun getSleepSessionFlow(id: Long): Flow<SleepSession?>

    suspend fun getSleepSession(id: Long): SleepSession?

    fun getAllSleepSessionsFlow(): Flow<List<SleepSession>>

    suspend fun getAllSleepSessions(): List<SleepSession>
}
