package com.example.sleep.data.local.dao

import androidx.room.*
import com.example.sleep.data.local.entity.NoiseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoiseDao {
    // 소음 센서 데이터 삽입 메서드(단일)
    @Insert(onConflict = OnConflictStrategy.REPLACE)(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoise(entity: NoiseEntity)

    // 소음 센서 데이터 삽입 메서드(전체)
    @Insert(onConflict = OnConflictStrategy.REPLACE)(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoiseAll(data: List<NoiseEntity>)

    // 세션별 소음 센서 데이터 조회 메서드(Flow)
    @Query("""
        SELECT * FROM noise_data
        WHERE sessionId = :sessionId
        ORDER BY timestamp ASC
    """)
    fun getByNoiseSessionFlow(sessionId: Long): Flow<List<NoiseEntity>>


    // 세션별 소음 센서 데이터 조회 메서드
    @Query("""
        SELECT * FROM noise_data
        WHERE sessionId = :sessionId
        ORDER BY timestamp ASC
    """)
    suspend fun getByNoiseSession(sessionId: Long): List<NoiseEntity>


    // 특정 시간대의 소음 센서 데이터 조회 메서드
    @Query("""
        SELECT * FROM noise_data
        WHERE sessionId = :sessionId
        AND timestamp BETWEEN :start AND :end
        ORDER BY timestamp ASC
    """)
    suspend fun getByNoiseTimeRange(sessionId: Long, start: Long, end: Long): List<NoiseEntity>

    // 평균 소음 조회 메서드
    @Query("""
        SELECT AVG(decibel)
        FROM noise_data
        WHERE sessionId = :sessionId
    """)
    suspend fun getAverageNoise(sessionId: Long): Float?

    // 최대 소음 조회 메서드
    @Query("""
        SELECT MAX(decibel)
        FROM noise_data
        WHERE sessionId = :sessionId
    """)
    suspend fun getMaxNoise(sessionId: Long): Float?

    // 단일 삭제 메서드
    @Delete
    suspend fun deleteNoise(entity: NoiseEntity)

    // 세션 단위 삭제 메서드
    @Query("DELETE FROM noise_data WHERE sessionId = :sessionId")
    suspend fun deleteNoiseBySession(sessionId: Long)
}
