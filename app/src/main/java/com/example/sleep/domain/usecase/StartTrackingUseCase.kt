package com.example.sleep.domain.usecase

import javax.inject.Inject

class StartTrackingUseCase @Inject constructor(
    private val repository: SleepRepository
){
    suspend operator fun invoke() {

    }
}