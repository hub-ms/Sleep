package com.example.sleep.domain.repository

import com.example.sleep.domain.model.AccelerometerData
import kotlinx.coroutines.flow.Flow

interface AccelerometerRepository {
    suspend fun insertAccelerometerData(data: AccelerometerData)

    suspend fun insertAccelerometerDataAll(data: List<AccelerometerData>)

    fun getAccelerometerDataFlow(sessionId: Long): Flow<List<AccelerometerData>>

    suspend fun getAccelerometerData(sessionId: Long): List<AccelerometerData>
}
