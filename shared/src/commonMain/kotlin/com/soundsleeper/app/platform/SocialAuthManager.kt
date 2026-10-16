package com.soundsleeper.app.platform

expect class SocialAuthManager {
    suspend fun getGoogleToken(): String?
    suspend fun getKakaoToken(): String?
    suspend fun getAppleToken(): String?

    /**
     * OS 가 알고 있는 이 기기의 이메일 주소. 모르거나 사용자가 고르지 않으면 null.
     *
     * 최초 이메일 가입 화면에서 "이 주소로 가입할까요?" 를 띄우는 데 쓴다. 키보드를 한 번도
     * 열지 않고 가입을 끝내게 하는 것이 목적이라, 주소를 **보여주기만** 하고 그대로
     * 인증 메일을 보낸다.
     *
     * 안드로이드에서는 `GET_ACCOUNTS` 권한이나 AccountManager 를 쓰지 않는다. 그 권한은
     * 런타임 요청이 필요한 위험 권한인 데다 기기의 모든 계정을 읽는다. 대신 이미 구글
     * 로그인에 쓰고 있는 Credential Manager 로 계정 선택 시트를 띄운다 - 사용자가 직접
     * 고른 하나만 넘어오고, 고르지 않으면 null 이다.
     */
    suspend fun getDeviceEmailHint(): String?
}
