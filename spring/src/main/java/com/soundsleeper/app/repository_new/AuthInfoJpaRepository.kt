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


    /**
     * 연결된 소셜 수단의 "목록". 개수(countByUser_UserId)만 있었을 때는 앱이 어떤 수단이
     * 연결됐는지 알 길이 없어, 서버의 해제 가드와 화면의 표시가 서로 다른 근거로 움직였다.
     */
    fun findAllByUser_UserId(userId: Long): List<AuthInfoEntity>
}