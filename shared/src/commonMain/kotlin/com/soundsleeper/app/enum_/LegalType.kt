package com.soundsleeper.app.enum_

enum class LegalType(val path: String) {
    TERMS("이용약관"),
    PRIVACY("개인정보처리방침");

    companion object {
        fun fromPath(path: String): LegalType? = entries.firstOrNull { it.path == path }
    }
}