package com.soundsleeper.app.data.remote.api

import com.soundsleeper.app.data.remote.dto.request.*
import com.soundsleeper.app.data.remote.dto.response.*
import com.soundsleeper.app.enum_.AuthProvider
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
) {

    /**
     * @param restore 탈퇴 유예 기간이 남은 계정을 되살리며 로그인할지. 사용자가 복구에
     * 동의한 뒤에만 true 로 보낸다.
     */
    suspend fun socialLogin(
        provider: AuthProvider,
        accessToken: String,
        restore: Boolean = false,
    ): Result<AuthInfoResponse> = runCatching {
        Napier.i("API: socialLogin 시작 - provider=$provider")
        val response = client.post("auth/social/$provider") {
            contentType(ContentType.Application.Json)
            header("X-Social-Token", accessToken)
            if (restore) parameter("restore", "true")
        }
        // 서버는 탈퇴한 계정에 410 Gone 을 돌려준다. 그냥 두면 "Social login failed: 410"
        // 같은 문자열이 되어 화면이 복구 가능 여부를 판단할 수 없다.
        if (response.status == HttpStatusCode.Gone) {
            throw WithdrawnAccountError(response.bodyAsText().ifBlank { "탈퇴한 계정입니다." })
        }
        if (response.status.value >= 400) {
            val errorBody = response.bodyAsText()
            Napier.e("socialLogin API 에러 - Status: ${response.status}, Body: $errorBody")
            throw Exception("Social login failed: ${response.status} - $errorBody")
        }
        response.body<AuthInfoResponse>().also { Napier.i("API: socialLogin 성공") }
    }.onFailure { Napier.e("API: socialLogin 예외 - ${it.message}", it) }

    suspend fun connectSocial(request: SocialConnectRequest): Result<Unit> = runCatching {
        val response = client.post("auth/connect/social") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.value >= 400) {
            val errorBody = response.bodyAsText()
            Napier.e("connectSocial 에러 - Status: ${response.status}, Body: $errorBody")
            throw Exception("Connect social failed: ${response.status}")
        }
    }.onFailure { Napier.e("connectSocial 예외: ${it.message}", it) }
    suspend fun getChannelTalkHash(): Result<ChannelTalkHashResponse> = runCatching {
        client.get("auth/channel-talk/hash").body<ChannelTalkHashResponse>()
    }.onFailure { Napier.e("getChannelTalkHash 예외: ${it.message}", it) }

    suspend fun sendAuthCode(email: String): Result<Unit> = runCatching {
        val response = client.post("auth/email/send") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("email" to email))
        }
        if (response.status.value >= 400) throw Exception("Send auth code failed: ${response.status}")
    }.onFailure { Napier.e("sendAuthCode 예외: ${it.message}", it) }
    /** 메일로 받은 6자리 코드로 로그인/가입. 서버의 AuthService.verifyAuthCode 와 짝이다. */
    suspend fun verifyAuthCode(request: EmailVerifyRequest): Result<AuthInfoResponse> = runCatching {
        val response = client.post("auth/email/verify") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.value >= 400) {
            val errorBody = response.bodyAsText()
            Napier.e("verifyAuthCode 에러 - Status: ${response.status}, Body: $errorBody")
            throw UserFacingApiException(
                errorBody.takeIf { it.isNotBlank() }?.take(300) ?: "인증코드가 올바르지 않습니다."
            )
        }
        response.body<AuthInfoResponse>()
    }.onFailure { Napier.e("verifyAuthCode 예외: ${it.message}", it) }

    suspend fun verifyEmailToken(token: String): Result<AuthInfoResponse> = runCatching {
        val response = client.get("auth/email/verify-token") { parameter("token", token) }
        if (response.status.value >= 400) throw Exception("Verify email token failed: ${response.status}")
        response.body<AuthInfoResponse>()
    }.onFailure { Napier.e("verifyEmailToken 예외: ${it.message}", it) }

    suspend fun connectEmail(request: EmailConnectRequest): Result<HttpResponse> = runCatching {
        client.post("auth/connect/email") {
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
            // 응답 본문에 토큰이나 서버 내부 정보가 섞여 나올 수 있어 상태코드만 남긴다.
            Napier.e("refreshToken 에러 - Status: ${response.status}")
            throw Exception("Refresh token failed: ${response.status}")
        }
        response.body<AuthInfoResponse>()
    }.onFailure { Napier.e("refreshToken 예외: ${it.message}", it) }

    suspend fun changePrimaryProvider(provider: AuthProvider): Result<HttpResponse> = runCatching {
        client.post("auth/change/$provider") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("provider" to provider))
        }
    }.onFailure { Napier.e("changePrimaryProvider 예외: ${it.message}", it) }

    /**
     * 클라이언트에 expectSuccess가 꺼져 있어 409도 예외 없이 돌아온다. 그대로 두면 호출부가
     * Result.success로 받아 해제되지 않은 provider를 UI에서 지워버리므로 여기서 상태를 해석한다.
     */
    private suspend fun HttpResponse.orThrowDisconnectFailure(what: String): HttpResponse {
        if (status == HttpStatusCode.Conflict) {
            throw LastAuthMethodError(bodyAsText().ifBlank { "마지막 로그인 수단은 해제할 수 없습니다." })
        }
        if (!status.isSuccess()) {
            val errorBody = bodyAsText()
            Napier.e("$what 에러 - Status: $status, Body: $errorBody")
            throw Exception("$what failed: $status")
        }
        return this
    }

    suspend fun disconnectSocial(provider: AuthProvider): Result<HttpResponse> = runCatching {
        client.post("auth/social/disconnect/$provider") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("provider" to provider))
        }.orThrowDisconnectFailure("disconnectSocial")
    }.onFailure { Napier.e("disconnectSocial 예외: ${it.message}", it) }

    suspend fun disconnectEmail(): Result<HttpResponse> = runCatching {
        client.post("auth/email/disconnect").orThrowDisconnectFailure("disconnectEmail")
    }.onFailure { Napier.e("disconnectEmail 예외: ${it.message}", it) }

    suspend fun updateProfile(
        nickname: String? = null,
        email: String? = null,
        imageBytes: ByteArray? = null,
        resetImage: Boolean = false,
        socialProvider: AuthProvider? = null,
        socialAccessToken: String? = null,
    ): Result<UserResponse> = runCatching {
        // resetImage가 빠져 있어서 "기본 이미지로 변경"(다른 인자는 전부 null)이 항상 여기서 막혔다.
        if (nickname == null && email == null && imageBytes == null && socialProvider == null && !resetImage) {
            throw UserFacingApiException("변경할 항목이 없습니다")
        }
        val response = client.post("user/profile") {
            socialAccessToken?.let { header("X-Social-Token", it) }
            setBody(MultiPartFormDataContent(formData {
                nickname?.let { append("nickname", it) }
                email?.let { append("email", it) }
                socialProvider?.let { append("socialProvider", it.name) }
                if (resetImage) append("resetImage", "true")
                imageBytes?.let {
                    append("profileImage", it, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"profile.jpg\"")
                    })
                }
            }))
        }
        if (response.status.value >= 400) {
            val errorBody = response.bodyAsText()
            Napier.e("updateProfile 에러 - Status: ${response.status}, Body: $errorBody")
            // 4xx 는 사용자가 고칠 수 있는 거절(닉네임 길이·중복·변경 쿨다운 등)이라 서버 문구를
            // 그대로 보여준다. 5xx 는 사용자가 할 수 있는 게 없고, 서버가 내려가 있을 때
            // 게이트웨이가 돌려주는 "프로필 저장 실패 (502): <HTML>" 같은 문자열을 그대로
            // 띄우면 기능이 고장 난 것처럼 읽힌다.
            throw UserFacingApiException(
                if (response.status.value >= 500) SERVER_UNREACHABLE_MESSAGE
                else errorBody.takeIf { it.isNotBlank() }?.take(300) ?: "프로필 저장에 실패했습니다."
            )
        }
        response.body<UserResponse>()
    }.recoverCatching { error ->
        Napier.e("updateProfile 예외: ${error.message}", error)
        // 서버에 닿지도 못한 경우(터널 다운, 기내 모드 등)의 예외 메시지는 사용자에게
        // 아무 의미가 없다. 위에서 이미 다듬어 던진 메시지만 그대로 통과시킨다.
        throw if (error is UserFacingApiException) error
        else UserFacingApiException(SERVER_UNREACHABLE_MESSAGE)
    }

    companion object {
        const val SERVER_UNREACHABLE_MESSAGE = "서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요."
    }
}

/** 메시지를 화면에 그대로 보여줘도 되는 실패. 그 밖의 예외는 사용자에게 번역해서 보여준다. */
class UserFacingApiException(message: String) : Exception(message)
