package com.example.sleep.data.mapper

import com.example.sleep.data.local.entity.SleepGoalEntity
import com.example.sleep.domain.model.SleepGoal

object SleepGoalMapper {
    fun toDomain(entity: SleepGoalEntity): SleepGoal = SleepGoal(
        userId = entity.userId,
        targetDurationMillis = entity.targetDurationMillis,
        targetBedTime = entity.targetBedTime,
        targetWakeTime = entity.targetWakeTime
    )

    fun fromDomain(domain: SleepGoal): SleepGoalEntity = SleepGoalEntity(
        userId = domain.userId,
        targetDurationMillis = domain.targetDurationMillis,
        targetBedTime = domain.targetBedTime,
        targetWakeTime = domain.targetWakeTime
    )
}
