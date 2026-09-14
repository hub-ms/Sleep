package com.sleepytime.shared.platform

interface SecureStorage {
    fun encrypt(plainText: String): String
    fun decrypt(encryptedText: String): String?
}