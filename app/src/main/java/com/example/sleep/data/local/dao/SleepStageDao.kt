package com.example.sleep.data.local.dao

import androidx.room.*
import com.example.sleep.data.local.entity.SleepStageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepStageDao {
    // 수면 단계 데이터 삽입 메서드
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepStage(stage: SleepStageEntity)

    // 수면 단계 데이터 삽입 메서드(전체)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepStageAll(data: List<SleepStageEntity>)

    // 수면 단계 조회 메서드(Flow)
    @Query("SELECT * FROM sleep_stage WHERE sessionId = :sessionId ORDER BY startTimeMillis ASC")
    fun getSleepStagesForSessionFlow(sessionId: Long): Flow<List<SleepStageEntity>>

    // 수면 단계 조회 메서드
    @Query("SELECT * FROM sleep_stage WHERE sessionId = :sessionId ORDER BY startTimeMillis ASC")
    suspend fun getSleepStagesForSession(sessionId: Long): List<SleepStageEntity>

    // 특정 수면 단계 조회 메서드
    @Query(
        """
            SELECT * FROM sleep_stage
            WHERE sessionId = :sessionId
            AND stage = :stage
        """
    )
    suspend fun getBySleepStage(
        sessionId: Long, stage: String
    ): List<SleepStageEntity>
}
