package com.sleepytime.shared.data.remote.api

import com.sleepytime.shared.data.remote.dto.request.*
import com.sleepytime.shared.data.remote.dto.response.*
import com.sleepytime.shared.enum_.AuthProvider
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*

class AuthApi(private val client: HttpClient) {
    suspend fun socialLogin(provider: AuthProvider, accessToken: String): AuthInfoResponse {
        try {
            Napier.i("API: socialLogin 시작 - provider=$provider")

            val response = client.post("/auth/social/$provider") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            if (response.status.value >= 400) {
                val errorBody = response.bodyAsText()
                Napier.e("socialLogin API 에러 - Status: ${response.status}, Body: $errorBody")
                throw Exception("Social login failed: ${response.status} - $errorBody")
            }

            val result = response.body<AuthInfoResponse>()
            Napier.i("API: socialLogin 성공")
            return result
        } catch (e: Exception) {
            Napier.e("API: socialLogin 예외 - ${e.message}", e)
            throw e
        }
    }

    suspend fun connectSocial(token: String, request: SocialConnectRequest) {
        try {
            val response = client.post("/auth/social/connect") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.value >= 400) {
                val errorBody = response.bodyAsText()
                Napier.e("connectSocial 에러 - Status: ${response.status}, Body: $errorBody")
                throw Exception("Connect social failed: ${response.status}")
            }
        } catch (e: Exception) {
            Napier.e("connectSocial 예외: ${e.message}", e)
            throw e
        }
    }

    suspend fun getUserInfo(token: String): UserResponse {
        try {
            return client.get("/user/info") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body()
        } catch (e: Exception) {
            Napier.e("getUserInfo 예외: ${e.message}", e)
            throw e
        }
    }

    suspend fun sendAuthCode(email: String) {
        try {
            val response = client.post("/auth/email/send") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("email" to email))
            }

            if (response.status.value >= 400) {
                throw Exception("Send auth code failed: ${response.status}")
            }
        } catch (e: Exception) {
            Napier.e("sendAuthCode 예외: ${e.message}", e)
            throw e
        }
    }

    suspend fun verifyAuthCode(request: EmailVerifyRequest): AuthInfoResponse {
        try {
            val response = client.post("/auth/email/verify") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.value >= 400) {
                val errorBody = response.bodyAsText()
                Napier.e("verifyAuthCode 에러 - Status: ${response.status}, Body: $errorBody")
                throw Exception("Verify auth code failed: ${response.status}")
            }

            return response.body()
        } catch (e: Exception) {
            Napier.e("verifyAuthCode 예외: ${e.message}", e)
            throw e
        }
    }

    suspend fun verifyEmailToken(token: String): AuthInfoResponse {
        try {
            val response = client.post("/auth/verify-email-token") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("token" to token))
            }

            if (response.status.value >= 400) {
                throw Exception("Verify email token failed: ${response.status}")
            }

            return response.body()
        } catch (e: Exception) {
            Napier.e("verifyEmailToken 예외: ${e.message}", e)
            throw e
        }
    }

    suspend fun connectEmail(token: String, request: EmailConnectRequest) {
        try {
            val response = client.post("/auth/connect/email") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.value >= 400) {
                throw Exception("Connect email failed: ${response.status}")
            }
        } catch (e: Exception) {
            Napier.e("connectEmail 예외: ${e.message}", e)
            throw e
        }
    }

    suspend fun logout() {
        try {
            val response = client.post("/auth/logout")
            if (response.status.value >= 400) {
                Napier.w("logout 경고 - Status: ${response.status}")
                // 로그아웃은 실패해도 로컬 데이터는 정리되어야 함
            }
        } catch (e: Exception) {
            Napier.w("logout 예외: ${e.message}")
            // 로그아웃은 실패해도 계속 진행
        }
    }

    suspend fun withdraw(token: String) {
        try {
            val response = client.delete("/auth/withdraw") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            if (response.status.value >= 400) {
                throw Exception("Withdraw failed: ${response.status}")
            }
        } catch (e: Exception) {
            Napier.e("withdraw 예외: ${e.message}", e)
            throw e
        }
    }

    suspend fun refreshToken(refreshToken: String): AuthInfoResponse {
        try {
            val response = client.post("/auth/refresh") {
                header(HttpHeaders.Authorization, "Bearer $refreshToken")
            }

            if (response.status.value >= 400) {
                val errorBody = response.bodyAsText()
                Napier.e("refreshToken 에러 - Status: ${response.status}, Body: $errorBody")
                throw Exception("Refresh token failed: ${response.status}")
            }

            return response.body()
        } catch (e: Exception) {
            Napier.e("refreshToken 예외: ${e.message}", e)
            throw e
        }
    }

    suspend fun changePrimaryProvider(token: String, provider: AuthProvider) {
        try {
            val response = client.post("/auth/primary-provider") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(mapOf("provider" to provider))
            }

            if (response.status.value >= 400) {
                throw Exception("Change primary provider failed: ${response.status}")
            }
        } catch (e: Exception) {
            Napier.e("changePrimaryProvider 예외: ${e.message}", e)
            throw e
        }
    }

    suspend fun disconnectProvider(token: String, provider: AuthProvider) {
        try {
            val response = client.delete("/auth/provider") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("provider", provider)
            }

            if (response.status.value >= 400) {
                throw Exception("Disconnect provider failed: ${response.status}")
            }
        } catch (e: Exception) {
            Napier.e("disconnectProvider 예외: ${e.message}", e)
            throw e
        }
    }

    suspend fun disconnectEmail(token: String) {
        try {
            val response = client.delete("/auth/email") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            if (response.status.value >= 400) {
                throw Exception("Disconnect email failed: ${response.status}")
            }
        } catch (e: Exception) {
            Napier.e("disconnectEmail 예외: ${e.message}", e)
            throw e
        }
    }
}