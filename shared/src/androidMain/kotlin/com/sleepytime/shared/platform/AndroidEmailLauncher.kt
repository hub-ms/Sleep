package com.sleepytime.shared.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class AndroidEmailLauncher(private val context: Context) : EmailLauncher {
    override fun openEmailApp(email: String) {
        val domain = email.substringAfterLast('@', "")
        val appUri = when (domain) {
            "gmail.com" -> "googlegmail://"
            "naver.com" -> "naveremail://"
            "daum.net", "hanmail.net" -> "daummail://"
            "kakao.com" -> "content://com.kakao.talk/mail"
            else -> null
        }

        val intent = if (!appUri.isNullOrEmpty()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(appUri))
        } else {
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_EMAIL)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("EmailAuth", "Failed to open email app", e)
        }
    }
}
