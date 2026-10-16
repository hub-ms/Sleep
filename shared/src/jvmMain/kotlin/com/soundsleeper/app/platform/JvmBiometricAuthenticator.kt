package com.soundsleeper.app.platform

/** JVM 타깃은 테스트·Spring 용이다. 생체 인증 하드웨어가 없다. */
class JvmBiometricAuthenticator : BiometricAuthenticator {
    override suspend fun isAvailable(): Boolean = false
    override suspend fun authenticate(title: String, subtitle: String, cancelLabel: String) = false
}
