package com.soundsleeper.app.data.local.dao

import com.soundsleeper.app.data.local.generated.SleepDatabase
import com.soundsleeper.app.data.local.mapper.toDomain
import com.soundsleeper.app.data.local.mapper.toEntity
import com.soundsleeper.app.domain.model.SleepSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/** SQLDelight로 생성된 쿼리를 감싸는 수면 세션 DAO. 모든 호출은 IO 디스패처에서 실행된다. */
class SleepSessionDao(db: SleepDatabase) {

    private val queries = db.sleepSessionEntityQueries

    /** 세션을 저장(기존 ID면 덮어쓰기)한다 — 측정 종료 후 분석 결과를 실제로 영속화하는 지점. */
    suspend fun insertSession(session: SleepSession) = withContext(Dispatchers.IO) {
        queries.insertSession(session.toEntity())
        Unit
    }

    /** 특정 날짜의 세션을 조회한다. */
    suspend fun getSessionByDate(date: LocalDate): SleepSession? =
        withContext(Dispatchers.IO) {
        queries.getSessionByDate(
            targetDate = date
        )
        .executeAsOneOrNull()
        ?.toDomain()
    }

    /** 날짜 범위 내의 모든 세션을 조회한다. */
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

    /** 해당 연/월에 세션이 존재하는 날짜들만 조회한다(캘린더에 표시할 날짜 찾기). */
    suspend fun getSessionDatesByMonth(
        year: String,
        month: String
    ): List<LocalDate> = withContext(Dispatchers.IO) {
        queries.getSessionDatesByMonth(
            yearMonthPattern = "$year, $month",
        ).executeAsList()
    }

    /** 가장 최근에 생성된 세션 하나를 조회한다. */
    suspend fun getLatestSession(): SleepSession? = withContext(Dispatchers.IO) {
        queries.getLatestSession()
            .executeAsOneOrNull()
            ?.toDomain()
    }

    /** 세션 ID로 정확히 하나의 세션을 조회한다(측정 직후 리포트 화면이 그 세션을 바로 열 때 사용). */
    suspend fun getSessionById(sessionId: String): SleepSession? = withContext(Dispatchers.IO) {
        queries.getSessionById(sessionId)
            .executeAsOneOrNull()
            ?.toDomain()
    }

    /** 세션을 삭제한다. */
    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        queries.deleteSession(sessionId)
        Unit
    }
}