package com.soundsleeper.app.repository_new

import com.soundsleeper.app.entity_new.AuthInfoEntity
import com.soundsleeper.app.enum_.AuthProvider
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AuthInfoJpaRepository : JpaRepository<AuthInfoEntity, Long> {

    // 1. socialLogin #1에서 사용 (authId 기반 조회)
    fun findByAuthIdAndProvider(authId: String, provider: AuthProvider): AuthInfoEntity?

    // 2. connectSocial #1에서 사용
    @Query("SELECT a FROM AuthInfoEntity a WHERE a.user.userId = :userId AND a.provider = :provider")
    fun findByUserIdAndProvider(@Param("userId") userId: Long, @Param("provider") provider: AuthProvider): AuthInfoEntity?

    /** 남은 로그인 수단 계산용. user.connectedProviders는 어긋날 수 있어 실제 자격증명 행을 센다. */
    fun countByUser_UserId(userId: Long): Long
}