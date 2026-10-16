package com.soundsleeper.app.platform

/**
 * Crashlytics 같은 크래시 리포팅 SDK에 대한 공통 계약.
 * 이메일/닉네임 등 PII는 절대 넘기지 않는다 — 로그인 상태를 구분할 수 있는 opaque id만 쓴다.
 */
interface CrashReporter {
    /** 로그인 시 opaque userId를, 로그아웃/만료 시 null을 넘긴다. */
    fun setUserId(userId: String?)
}
