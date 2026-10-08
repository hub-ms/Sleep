package com.soundsleeper.app.enum_

/** 사용자가 고르는 표시 언어. SYSTEM 은 기기 언어를 따른다. */
enum class AppLanguage {
    SYSTEM, KO, EN;

    companion object {
        fun fromKey(key: String?): AppLanguage = entries.find { it.name == key } ?: SYSTEM
    }
}
