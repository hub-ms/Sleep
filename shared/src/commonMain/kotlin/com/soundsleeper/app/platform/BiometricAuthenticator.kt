package com.soundsleeper.app.platform

/**
 * 기기의 생체 인증(지문·얼굴)으로 사용자를 확인한다.
 *
 * 진짜 패스키(WebAuthn)가 아니다. 서버에 공개키를 등록하고 challenge 를 주고받는 대신,
 * **이미 암호화해 저장해 둔 refresh token 을 꺼내 쓰기 전에 사람이 맞는지만 묻는다.**
 * 토큰은 예전부터 [SecureStorage](AES-GCM, Android Keystore)로 암호화돼
 * `TokenRepositoryImpl` 에 들어 있었으므로 저장 쪽은 새로 만들 것이 없다.
 *
 * 그래서 이 인증이 보호하는 범위도 거기까지다 - "이 기기에 저장된 세션을 복구해도 되는가".
 * 서버는 여전히 평소처럼 refresh token 만 검증한다.
 */
interface BiometricAuthenticator {
    /** 기기에 등록된 생체 정보가 있어 인증을 띄울 수 있는지. */
    suspend fun isAvailable(): Boolean

    /**
     * 생체 인증 다이얼로그를 띄우고 결과를 기다린다.
     *
     * @return 사용자가 본인 확인에 성공하면 true. 취소·실패·하드웨어 부재는 모두 false 다
     *   (호출부는 셋을 구분할 이유가 없다 - 어느 쪽이든 평소 로그인으로 보내면 된다).
     */
    suspend fun authenticate(title: String, subtitle: String, cancelLabel: String): Boolean
}
