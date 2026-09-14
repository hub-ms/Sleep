package com.sleepytime.app.controller_new

import com.sleepytime.app.service_new.AuthService
import com.sleepytime.shared.data.remote.dto.request.EmailConnectRequest
import com.sleepytime.shared.data.remote.dto.request.EmailVerifyRequest
import com.sleepytime.shared.data.remote.dto.request.SocialConnectRequest
import com.sleepytime.shared.data.remote.dto.response.AuthInfoResponse
import com.sleepytime.shared.data.remote.dto.response.UserResponse
import com.sleepytime.shared.enum_.AuthProvider
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@CrossOrigin(origins = ["*", "http://192.168.219.104:8080", "https://hub-ms.github.io"])
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/social/{provider}")
    fun socialLogin(
        @PathVariable provider: AuthProvider,
        @RequestHeader("X-Social-Token") authorization: String
    ): ResponseEntity<AuthInfoResponse> {
        val accessToken = authorization.removePrefix("Bearer ")
        val response = authService.socialLogin(provider, accessToken)
        return ResponseEntity.ok(response)
    }
    @PostMapping("/connect")
    fun connectSocial(
        @AuthenticationPrincipal principal: User?,
        @RequestBody request: SocialConnectRequest
    ): ResponseEntity<String> {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Logged in user can only access")
        }

        val userId = principal.username.toLong()

        try {
            authService.connectSocial(userId, request.provider, request.accessToken)
            return ResponseEntity.ok().build()
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to connect social account: ${e.message}")
        }
    }

    @PostMapping("/email/send")
    fun sendAuthCode(@RequestBody email: String): ResponseEntity<Unit> {
        log.debug("authcode requested: $email")
        authService.sendAuthCode(email)
        return ResponseEntity.ok(Unit)
    }

    @PostMapping("/email/verify")
    fun verifyAuthCode(@RequestBody request: EmailVerifyRequest): ResponseEntity<Any> {
        log.debug("인증 요청 데이터: email={}, code={}", request.email, request.code)
        return try {
            val response = authService.verifyAuthCode(request)
            log.debug("response: {}", response)
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            log.debug("인증 실패: ${e.message}")
            ResponseEntity.badRequest().body(e.message)
        } catch (e: Exception) {
            log.error("인증 처리 중 에러: email={}", request.email, e)
            ResponseEntity.internalServerError().build()
        }
    }
    @PostMapping("/connect/email")
    fun connectEmail(
        @RequestHeader("Authorization") token: String,
        @RequestBody request: EmailConnectRequest
    ): ResponseEntity<Void> {

        val jwt = token.removePrefix("Bearer ")
        authService.connectEmail(jwt, request.emailToken)

        return ResponseEntity.ok().build()
    }

    @PostMapping("/refresh")
    fun refreshToken(@RequestBody refreshToken: String): ResponseEntity<AuthInfoResponse> {
        val response = authService.refreshToken(refreshToken)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/logout")
    fun logout(@RequestHeader("Authorization") authHeader: String?): ResponseEntity<Unit> {
        val accessToken = authHeader?.removePrefix("Bearer ")
        val authentication = SecurityContextHolder.getContext().authentication ?: return ResponseEntity.ok(Unit)
        authService.logout(authentication, accessToken)
        return ResponseEntity.ok(Unit)
    }

    @PostMapping("/me")
    fun updateProfile(
        @AuthenticationPrincipal principal: User?,
        @RequestParam(required = false) nickname: String?,
        @RequestParam(required = false) email: String?,
        @RequestPart(required = false) profileImage: org.springframework.web.multipart.MultipartFile?
    ): ResponseEntity<Void> {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val userId = principal.username.toLong()

        // 💡 실제로는 S3 등에 업로드 후 URL을 받아와야 합니다.
        // 여기서는 학습용으로 임시 경로 또는 base64 변환 등의 로직이 들어갈 자리입니다.
        val imageUrl = if (profileImage != null && !profileImage.isEmpty) {
            "https://your-storage.com/profiles/${userId}_${profileImage.originalFilename}"
        } else null

        authService.updateProfile(userId, nickname, email, imageUrl)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/withdraw")
    fun withdraw(
        @AuthenticationPrincipal principal: User?,
        @RequestHeader("Authorization") accessToken: String,
        @RequestParam(required = false) reason: String?
    ): ResponseEntity<Unit> {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val userId = principal.username.toLong()
        authService.withdraw(userId, accessToken.removePrefix("Bearer "), reason)
        return ResponseEntity.ok().build()
    }
    @PostMapping("/primary-provider")
    fun changePrimaryProvider(
        @RequestHeader("Authorization") token: String,
        @RequestBody provider: AuthProvider
    ): ResponseEntity<Void> {
        authService.changePrimaryProvider(token, provider)
        return ResponseEntity.ok().build()
    }
    @DeleteMapping("/provider")
    fun disconnectProvider(
        @RequestHeader("Authorization") token: String,
        @RequestParam provider: AuthProvider
    ): ResponseEntity<Void> {
        authService.disconnectProvider(token, provider)
        return ResponseEntity.ok().build()
    }
    @PostMapping("/email")
    fun disconnectEmail(
        @RequestHeader("Authorization") token: String
    ): ResponseEntity<Void> {
        authService.disconnectEmail(token)
        return ResponseEntity.ok().build()
    }

    @RequestMapping("/me")
    fun getMyPageInfo(
        @AuthenticationPrincipal principal: User?
    ): ResponseEntity<AuthInfoResponse> {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val userId = principal.username.toLong()
        val response = authService.getCurrentUser(userId)
        return ResponseEntity.ok(response)
    }
    @PostMapping("/social-profile/{provider}/refresh")
    fun refreshSocialProfile(
        @PathVariable provider: AuthProvider,
        @RequestHeader("X-Social-Token") socialAccessToken: String,
        principal: User // JwtAuthenticationFilter가 UserDetails를 세팅함
    ): ResponseEntity<UserResponse> {
        val userId = principal.username.toLong()
        val updatedUser = authService.refreshSocialProfile(userId, provider, socialAccessToken)
        return ResponseEntity.ok(updatedUser)
    }
}