package com.example.sleep.data.local.dao

import androidx.room.*
import com.example.sleep.data.local.entity.SleepSessionEntity

@Dao
interface SleepSessionDao {
    // 수면 세션 삽입 메서드
    @Insert(onConflict = OnConflictStrategy.REPLACE)(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepSession(session: SleepSessionEntity): Long

    // 수면 세션 조회 메서드(Flow)
    @Query("SELECT * FROM sleep_session WHERE localId = :id")
    suspend fun getSleepSessionFlow(id: Long): SleepSessionEntity?

    // 날짜별 수면 세션 조회 메서드
    @Query("""
        SELECT * FROM sleep_session
        WHERE startTimeMillis BETWEEN :start AND :end
    """)
    suspend fun getSleepSessionsByDateRange(
        start: Long,
        end: Long
    ): List<SleepSessionEntity>

    // 수면 세션 단일 삭제 메서드
    @Delete
    suspend fun deleteSleepSession(session: SleepSessionEntity)

    // 수면 세션 전체 삭제 메서드
    @Query("DELETE FROM sleep_session")
    suspend fun deleteAllSleepSessions()
}
