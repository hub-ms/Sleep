package com.example.sleep.data.local.dao

import androidx.room.*
import com.example.sleep.data.local.entity.AccelerometerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccelerometerDao {
    // 가속도 센서 데이터 삽입 메서드(단일)
    @Insert(onConflict = OnConflictStrategy.REPLACE)(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccelerometer(entity: AccelerometerEntity)

    // 가속도 센서 데이터 삽입 메서드(전체)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccelerometerAll(data: List<AccelerometerEntity>)

    // 세션별 가속도 센서 데이터 조회 메서드(Flow)
    @Query(
        """
            SELECT * FROM accelerometer_data
            WHERE sessionId = :sessionId
            ORDER BY timestamp ASC
        """
    )
    fun getByAccelerometerSessionFlow(sessionId: Long): Flow<List<AccelerometerEntity>>

    // 세션별 가속도 센서 데이터 조회 메서드
    @Query(
        """
            SELECT * FROM accelerometer_data
            WHERE sessionId = :sessionId
            ORDER BY timestamp ASC
        """
    )
    suspend fun getByAccelerometerSession(sessionId: Long): List<AccelerometerEntity>

    // 특정 시간대의 가속도 센서 데이터 조회 메서드
    @Query(
        """
            SELECT * FROM accelerometer_data
            WHERE sessionId = :sessionId AND 
            timestamp BETWEEN :startTime AND :endTime
            ORDER BY timestamp ASC
        """
    )
    suspend fun getByAccelerometerTimeRange(
        sessionId: Long,
        startTime: Long,
        endTime: Long
    ): List<AccelerometerEntity>

    // 특정 ID 조회 메서드
    @Query("SELECT * FROM accelerometer_data WHERE id = :id")
    suspend fun getByAccelerometerId(id: Long): AccelerometerEntity?

    // 단일 삭제 메서드
    @Delete
    suspend fun deleteAccelerometer(entity: AccelerometerEntity)

    // 세션 단위 삭제 메서드
    @Query("DELETE FROM accelerometer_data WHERE sessionId = :sessionId")
    suspend fun deleteAccelerometerBySession(sessionId: Long)
}
