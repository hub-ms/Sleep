package com.soundsleeper.app.ui.setting

import androidx.annotation.RawRes

/**
 * 라이선스 목록 JSON 의 raw 리소스 id 를 앱 모듈에서 받아 둔다.
 *
 * AboutLibraries Gradle 플러그인은 `:androidApp` 에서 돌아야 한다(`:shared` 는 Android variant
 * 모델이 없어 의존성을 하나도 수집하지 못한다 — androidApp/build.gradle.kts 주석 참고).
 * 그래서 생성된 `res/raw/aboutlibraries.json` 은 `:androidApp` 의 R 에 들어가고,
 * 의존 방향이 androidApp → shared 이므로 이 모듈에서는 그 R 을 참조할 수 없다.
 *
 * 리소스를 이름으로 찾는 방법(`Resources.getIdentifier`)은 쓰지 않는다. 릴리스 빌드가
 * `isShrinkResources = true` 라서, 정적으로 참조되지 않는 raw 리소스는 제거되고 목록이
 * 조용히 비어 버린다. 앱 모듈이 `R.raw.aboutlibraries` 를 **컴파일 시점에** 넘겨주면
 * 그 참조가 남아 R8 이 리소스를 지우지 않는다.
 *
 * [SleepApp] 이 `onCreate` 에서 채운다. 비어 있으면 목록 대신 빈 화면을 그린다.
 */
object AboutLibrariesRes {
    @RawRes
    var rawResId: Int? = null
}
