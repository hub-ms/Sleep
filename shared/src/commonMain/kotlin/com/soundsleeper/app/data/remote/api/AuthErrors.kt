package com.soundsleeper.app.data.remote.api

class LastAuthMethodError(
    message: String = "마지막 로그인 수단은 해제할 수 없습니다."
) : Exception(message)
/**
 * 탈퇴 처리된 계정으로 로그인하려 했을 때(서버 410).
 *
 * 메시지는 서버가 내려준 것을 그대로 쓴다. 유예 기간이 남았으면 복구를 권하는 문구가,
 * 지났으면 다시 가입하라는 문구가 들어 있다.
 */
class WithdrawnAccountError(
    message: String = "탈퇴한 계정입니다."
) : Exception(message)
