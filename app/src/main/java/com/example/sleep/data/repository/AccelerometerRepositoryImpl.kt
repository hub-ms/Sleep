package com.example.sleep.data.repository

import com.example.sleep.data.local.dao.AccelerometerDao
import com.example.sleep.data.mapper.AccelerometerDataMapper
import com.example.sleep.domain.model.AccelerometerData
import com.example.sleep.domain.repository.AccelerometerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AccelerometerRepositoryImpl @Inject constructor(
    private val accelerometerDao: AccelerometerDao
) : AccelerometerRepository {
    override suspend fun insertAccelerometerData(data: AccelerometerData) {
        val entity = AccelerometerDataMapper.fromDomain(data)
        accelerometerDao.insertAccelerometerData(entity)
    }

    override suspend fun insertAccelerometerDataAll(data: List<AccelerometerData>) {
        val entities = data.map { AccelerometerDataMapper.fromDomain(it) }
        accelerometerDao.insertAccelerometerDataAll(entities)
    }

    override fun getAccelerometerDataFlow(sessionId: Long): Flow<List<AccelerometerData>> {
        return accelerometerDao.getAccelerometerDataForSessionFlow(sessionId).map {
            it.map { entity -> AccelerometerDataMapper.toDomain(entity) }
        }
    }

    override suspend fun getAccelerometerData(sessionId: Long): List<AccelerometerData> {
        return accelerometerDao.getAccelerometerDataForSession(sessionId).map {
            AccelerometerDataMapper.toDomain(it)
        }
    }
}
