package com.sleepytime.shared.data.remote.api

import com.sleepytime.shared.data.local.dao.UserDao
import com.sleepytime.shared.data.local.mapper.toUserEntity
import com.sleepytime.shared.data.remote.dto.request.*
import com.sleepytime.shared.data.remote.dto.response.*
import com.sleepytime.shared.data.remote.mapper.responseToUser
import com.sleepytime.shared.enum_.AuthProvider
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*

class AuthApi(
    private val client: HttpClient,
    private val userDao: UserDao
) {

    suspend fun socialLogin(provider: AuthProvider, accessToken: String): Result<AuthInfoResponse> = runCatching {
        Napier.i("API: socialLogin 시작 - provider=$provider")
        val response = client.post("auth/social/$provider") {
            contentType(ContentType.Application.Json)
            header("X-Social-Token", accessToken)
        }
        if (response.status.value >= 400) {
            val errorBody = response.bodyAsText()
            Napier.e("socialLogin API 에러 - Status: ${response.status}, Body: $errorBody")
            throw Exception("Social login failed: ${response.status} - $errorBody")
        }
        response.body<AuthInfoResponse>().also { Napier.i("API: socialLogin 성공") }
    }.onFailure { Napier.e("API: socialLogin 예외 - ${it.message}", it) }

    suspend fun connectSocial(request: SocialConnectRequest): Result<Unit> = runCatching {
        val response = client.post("auth/social/connect") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.value >= 400) {
            val errorBody = response.bodyAsText()
            Napier.e("connectSocial 에러 - Status: ${response.status}, Body: $errorBody")
            throw Exception("Connect social failed: ${response.status}")
        }
    }.onFailure { Napier.e("connectSocial 예외: ${it.message}", it) }

    suspend fun getUserInfo(): Result<UserResponse> = runCatching {
        client.get("user/info").body<UserResponse>()
    }.onFailure { Napier.e("getUserInfo 예외: ${it.message}", it) }

    suspend fun sendAuthCode(email: String): Result<Unit> = runCatching {
        val response = client.post("auth/email/send") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("email" to email))
        }
        if (response.status.value >= 400) throw Exception("Send auth code failed: ${response.status}")
    }.onFailure { Napier.e("sendAuthCode 예외: ${it.message}", it) }

    suspend fun verifyAuthCode(request: EmailVerifyRequest): Result<AuthInfoResponse> = runCatching {
        val response = client.post("auth/email/verify") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.value >= 400) {
            val errorBody = response.bodyAsText()
            Napier.e("verifyAuthCode 에러 - Status: ${response.status}, Body: $errorBody")
            throw Exception("Verify auth code failed: ${response.status}")
        }
        response.body<AuthInfoResponse>()
    }.onFailure { Napier.e("verifyAuthCode 예외: ${it.message}", it) }

    suspend fun verifyEmailToken(token: String): Result<AuthInfoResponse> = runCatching {
        val response = client.get("auth/email/verify-token") { parameter("token", token) }
        if (response.status.value >= 400) throw Exception("Verify email token failed: ${response.status}")
        response.body<AuthInfoResponse>()
    }.onFailure { Napier.e("verifyEmailToken 예외: ${it.message}", it) }

    suspend fun connectEmail(request: EmailConnectRequest): Result<HttpResponse> = runCatching {
        client.post("auth/email/connect") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.onFailure { Napier.e("connectEmail 예외: ${it.message}", it) }

    suspend fun logout(): Result<Unit> = runCatching {
        client.post("auth/logout")
        Unit
    }.onFailure { Napier.w("logout 예외: ${it.message}") } // 실패해도 로컬 정리는 Repository가 계속 진행

    suspend fun withdraw(reason: String? = null): Result<Unit> = runCatching {
        val response = client.delete("auth/withdraw") {
            if (reason != null) parameter("reason", reason)
        }
        if (response.status.value >= 400) throw Exception("Withdraw failed: ${response.status}")
    }.onFailure { Napier.e("withdraw 예외: ${it.message}", it) }

    suspend fun refreshToken(refreshToken: String): Result<AuthInfoResponse> = runCatching {
        val response = client.post("auth/refresh") {
            header(HttpHeaders.Authorization, "Bearer $refreshToken")
        }
        if (response.status.value >= 400) {
            val errorBody = response.bodyAsText()
            Napier.e("refreshToken 에러 - Status: ${response.status}, Body: $errorBody")
            throw Exception("Refresh token failed: ${response.status}")
        }
        response.body<AuthInfoResponse>()
    }.onFailure { Napier.e("refreshToken 예외: ${it.message}", it) }

    suspend fun changePrimaryProvider(provider: AuthProvider): Result<HttpResponse> = runCatching {
        client.post("auth/change-primary-provider") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("provider" to provider))
        }
    }.onFailure { Napier.e("changePrimaryProvider 예외: ${it.message}", it) }

    suspend fun disconnectProvider(provider: AuthProvider): Result<HttpResponse> = runCatching {
        client.post("auth/disconnect-provider") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("provider" to provider))
        }
    }.onFailure { Napier.e("disconnectProvider 예외: ${it.message}", it) }

    suspend fun disconnectEmail(): Result<HttpResponse> = runCatching {
        client.post("auth/disconnect-email")
    }.onFailure { Napier.e("disconnectEmail 예외: ${it.message}", it) }

    suspend fun refreshSocialProfile(provider: AuthProvider, socialAccessToken: String): Result<UserResponse> = runCatching {
        val response = client.post("auth/social-profile/$provider/refresh") {
            contentType(ContentType.Application.Json)
            header("X-Social-Token", socialAccessToken)
        }

        if (response.status.value >= 400) {
            val errorBody = response.bodyAsText()
            Napier.e("refreshSocialProfile API 에러 - Status: ${response.status}, Body: $errorBody")
            throw Exception("HTTP ${response.status.value}: Refresh social profile failed")
        }

        response.body<UserResponse>()
    }.onFailure { Napier.e("refreshSocialProfile 예외: ${it.message}", it) }

    suspend fun updateProfile(
        nickname: String?,
        email: String?,
        imageBytes: ByteArray?
    ): Result<Unit> = runCatching {
        val response = client.post("auth/me") {
            setBody(MultiPartFormDataContent(
                formData {
                    nickname?.let { append("nickname", it) }
                    email?.let { append("email", it) }
                    imageBytes?.let {
                        append("profileImage", it, Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"profile.jpg\"")
                        })
                    }
                }
            ))
        }
        if (response.status.value >= 400) {
            val errorBody = response.bodyAsText()
            throw Exception("Update profile failed: ${response.status} - $errorBody")
        }
    }.onFailure { Napier.e("updateProfile 예외: ${it.message}", it) }
}
