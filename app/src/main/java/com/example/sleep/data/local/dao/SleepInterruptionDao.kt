package com.example.sleep.data.local.dao

import androidx.room.*
import com.example.sleep.data.local.entity.SleepInterruptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepInterruptionDao {
    // 수면 중단 이벤트 삽입 메서드(단일)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepInterruption(interruption: SleepInterruptionEntity)

    // 수면 중단 이벤트 삽입 메서드(전체)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepInterruptionAll(data: List<SleepInterruptionEntity>)

    // 수면 중단 세션 조회 메서드(Flow)
    @Query("SELECT * FROM sleep_interruption WHERE sessionId = :sessionId")
    fun getBySleepInterruptionSessionFlow(sessionId: Long): Flow<List<SleepInterruptionEntity>>

    // 수면 중단 세션 조회 메서드
    @Query("SELECT * FROM sleep_interruption WHERE sessionId = :sessionId")
    suspend fun getBySleepInterruptionSession(sessionId: Long): List<SleepInterruptionEntity>

    // 수면 중단 전체 시간 조회 메서드
    @Query("SELECT SUM(durationMillis) FROM sleep_interruption WHERE sessionId = :sessionId")
    suspend fun getSleepInterruptionTotalDuration(sessionId: Long): Long?
}
