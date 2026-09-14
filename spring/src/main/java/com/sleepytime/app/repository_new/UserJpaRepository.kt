package com.sleepytime.app.repository_new

import com.sleepytime.app.entity_new.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface UserJpaRepository : JpaRepository<UserEntity, Long> {
    fun findByEmail(email: String): UserEntity?
    fun existsByEmail(email: String): Boolean
    fun existsByNickname(nickName: String): Boolean

    fun findAllByIsDeletedTrueAndDeleteAfterBefore(
        time: LocalDateTime
    ): List<UserEntity>
}