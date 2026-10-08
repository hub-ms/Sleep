package com.soundsleeper.app.entity_new

// ❌ shared 모듈 엔티티 임포트 절대 금지 (삭제)
// import com.soundsleeper.app.data.local.AuthInfoEntity

import com.soundsleeper.app.enum_.AuthProvider
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "users",
    indexes = [Index(name = "email_idx", columnList = "email", unique = true)]
)
class UserEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val userId: Long = 0L,

    @Column(nullable = false, unique = true, length = 30)
    var nickname: String,

    /**
     * 닉네임을 마지막으로 바꾼 시각. 변경 쿨다운(NICKNAME_CHANGE_COOLDOWN_DAYS) 판정에 쓴다.
     * null 이면 가입 후 한 번도 바꾸지 않은 것이므로 바로 바꿀 수 있다.
     */
    var nicknameUpdatedAt: LocalDateTime? = null,

    var email: String? = null,
    var profileImageUrl: String? = null,

    @Column(nullable = false)
    var isActive: Boolean = true,

    var isPremium: Boolean = false,
    var lastLoginAt: LocalDateTime? = null,

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val authInfo: MutableList<AuthInfoEntity> = mutableListOf(),

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    var deletedAt: LocalDateTime? = null,
    var deleteAfter: LocalDateTime? = null,

    @Column(nullable = false)
    var isDeleted: Boolean = false,

    @Enumerated(EnumType.STRING)
    var primaryProvider: AuthProvider? = null,

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "user_connected_providers",
        joinColumns = [JoinColumn(name = "user_id")]
    )
    @Column(name = "provider")
    @Enumerated(EnumType.STRING)
    var connectedProviders: MutableSet<AuthProvider> = mutableSetOf(),

    @Column(nullable = false)
    var emailVerified: Boolean = false
) {
    fun updateEmail(newEmail: String) {
        require(newEmail.isNotBlank()) { "이메일은 비어있을 수 없습니다" }
        this.email = newEmail
    }
}