package com.soundsleeper.app.enum_

enum class LoginButtonType {
    /** 아이콘만. 좁은 자리에 여러 개를 늘어놓을 때. */
    Circle,

    /** 화면 폭을 다 쓰는 주 CTA. 아이콘은 왼쪽 끝, 라벨은 가운데. */
    FullWidth,

    /**
     * 둘을 가로로 반씩 나눠 놓을 때.
     *
     * [FullWidth] 를 반 폭에 그대로 쓰면 가운데 정렬된 라벨과 왼쪽 끝 아이콘이 겹친다
     * (에뮬레이터에서 확인). 이쪽은 아이콘과 짧은 공급자 이름을 한 Row 에 나란히 둔다.
     */
    Compact,
}
