package com.sleepytime.app.service_new

import com.sleepytime.app.dto_new.SleepSessionDto
import com.sleepytime.app.repository_new.SleepSessionJpaRepository
import com.sleepytime.app.repository_new.SleepStageJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SleepService(
    private val sleepSessionJpaRepository: SleepSessionJpaRepository,
    private val sleepStageJpaRepository: SleepStageJpaRepository,
) {

    @Transactional(readOnly = true)
    fun getUserSleepData(userId: Long): List<SleepSessionDto> {
        val sessions = sleepSessionJpaRepository.findByUserIdOrderByStartAtDesc(userId)

        val sessionIds = sessions.map { it.id }

        val stages = sleepStageJpaRepository.findBySleepSessionIdIn(sessionIds)

        return sessions.map { session ->
            SleepSessionDto(
                session = session,
                stages = stages.filter { it.sleepSessionId == session.id },
            )
        }
    }

    // 🔥 핵심: bulk delete
    @Transactional
    fun deleteUserSleepData(userId: Long) {
        sleepStageJpaRepository.deleteByUserId(userId)
        sleepSessionJpaRepository.deleteByUserId(userId)
    }
}