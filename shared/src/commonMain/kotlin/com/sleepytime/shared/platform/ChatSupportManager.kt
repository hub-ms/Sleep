package com.sleepytime.shared.platform

interface ChatSupportManager {
    fun boot(userId: String?, email: String?, nickname: String?)
    fun showMessenger()
    fun shutdown()
}