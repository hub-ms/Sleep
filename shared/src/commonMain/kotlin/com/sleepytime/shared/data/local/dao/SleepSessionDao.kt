package com.sleepytime.shared.data.local.dao

import com.sleepytime.shared.data.local.generated.SleepDatabase
import com.sleepytime.shared.data.local.mapper.toDomain
import com.sleepytime.shared.data.local.mapper.toEntity
import com.sleepytime.shared.domain.model.SleepSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class SleepSessionDao(db: SleepDatabase) {

    private val queries = db.sleepSessionEntityQueries

    suspend fun insertSession(session: SleepSession) = withContext(Dispatchers.IO) {
        queries.insertSession(session.toEntity())
        Unit
    }
    suspend fun getSessionById(sessionId: String): SleepSession? = withContext(Dispatchers.IO) {
        queries.getSessionById(sessionId)
            .executeAsOneOrNull()
            ?.toDomain()
    }

    suspend fun getSessionByDate(dateEpochMs: Long): SleepSession? =
        withContext(Dispatchers.IO) {
            val (startOfDayMs, endOfDayMs) = dateEpochMs.toDayRange()
        queries.getSessionByDate(
            startOfDayMs = startOfDayMs,
            endOfDayMs = endOfDayMs
        )
        .executeAsOneOrNull()
        ?.toDomain()
    }
    suspend fun getSessionsByDateRange(
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<SleepSession> = withContext(Dispatchers.IO) {
        queries.getSessionsByDateRange(
            fromEpochMs = fromEpochMs,
            toEpochMs   = toEpochMs
        )
        .executeAsList()
        .map { it.toDomain() }
    }
    suspend fun getSessionDatesByMonth(
        year: String,
        month: String
    ): List<Long> = withContext(Dispatchers.IO) {
        queries.getSessionDatesByMonth(
            year = year.toLong(),
            month = month.toLong()
        ).executeAsList()
    }

    suspend fun getLatestSession(): SleepSession? = withContext(Dispatchers.IO) {
        queries.getLatestSession()
            .executeAsOneOrNull()
            ?.toDomain()
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        queries.deleteSession(sessionId)
        Unit
    }



    private fun Long.toDayRange(): Pair<Long, Long> {
        val localDate = Instant.fromEpochMilliseconds(this)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        val startOfDay = localDate
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        val endOfDay = localDate
            .plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        return startOfDay to endOfDay
    }
}