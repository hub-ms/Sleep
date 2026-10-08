package com.soundsleeper.app.ui.setting

import cafe.adriel.voyager.core.model.ScreenModel
import com.russhwolf.settings.ObservableSettings
import com.soundsleeper.app.enum_.AppLanguage
import com.soundsleeper.app.util.PreferencesKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 언어 설정을 읽고 쓴다.
 *
 * MainActivity 는 Composable 이 아니라서 설정 화면들처럼 koinInject 로 바로 값을 읽을 수
 * 없다. 이 ViewModel 하나를 양쪽(설정 화면, MainActivity)이 함께 주입받아 상태를 공유한다 —
 * Koin 싱글턴이므로 설정 화면에서 바꾸면 MainActivity 의 StateFlow 도 즉시 갱신된다.
 */
class AppearanceViewModel(
    private val settings: ObservableSettings,
) : ScreenModel {
    private val _language = MutableStateFlow(
        AppLanguage.fromKey(settings.getStringOrNull(PreferencesKeys.Appearance.LANGUAGE))
    )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        settings.putString(PreferencesKeys.Appearance.LANGUAGE, language.name)
        _language.value = language
    }
}
