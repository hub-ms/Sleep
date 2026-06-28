package com.sleepytime.app.entity_new

import com.sleepytime.shared.enum_.AuthProvider
import jakarta.persistence.*

@Entity
@Table(
    name = "social_info",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["social_id", "provider"])
    ]
)
class AuthInfoEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", updatable = false)
    val provider: AuthProvider?,

    @Column(name = "social_id", nullable = false)
    val authId: String,

    // var 및 Nullable(? = null) 처리를 해주는 것이 다중 모듈 아키텍처에서 안전합니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    var user: UserEntity? = null
)
