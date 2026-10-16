package com.soundsleeper.app.dto_new.mapper

import com.soundsleeper.app.entity_new.UserEntity
import com.soundsleeper.app.data.remote.dto.response.UserResponse
import com.soundsleeper.app.enum_.AuthProvider
import kotlinx.datetime.LocalDateTime

// Entity (Java) -> Response DTO (Kotlinx) 변환
/**
 * 지어낸 이메일인지 판별한다.
 *
 * 과거에는 소셜 로그인에서 이메일을 받지 못하면 "{socialId}@kakao.user" 같은 주소를 만들어
 * 저장했다. 지금은 만들지 않지만 그때 가입한 사용자들의 레코드에는 값이 남아 있다.
 * 이미 로그인된 사용자는 socialId 로 조회되는 경로를 타서 이메일 로직을 다시 거치지 않으므로,
 * 저장된 값을 그대로 두면 계속 이상한 주소가 보인다. 내보낼 때 걸러 준다.
 *
 * 근본적으로는 DB 의 해당 레코드를 정리하는 편이 낫다.
 */
private val SYNTHETIC_EMAIL_REGEX = Regex("^\\d+@(kakao|google|KAKAO|GOOGLE)\\.user$")

/**
 * [connectedProviders] 는 호출부가 계산해서 넘긴다.
 *
 * 엔티티의 authInfo 와 connectedProviders 는 둘 다 LAZY 라, 트랜잭션 밖에서 호출되는
 * 자리가 섞여 있는 이 매퍼 안에서 직접 읽으면 LazyInitializationException 으로 터질 수 있다.
 * 또 비정규화된 user.connectedProviders 는 실제 자격증명과 어긋날 수 있어 믿을 값이 아니다.
 * 판단 규칙은 AuthService.connectedAuthMethods 한 곳에만 둔다.
 */
fun UserEntity.toResponse(connectedProviders: Set<AuthProvider> = emptySet()): UserResponse {
    return UserResponse(
        userId = this.userId,
        nickname = this.nickname,
        email = this.email?.takeUnless { SYNTHETIC_EMAIL_REGEX.matches(it) },
        profileImageUrl = this.profileImageUrl,
        isActive = this.isActive,
        isPremium = this.isPremium,
        connectedProviders = connectedProviders,

        // [해결] Nullable Java LocalDateTime -> kotlinx.datetime.LocalDateTime 수동 조립
        lastLoginAt = this.lastLoginAt?.let { javaTime ->
            LocalDateTime(
                year = javaTime.year,
                month = javaTime.monthValue,
                day = javaTime.dayOfMonth,
                hour = javaTime.hour,
                minute = javaTime.minute,
                second = javaTime.second,
                nanosecond = javaTime.nano
            )
        },

        // [해결] Non-null Java LocalDateTime -> kotlinx.datetime.LocalDateTime 수동 조립
        createdAt = this.createdAt.let { javaTime ->
            LocalDateTime(
                year = javaTime.year,
                month = javaTime.monthValue,
                day = javaTime.dayOfMonth,
                hour = javaTime.hour,
                minute = javaTime.minute,
                second = javaTime.second,
                nanosecond = javaTime.nano
            )
        },
        updatedAt = this.updatedAt.let { javaTime ->
            LocalDateTime(
                year = javaTime.year,
                month = javaTime.monthValue,
                day = javaTime.dayOfMonth,
                hour = javaTime.hour,
                minute = javaTime.minute,
                second = javaTime.second,
                nanosecond = javaTime.nano
            )
        }
    )
}
