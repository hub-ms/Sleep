package com.sleepytime.app.controller_new

import com.sleepytime.app.exception.LastAuthMethodException
import com.sleepytime.app.service_new.AuthService
import com.sleepytime.app.service_new.ChannelTalkService
import com.sleepytime.shared.data.remote.dto.request.EmailConnectRequest
import com.sleepytime.shared.data.remote.dto.request.EmailSendRequest
import com.sleepytime.shared.data.remote.dto.request.SocialConnectRequest
import com.sleepytime.shared.data.remote.dto.response.AuthInfoResponse
import com.sleepytime.shared.data.remote.dto.response.ChannelTalkHashResponse
import com.sleepytime.shared.enum_.AuthProvider
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.User
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val channelTalkService: ChannelTalkService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ===== 로그인 / 토큰 =====

    /** AuthApi.socialLogin */
    @PostMapping("/social/{provider}")
    fun socialLogin(
        @PathVariable provider: AuthProvider,
        @RequestHeader("X-Social-Token") socialToken: String
    ): ResponseEntity<AuthInfoResponse> =
        ResponseEntity.ok(authService.socialLogin(provider, socialToken.removePrefix("Bearer ")))

    /** AuthApi.sendAuthCode — body: {"email": "..."} */
    @PostMapping("/email/send")
    fun sendAuthCode(@RequestBody request: EmailSendRequest): ResponseEntity<Unit> {
        authService.sendAuthCode(request.email)
        return ResponseEntity.ok().build()
    }

    /** AuthApi.verifyEmailToken — 이메일 토큰으로 로그인 */
    @GetMapping("/email/verify-token")
    fun verifyEmailToken(@RequestParam("token") token: String): ResponseEntity<AuthInfoResponse> =
        ResponseEntity.ok(authService.loginWithEmailToken(token))

    /** AuthApi.refreshToken — Authorization: Bearer {refreshToken} */
    @PostMapping("/refresh")
    fun refreshToken(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ): ResponseEntity<AuthInfoResponse> =
        ResponseEntity.ok(authService.refreshToken(authorization.removePrefix("Bearer ")))

    /** AuthApi.logout */
    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal principal: User?,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) authHeader: String?
    ): ResponseEntity<Unit> {
        // 이미 인증이 풀린 상태여도 앱의 로컬 정리는 진행되어야 하므로 200으로 응답
        principal?.let {
            authService.logout(it.username.toLong(), authHeader?.removePrefix("Bearer "))
        }
        return ResponseEntity.ok().build()
    }

    // ===== 계정 연결 / 해제 =====

    /** AuthApi.connectSocial */
    @PostMapping("/connect/social")
    fun connectSocial(
        @AuthenticationPrincipal principal: User?,
        @RequestBody request: SocialConnectRequest
    ): ResponseEntity<Void> {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        authService.connectSocial(principal.username.toLong(), request.provider, request.accessToken)
        return ResponseEntity.ok().build()
    }

    /** AuthApi.connectEmail */
    @PostMapping("/connect/email")
    fun connectEmail(
        @AuthenticationPrincipal principal: User?,
        @RequestBody request: EmailConnectRequest
    ): ResponseEntity<Void> {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        authService.connectEmail(principal.username.toLong(), request.emailToken)
        return ResponseEntity.ok().build()
    }

    /** AuthApi.disconnectSocial — 바디는 무시하고 경로 변수만 사용 */
    @PostMapping("/social/disconnect/{provider}")
    fun disconnectSocial(
        @AuthenticationPrincipal principal: User?,
        @PathVariable provider: AuthProvider
    ): ResponseEntity<Void> {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        authService.disconnectSocial(principal.username.toLong(), provider)
        return ResponseEntity.ok().build()
    }

    /** AuthApi.disconnectEmail */
    @PostMapping("/email/disconnect")
    fun disconnectEmail(
        @AuthenticationPrincipal principal: User?
    ): ResponseEntity<Void> {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        authService.disconnectEmail(principal.username.toLong())
        return ResponseEntity.ok().build()
    }

    /** AuthApi.changePrimaryProvider */
    @PostMapping("/change/{provider}")
    fun changePrimaryProvider(
        @AuthenticationPrincipal principal: User?,
        @PathVariable provider: AuthProvider
    ): ResponseEntity<Void> {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        authService.changePrimaryProvider(principal.username.toLong(), provider)
        return ResponseEntity.ok().build()
    }

    // ===== 탈퇴 =====

    /** AuthApi.withdraw */
    @DeleteMapping("/withdraw")
    fun withdraw(
        @AuthenticationPrincipal principal: User?,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) authHeader: String?,
        @RequestParam(value = "reason", required = false) reason: String?
    ): ResponseEntity<Unit> {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        authService.withdraw(principal.username.toLong(), authHeader?.removePrefix("Bearer "), reason)
        return ResponseEntity.ok().build()
    }

    // ===== 3rd-party 연동 =====

    /** AuthApi.getChannelTalkHash — 채널톡 회원 인증(고객 정보 암호화) memberHash */
    @GetMapping("/channel-talk/hash")
    fun getChannelTalkHash(@AuthenticationPrincipal principal: User?): ResponseEntity<ChannelTalkHashResponse> {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(channelTalkService.getMemberHash(principal.username.toLong()))
    }

    // ===== 예외 → 상태코드 =====

    /** 마지막 로그인 수단 해제 시도 → 409 (앱은 이 코드로 차단 모달을 띄움) */
    @ExceptionHandler(LastAuthMethodException::class)
    fun handleLastAuthMethod(e: LastAuthMethodException): ResponseEntity<String> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(e.message)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): ResponseEntity<String> =
        ResponseEntity.badRequest().body(e.message)
}