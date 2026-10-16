package com.soundsleeper.app.enum_

/**
 * 법적 고지 문서 종류.
 *
 * [path] 는 **서버 경로이자 번들 파일명이자 캐시 키**다
 * (`LegalApi` 의 `legal/${type.path}`, `BundledLegalSource` 의 `files/${type.path}.json`,
 * `LegalCache` 의 저장 키, 서버 `LegalService` 의 `legal/${type.path}.json`).
 * 하나라도 어긋나면 원격 조회·캐시·번들 로딩이 한꺼번에 깨지고, 서버는 404 가 아니라
 * 기동 실패로 터진다.
 *
 * 화면에 보이는 이름은 여기 두지 않는다 — 이 enum 은 `:spring` 도 함께 쓰는 공용 타입이라,
 * Compose Resources 를 끌어오면 서버 모듈이 UI 리소스에 의존하게 된다.
 * 표시 이름은 `LegalType.titleRes`(ui/setting/LegalDocumentScreen.kt) 확장에 있다.
 *
 * 원문은 GitHub Pages 에 공개돼 있다(스토어 등록용):
 * - https://soundsleeper.github.io/terms/
 * - https://soundsleeper.github.io/privacy/
 *
 * 다만 **앱은 그 페이지를 읽지 않는다.** 예전에 Notion 공개 페이지를 웹뷰로 띄우던 코드가
 * 있었는데, 미발행 주소에도 Notion 이 200 과 자사 마케팅 셸을 주는 바람에 실패를 감지하지
 * 못해 빈 화면만 보였다. 지금은 아래 JSON 만이 앱이 보는 원본이다.
 *
 * ## 문서를 개정할 때
 * 1. GitHub Pages 원문을 고친다.
 * 2. `shared/src/commonMain/composeResources/files/{terms,privacy}.json` 를 갱신한다.
 *    이 두 파일이 유일한 원본이고, 서버 쪽 `legal` 리소스는 빌드 때 여기서 복사된
 *    생성물이다(`spring/build.gradle.kts` 의 processResources).
 * 3. **`version` 을 반드시 올린다**(`yyyy-MM-dd`). `effectiveDate`·`updatedDate` 도 함께.
 *    `LegalRepositoryImpl` 이 `cached.version >= bundled.version` 으로 고르기 때문에,
 *    내용만 바꾸고 version 을 그대로 두면 이미 캐시를 가진 기기는 새 문서를 영영 못 본다.
 */
enum class LegalType(val path: String) {
    TERMS("terms"),
    PRIVACY("privacy");

    companion object {
        fun fromPath(path: String): LegalType? = entries.firstOrNull { it.path == path }
    }
}
