package com.sleepytime.shared.domain.repository

import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepAnalysis
import com.sleepytime.shared.domain.model.SleepSession
import kotlinx.datetime.LocalDate

interface SleepSessionRepository {
    suspend fun initializeModel(): Result<Unit>
    suspend fun analyzeSleepData(
        sensorData: List<FloatArray>,
        environmentFeature: EnvironmentFeature?
    ): Result<SleepAnalysis>
    suspend fun analyzeSleepSession(
        timestamps: List<Long>,
        environmentFeatures: List<EnvironmentFeature> = emptyList(),
        sessionId: String
    ): Result<SleepSession>
    fun closeModel()
    fun isReady(): Boolean
    suspend fun insertSession(session: SleepSession)
    suspend fun getSessionByDate(date: LocalDate): SleepSession?
    suspend fun getSessionByDateRange(
        fromEpochMs: LocalDate,
        toEpochMs: LocalDate
    ): List<SleepSession>
    suspend fun getSessionDatesByMonth(
        year: String,
        month: String
    ): List<LocalDate>
    suspend fun getLatestSession(): SleepSession?
    suspend fun deleteSession(sessionId: String)
    suspend fun updateEnvironmentContext(feature: EnvironmentFeature): Result<Unit>
    suspend fun getRecentSessions(days: Int): List<SleepSession>
}
