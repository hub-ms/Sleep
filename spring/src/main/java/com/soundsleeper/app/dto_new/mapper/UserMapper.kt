package com.soundsleeper.app.dto_new.mapper

import com.soundsleeper.app.entity_new.UserEntity
import com.soundsleeper.app.data.remote.dto.response.UserResponse
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime

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

fun UserEntity.toResponse(): UserResponse {
    return UserResponse(
        userId = this.userId,
        nickname = this.nickname,
        email = this.email?.takeUnless { SYNTHETIC_EMAIL_REGEX.matches(it) },
        profileImageUrl = this.profileImageUrl,
        isActive = this.isActive,
        isPremium = this.isPremium,

        // [해결] Nullable Java LocalDateTime -> kotlinx.datetime.LocalDateTime 수동 조립
        lastLoginAt = this.lastLoginAt?.let { javaTime ->
            KotlinLocalDateTime(
                year = javaTime.year,
                monthNumber = javaTime.monthValue,
                dayOfMonth = javaTime.dayOfMonth,
                hour = javaTime.hour,
                minute = javaTime.minute,
                second = javaTime.second,
                nanosecond = javaTime.nano
            )
        },

        // [해결] Non-null Java LocalDateTime -> kotlinx.datetime.LocalDateTime 수동 조립
        createdAt = this.createdAt.let { javaTime ->
            KotlinLocalDateTime(
                year = javaTime.year,
                monthNumber = javaTime.monthValue,
                dayOfMonth = javaTime.dayOfMonth,
                hour = javaTime.hour,
                minute = javaTime.minute,
                second = javaTime.second,
                nanosecond = javaTime.nano
            )
        },
        updatedAt = this.updatedAt.let { javaTime ->
            KotlinLocalDateTime(
                year = javaTime.year,
                monthNumber = javaTime.monthValue,
                dayOfMonth = javaTime.dayOfMonth,
                hour = javaTime.hour,
                minute = javaTime.minute,
                second = javaTime.second,
                nanosecond = javaTime.nano
            )
        }
    )
}
