package com.sleepytime.app.entity_new

// ❌ shared 모듈 엔티티 임포트 절대 금지 (삭제)
// import com.sleepytime.shared.data.local.AuthInfoEntity

import com.sleepytime.shared.enum_.AuthProvider
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