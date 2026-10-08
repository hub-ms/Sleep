package com.soundsleeper.app.repository_new

import com.soundsleeper.app.entity_new.UserEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface UserJpaRepository : JpaRepository<UserEntity, Long> {
    fun findByEmail(email: String): UserEntity?
    fun existsByEmail(email: String): Boolean
    fun existsByNickname(nickName: String): Boolean

    /**
     * 로그인 수단 해제 시 유저 행을 잠근다.
     * 수단이 2개 남은 상태에서 서로 다른 provider 해제 요청이 동시에 들어오면 둘 다 "남은 수단 2개"를
     * 읽고 통과해 계정이 로그인 수단 0개로 잠기므로, 검사와 삭제를 이 락으로 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.userId = :userId")
    fun findByIdForUpdate(@Param("userId") userId: Long): UserEntity?

    fun findAllByIsDeletedTrueAndDeleteAfterBefore(
        time: LocalDateTime
    ): List<UserEntity>
}