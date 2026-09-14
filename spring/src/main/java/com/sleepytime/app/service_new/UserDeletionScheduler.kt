package com.sleepytime.app.scheduler

import com.sleepytime.app.repository_new.UserJpaRepository
import com.sleepytime.app.repository_new.SleepSessionJpaRepository
import com.sleepytime.app.repository_new.SleepStageJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class UserDeletionScheduler(
    private val userJpaRepository: UserJpaRepository,
    private val sleepSessionRepository: SleepSessionJpaRepository,
    private val sleepStageJpaRepository: SleepStageJpaRepository
) {
    private val log = LoggerFactory.getLogger(UserDeletionScheduler::class.java)

    // 매일 새벽 3시에 만료된(탈퇴 유예 기간이 지난) 유저와 연관 데이터 일괄 삭제
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional // 대량의 삭제 작업이 일어나므로 전체 과정을 하나의 트랜잭션으로 묶습니다.
    fun deleteExpiredUsers() {
        log.info("[Scheduler] 만료된 유저 영구 삭제 작업 시작")
        val now = LocalDateTime.now()

        // 1. 삭제 대상 유저 일괄 조회
        val targets = userJpaRepository.findAllByIsDeletedTrueAndDeleteAfterBefore(now)

        if (targets.isEmpty()) {
            log.info("[Scheduler] 삭제 대상 유저가 없습니다.")
            return
        }

        log.info("[Scheduler] 총 {}명의 유저 데이터 삭제 진행", targets.size)

        // 2. 루프를 돌며 유저의 수면 데이터 및 유저 엔티티 삭제
        targets.forEach { user ->
            try {
                // 연관 데이터(수면 세션, 수면 단계) 삭제
                sleepSessionRepository.deleteByUserId(user.userId)
                sleepStageJpaRepository.deleteByUserId(user.userId)

                // 유저 본인 삭제
                userJpaRepository.delete(user)

                log.debug("[Scheduler] 유저 영구 삭제 완료: userId={}", user.userId)
            } catch (e: Exception) {
                // 특정 유저 삭제 중 예외 발생 시 로그를 남김 (전체 트랜잭션 롤백을 원치 않는다면 개별 트랜잭션 분리 고려 가능)
                log.error("[Scheduler] 유저 삭제 중 오류 발생: userId=${user.userId}, message=${e.message}")
                throw e
            }
        }

        log.info("[Scheduler] 만료된 유저 영구 삭제 작업 완료")
    }
}