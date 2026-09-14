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
import kotlinx.datetime.LocalDate
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
    suspend fun getSessionByDate(date: LocalDate): SleepSession? =
        withContext(Dispatchers.IO) {
        queries.getSessionByDate(
            targetDate = date
        )
        .executeAsOneOrNull()
        ?.toDomain()
    }
    suspend fun getSessionsByDateRange(
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<SleepSession> = withContext(Dispatchers.IO) {
        queries.getSessionsByDateRange(
            fromDate = fromDate,
            toDate = toDate,
        )
        .executeAsList()
        .map { it.toDomain() }
    }
    suspend fun getSessionDatesByMonth(
        year: String,
        month: String
    ): List<LocalDate> = withContext(Dispatchers.IO) {
        queries.getSessionDatesByMonth(
            yearMonthPattern = "$year, $month",
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
}