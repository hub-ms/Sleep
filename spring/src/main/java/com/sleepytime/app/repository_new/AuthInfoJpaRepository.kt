package com.sleepytime.app.repository_new

import com.sleepytime.app.entity_new.AuthInfoEntity
import com.sleepytime.app.entity_new.UserEntity
import com.sleepytime.shared.enum_.AuthProvider
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AuthInfoJpaRepository : JpaRepository<AuthInfoEntity, Long> {

    // 1. socialLogin #1에서 사용 (authId 기반 조회)
    fun findByAuthIdAndProvider(authId: String, provider: AuthProvider): AuthInfoEntity?

    // 2. connectSocial #1에서 사용
    @Query("SELECT a FROM AuthInfoEntity a WHERE a.user.userId = :userId AND a.provider = :provider")
    fun findByUserIdAndProvider(@Param("userId") userId: Long, @Param("provider") provider: AuthProvider): AuthInfoEntity?

    // 3. socialLogin #3에서 사용 (서비스단에서 findUserBySocialIdAndProvider로 호출하므로 명칭 통일)
    @Query("SELECT DISTINCT a.user FROM AuthInfoEntity a WHERE a.authId = :socialId AND a.provider != :provider AND a.user IS NOT NULL")
    fun findUserBySocialIdAndProvider(
        @Param("socialId") socialId: String,
        @Param("provider") provider: AuthProvider
    ): UserEntity?

    fun findByUser_UserId(userId: Long): UserEntity?
    fun deleteByUser_UserId(userId: Long)
}