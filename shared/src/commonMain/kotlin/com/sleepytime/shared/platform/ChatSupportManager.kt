package com.sleepytime.shared.platform

interface ChatSupportManager {
    suspend fun boot(userId: String?, email: String?, nickname: String?, memberHash: String?): Boolean
    fun showMessenger()
    fun shutdown()
}