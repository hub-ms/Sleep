package com.soundsleeper.app.exception

class LastAuthMethodException(
    message: String = "마지막 로그인 수단은 해제할 수 없습니다."
) : RuntimeException(message)