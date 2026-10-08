package com.soundsleeper.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 아이콘 크기 기준값.
 *
 * 예전에는 상수가 아예 없어서 호출부마다 14/16/18/20/24/28/32dp 가 흩어져 있었고,
 * 같은 역할의 아이콘이 화면마다 다른 크기로 보였다.
 *
 * [Default] 가 기준이다. [Inline] 과 [Dense] 는 "작게 두고 싶어서"가 아니라
 * 32dp 를 넣으면 레이아웃이 깨지는 자리를 위한 것이므로, 새 코드에서는
 * 되도록 [Default] 를 쓴다.
 */
object IconSize {
    /** 독립적으로 놓이거나 내비게이션 역할을 하는 아이콘. 기본으로 이것을 쓴다. */
    val Default = 32.dp

    /**
     * caption(12sp)·bodyMedium(14sp) 글자와 같은 줄에 놓이는 아이콘.
     * 32dp 로 올리면 글자보다 두 배 이상 커져 줄 높이가 무너진다.
     */
    val Inline = 16.dp

    /**
     * 행 높이를 지켜야 하는 표 안의 아이콘(페이월 무료/프리미엄 비교표).
     * 여기서 32dp 를 쓰면 8개 행이 한 화면에 들어오지 않는다.
     */
    val Dense = 18.dp
}
