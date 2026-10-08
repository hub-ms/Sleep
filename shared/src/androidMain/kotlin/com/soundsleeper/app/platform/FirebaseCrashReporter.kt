package com.soundsleeper.app.platform

import com.google.firebase.crashlytics.FirebaseCrashlytics

class FirebaseCrashReporter : CrashReporter {
    override fun setUserId(userId: String?) {
        // 이메일/닉네임 등 PII는 절대 쓰지 않는다 — User.userId(Long)의 문자열 표현만 사용한다.
        FirebaseCrashlytics.getInstance().setUserId(userId.orEmpty())
    }
}
