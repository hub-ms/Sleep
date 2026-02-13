package com.example.sleep.data.repository

import com.example.sleep.data.local.dao.NoiseDao
import com.example.sleep.data.mapper.NoiseDataMapper
import com.example.sleep.domain.model.NoiseData
import com.example.sleep.domain.repository.NoiseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NoiseRepositoryImpl @Inject constructor(
    private val noiseDao: NoiseDao
) : NoiseRepository {
    override suspend fun insertNoiseData(data: NoiseData) {
        val entity = NoiseDataMapper.fromDomain(data)
        noiseDao.insertNoiseData(entity)
    }

    override suspend fun insertNoiseDataAll(data: List<NoiseData>) {
        val entities = data.map { NoiseDataMapper.fromDomain(it) }
        noiseDao.insertNoiseDataAll(entities)
    }

    override fun getNoiseDataFlow(sessionId: Long): Flow<List<NoiseData>> {
        return noiseDao.getNoiseDataForSessionFlow(sessionId).map {
            it.map { entity -> NoiseDataMapper.toDomain(entity) }
        }
    }

    override suspend fun getNoiseData(sessionId: Long): List<NoiseData> {
        return noiseDao.getNoiseDataForSession(sessionId).map {
            NoiseDataMapper.toDomain(it)
        }
    }

    override suspend fun getMaxDecibel(sessionId: Long): Float? {
        return noiseDao.getMaxDecibel(sessionId)
    }
}
