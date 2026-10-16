package com.soundsleeper.app.exception

import java.time.LocalDateTime

/**
 * 탈퇴 처리된 계정으로 로그인을 시도했을 때.
 *
 * 탈퇴는 즉시 삭제가 아니라 [java.time.LocalDateTime] 기준 유예 기간을 둔 soft delete 라서,
 * 기간이 남아 있으면 되살릴 수 있다. 그 둘을 앱이 구분해야 "복구할까요?"를 물을지
 * "다시 가입해 주세요"를 안내할지 정할 수 있으므로 [restorableUntil] 로 알려 준다.
 *
 * 예전에는 이 검사 자체가 없어서 탈퇴한 계정도 토큰을 새로 받아 로그인에 성공했다.
 * 화면은 로그인 상태인데 정작 사용자 정보 API 는 전부 막혀 아무 기능도 동작하지 않았다.
 */
class WithdrawnAccountException(
    val restorableUntil: LocalDateTime?,
    message: String = if (restorableUntil != null) {
        "탈퇴 신청한 계정입니다. 복구하시겠어요?"
    } else {
        "이미 삭제된 계정입니다. 새로 가입해 주세요."
    }
) : RuntimeException(message) {
    val isRestorable: Boolean get() = restorableUntil != null
}
