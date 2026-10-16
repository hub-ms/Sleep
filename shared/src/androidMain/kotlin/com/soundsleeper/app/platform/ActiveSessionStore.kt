package com.soundsleeper.app.platform

import com.russhwolf.settings.ObservableSettings
import com.soundsleeper.app.util.PreferencesKeys.Session.KEY_DURATION
import com.soundsleeper.app.util.PreferencesKeys.Session.KEY_MUSIC_NAME
import com.soundsleeper.app.util.PreferencesKeys.Session.KEY_SESSION_ID
import com.soundsleeper.app.util.PreferencesKeys.Session.KEY_START_TIME

/**
 * 지금 진행 중인 측정 세션의 식별 정보를 설정(ObservableSettings)에 평범한 값으로 저장해 두는 저장소.
 * 앱이 재시작되거나 프로세스가 죽었다가 돌아왔을 때도 "측정 중인 세션이 있었는지"를
 * 알 수 있게 하기 위한 것으로, [AndroidTrackingManager]가 측정 시작/종료 시점에 쓰고 지운다.
 */
class ActiveSessionStore(
    private val settings: ObservableSettings
) {
    fun save(
        sessionId: String,
        startTimeMillis: Long,
        duration: Int,
        musicName: String?
    ) {
        settings.putString(KEY_SESSION_ID, sessionId)
        settings.putLong(KEY_START_TIME, startTimeMillis)
        settings.putInt(KEY_DURATION, duration)
        musicName?.let { settings.putString(KEY_MUSIC_NAME, it) }
            ?: settings.remove(KEY_MUSIC_NAME)
    }
    fun clear() {
        settings.remove(KEY_SESSION_ID)
        settings.remove(KEY_START_TIME)
        settings.remove(KEY_DURATION)
        settings.remove(KEY_MUSIC_NAME)
    }
}