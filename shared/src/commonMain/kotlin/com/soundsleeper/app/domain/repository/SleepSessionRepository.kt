package com.soundsleeper.app.domain.repository

import com.soundsleeper.app.domain.model.EnvironmentFeature
import com.soundsleeper.app.domain.model.SleepAnalysis
import com.soundsleeper.app.domain.model.SleepSession
import kotlinx.datetime.LocalDate

interface SleepSessionRepository {
    suspend fun initializeModel(): Result<Unit>
    suspend fun analyzeSleepData(
        sensorData: List<FloatArray>,
        environmentFeature: EnvironmentFeature?,
        // 신규(Phase 8): 같은 30초 구간의 raw 오디오(16kHz 리샘플링된 mono 샘플). 기본값 null은
        // 기존 호출부(오디오 없이 accel만 분석)를 전혀 바꾸지 않는다.
        rawAudioEpochSamples: FloatArray? = null,
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
    suspend fun getSessionById(sessionId: String): SleepSession?
    suspend fun deleteSession(sessionId: String)
    suspend fun updateEnvironmentContext(feature: EnvironmentFeature): Result<Unit>
    suspend fun getRecentSessions(days: Int): List<SleepSession>
}
