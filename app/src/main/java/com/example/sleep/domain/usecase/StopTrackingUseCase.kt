package com.example.sleep.domain.usecase

import com.example.sleep.data.local.entity.SleepSessionEntity
import javax.inject.Inject

class StopTrackingUseCase @Inject constructor(
    private val repository: SleepRepository
){
    suspend operator fun invoke(session: SleepSessionEntity) {
        repository.saveSession(session)
    }
}