package com.example.sleep.domain.repository

import com.example.sleep.domain.model.NoiseData
import kotlinx.coroutines.flow.Flow

interface NoiseRepository {
    suspend fun insertNoiseData(data: NoiseData)

    suspend fun insertNoiseDataAll(data: List<NoiseData>)

    fun getNoiseDataFlow(sessionId: Long): Flow<List<NoiseData>>

    suspend fun getNoiseData(sessionId: Long): List<NoiseData>

    suspend fun getMaxDecibel(sessionId: Long): Float?
}
