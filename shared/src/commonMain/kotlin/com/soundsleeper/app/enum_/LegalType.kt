package com.soundsleeper.app.enum_

/**
 * 법적 고지 문서 종류.
 *
 * [path] 는 화면 제목이자 **서버 경로이자 번들 파일명**이다
 * (`LegalApi` 의 `legal/${type.path}`, `BundledLegalSource` 의 `files/${type.path}.json`,
 * `LegalCache` 의 저장 키). 한국어 문자열이 그대로 식별자 역할을 하므로 건드리면
 * 원격 조회·캐시·번들 로딩이 한꺼번에 깨진다.
 *
 * [notionUrl] 은 웹뷰로 먼저 띄울 공개 문서 주소다. 비어 있거나 로딩에 실패하면
 * 기존 네이티브 렌더링으로 떨어진다 — 문서를 아직 발행하지 않았어도 화면은 동작한다.
 */
enum class LegalType(val path: String, val notionUrl: String) {
    TERMS("이용약관", "https://somniotune.notion.site/terms-ko"),
    PRIVACY("개인정보처리방침", "https://somniotune.notion.site/privacy-ko");

    companion object {
        fun fromPath(path: String): LegalType? = entries.firstOrNull { it.path == path }
    }
}
