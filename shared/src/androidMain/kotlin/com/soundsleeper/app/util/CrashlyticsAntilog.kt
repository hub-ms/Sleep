package com.soundsleeper.app.util

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel

/**
 * 릴리즈 빌드에서만 심는다(AppLogger.plantCrashlytics 참고).
 * WARNING/ERROR(및 ASSERT)만 Crashlytics로 보낸다 — VERBOSE/DEBUG/INFO까지 보내면
 * breadcrumb 쿼터가 금방 차고, 정작 중요한 로그가 묻힌다.
 */
class CrashlyticsAntilog : Antilog() {
    override fun isEnable(priority: LogLevel, tag: String?): Boolean =
        priority.ordinal >= LogLevel.WARNING.ordinal

    override fun performLog(priority: LogLevel, tag: String?, throwable: Throwable?, message: String?) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        val line = if (tag != null) "[$tag] ${message.orEmpty()}" else message.orEmpty()
        if (line.isNotEmpty()) crashlytics.log(line)

        when {
            throwable != null -> crashlytics.recordException(throwable)
            // Napier.e("메시지") 처럼 예외 없이 에러만 남기는 호출도 Issues 탭에 남긴다.
            priority == LogLevel.ERROR -> crashlytics.recordException(
                IllegalStateException(line.ifEmpty { "Napier ERROR log without message/throwable" })
            )
        }
    }
}

/**
 * AppLogger(commonMain)는 FirebaseCrashlytics를 모르므로, androidMain에서 확장 함수로
 * 얹는다. SleepApp.onCreate()에서 릴리즈 빌드일 때만 호출한다.
 */
fun AppLogger.plantCrashlytics() {
    io.github.aakira.napier.Napier.base(CrashlyticsAntilog())
}
