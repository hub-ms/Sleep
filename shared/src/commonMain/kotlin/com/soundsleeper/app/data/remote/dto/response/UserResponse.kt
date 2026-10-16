package com.soundsleeper.app.data.remote.dto.response

import com.soundsleeper.app.enum_.AuthProvider
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    @SerialName("userId") val userId: Long = 0L,
    @SerialName("email") val email: String? = null,
    @SerialName("nickname") val nickname: String = "",
    @SerialName("profileImageUrl") val profileImageUrl: String? = null,
    @SerialName("isPremium") val isPremium: Boolean = false,
    @SerialName("isActive") val isActive: Boolean = true,
    @SerialName("lastLoginAt") val lastLoginAt: LocalDateTime? = null,
    @SerialName("createdAt") val createdAt: LocalDateTime = LocalDateTime(2023, 1, 1, 0, 0),
    @SerialName("updatedAt") val updatedAt: LocalDateTime? = null,
    /**
     * 이 계정에 실제로 연결된 로그인 수단. 서버가 자격증명 행과 이메일 인증 여부로 계산해
     * 내려준다(GET /user/info).
     *
     * 예전에는 이 필드가 없어서 앱이 "지금 로그인한 수단"밖에 알 수 없었다. 그래서 수단이
     * 두 개인데도 화면은 하나로 보고 해제 버튼을 비활성화했고(눌러도 무반응), 서버의 해제
     * 가드와 판단이 어긋나 정상 해제가 409로 막히기도 했다.
     *
     * 기본값을 둔 이유: 이 필드를 내려주지 않는 다른 응답(로그인·프로필 저장 등)에서도
     * 역직렬화가 깨지지 않아야 한다. 즉 "빈 집합"은 "연결된 수단이 없다"가 아니라
     * "이 응답은 모른다"는 뜻이므로, 호출부가 이 값을 믿고 덮어써도 되는 곳은
     * GET /user/info 뿐이다.
     */
    @SerialName("connectedProviders") val connectedProviders: Set<AuthProvider> = emptySet(),
)
