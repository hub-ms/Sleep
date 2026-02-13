package com.example.sleep.data.local.dao

import androidx.room.*
import com.example.sleep.data.local.entity.SleepGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepGoalDao {
    // 수면 목표 삽입 메서드
    @Insert(onConflict = OnConflictStrategy.REPLACE)(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepGoal(goal: SleepGoalEntity)

    // 수면 목표 유저 조회 메서드(Flow)
    @Query("SELECT * FROM sleep_goal WHERE userId = :userId")
    fun getByUserIdFlow(userId: Long): Flow<SleepGoalEntity?>

    // 수면 목표 유저 조회 메서드
    @Query("SELECT * FROM sleep_goal WHERE userId = :userId")
    suspend fun getByUserId(userId: Long): SleepGoalEntity?

    // 수면 목표 수정 메서드
    @Update
    suspend fun updateSleepGoal(goal: SleepGoalEntity)

    // 수면 목표 삭제 메서드
    @Delete
    suspend fun deleteSleepGoal(goal: SleepGoalEntity)

    // 수면 목표 전체 삭제 메서드
    @Query("DELETE FROM sleep_goal")
    suspend fun deleteAllSleepGoals()
}
