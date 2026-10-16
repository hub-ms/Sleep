package com.soundsleeper.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
/**
 * 약관·방침 본문의 표 한 줄.
 *
 * 화면은 열을 나란히 그리지 않고 label → value → note 를 위에서 아래로 쌓는다. 휴대폰 폭에
 * 3열 표를 넣으면 글자가 한 글자씩 끊기기 때문이다.
 *
 * [note] 는 세 번째 열이 있는 표에만 쓴다(개인정보처리방침의 "수집 방법", "이전 시기 및 보유
 * 기간"). 기본값이 null 이라 2열 표를 담고 있던 기존 JSON 과 그대로 호환된다.
 */
data class LegalTableRow(
    val label: String,
    val value: String,
    val note: String? = null,
)