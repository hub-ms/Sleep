package com.example.sleep.data.local.dao

import androidx.room.*
import com.example.sleep.data.local.entity.SleepScoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepScoreDao {
    // 수면 점수 삽입 메서드
    @Insert(onConflict = OnConflictStrategy.REPLACE)(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepScore(score: SleepScoreEntity)

    // 수면 점수 조회 메서드(Flow)
    @Query("SELECT * FROM sleep_score WHERE sessionId = :sessionId")
    fun getSleepScoreForSessionFlow(sessionId: Long): Flow<SleepScoreEntity?>

    // 수면 점수 조회 메서드
    @Query("SELECT * FROM sleep_score WHERE sessionId = :sessionId")
    suspend fun getSleepScoreForSession(sessionId: Long): SleepScoreEntity?
}
