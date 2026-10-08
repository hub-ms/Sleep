package com.soundsleeper.app.controller_new

import com.soundsleeper.app.dto_new.response.WithdrawnAccountResponse
import com.soundsleeper.app.exception.LastAuthMethodException
import com.soundsleeper.app.exception.WithdrawnAccountException
import com.soundsleeper.app.service_new.AuthService
import com.soundsleeper.app.service_new.ChannelTalkService
import com.soundsleeper.app.data.remote.dto.request.EmailConnectRequest
import com.soundsleeper.app.data.remote.dto.request.EmailVerifyRequest
import com.soundsleeper.app.data.remote.dto.request.EmailSendRequest
import com.soundsleeper.app.data.remote.dto.request.SocialConnectRequest
import com.soundsleeper.app.data.remote.dto.response.AuthInfoResponse
import com.soundsleeper.app.data.remote.dto.response.ChannelTalkHashResponse
import com.soundsleeper.app.enum_.AuthProvider
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
    /**
     * @param restore 탈퇴 유예 기간이 남은 계정을 되살리며 로그인할지. 앱이 사용자에게
     * 복구 여부를 물어 동의를 받은 뒤에만 true 로 보낸다.
     */
    @PostMapping("/social/{provider}")
    fun socialLogin(
        @PathVariable provider: AuthProvider,
        @RequestHeader("X-Social-Token") socialToken: String,
        @RequestParam(value = "restore", required = false, defaultValue = "false") restore: Boolean,
    ): ResponseEntity<AuthInfoResponse> =
        ResponseEntity.ok(
            authService.socialLogin(provider, socialToken.removePrefix("Bearer "), restore)
        )

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

    /**
     * AuthApi.verifyAuthCode — 메일로 받은 6자리 코드로 로그인/가입.
     *
     * 서비스에는 구현돼 있었지만 매핑이 없어 앱에서는 호출할 길이 없었다.
     */
    @PostMapping("/email/verify")
    fun verifyAuthCode(@RequestBody request: EmailVerifyRequest): ResponseEntity<AuthInfoResponse> =
        ResponseEntity.ok(authService.verifyAuthCode(request))

    /** AuthApi.connectEmail — 로그인된 계정에 이메일을 붙인다. */
    @PostMapping("/connect/email")
    fun connectEmail(
        @AuthenticationPrincipal principal: User?,
        @RequestBody request: EmailConnectRequest
    ): ResponseEntity<Void> {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        authService.connectEmail(
            principal.username.toLong(),
            EmailVerifyRequest(email = request.email, code = request.code)
        )
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

    /**
     * 탈퇴한 계정으로 로그인 시도 → 410 Gone.
     *
     * 409 는 이미 '마지막 로그인 수단 해제'가 쓰고 있어 앱이 두 경우를 구분할 수 없다.
     * 복구 가능 여부는 [WithdrawnAccountResponse.isRestorable]로 구조화해서 전달한다 —
     * 앱이 이 값으로 "복구하시겠어요?"와 "새로 가입해 주세요" 화면을 구분한다.
     */
    @ExceptionHandler(WithdrawnAccountException::class)
    fun handleWithdrawnAccount(e: WithdrawnAccountException): ResponseEntity<WithdrawnAccountResponse> =
        ResponseEntity.status(HttpStatus.GONE).body(
            WithdrawnAccountResponse(
                message = e.message ?: "",
                isRestorable = e.isRestorable,
                restorableUntil = e.restorableUntil,
            )
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): ResponseEntity<String> =
        ResponseEntity.badRequest().body(e.message)

    /** 서비스가 check/require 로 막은 상태 오류 → 409 (본문에 사유) */
    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(e: IllegalStateException): ResponseEntity<String> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(e.message)
}