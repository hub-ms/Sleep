package com.sleepytime.shared.platform

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AesGcmSecureStorage : SecureStorage {

    companion object {
        private const val ANDROID_KEYSTORE  = "AndroidKeyStore"
        private const val KEY_ALIAS         = "sleepytime_token_key"
        private const val AES_MODE          = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH    = 128
        private const val IV_SIZE           = 12
    }

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(AES_MODE).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        }
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined  = cipher.iv + encrypted        // IV(12바이트) || 암호문
        return Base64.getEncoder().encodeToString(combined)
    }

    override fun decrypt(encryptedText: String): String? = runCatching {
        val decoded = Base64.getDecoder().decode(encryptedText)
        val iv      = decoded.sliceArray(0 until IV_SIZE)
        val data    = decoded.sliceArray(IV_SIZE until decoded.size)

        val cipher = Cipher.getInstance(AES_MODE).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            ).apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
                generateKey()
            }
        }

        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }
}