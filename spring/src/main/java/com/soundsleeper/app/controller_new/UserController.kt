package com.soundsleeper.app.controller_new

import com.soundsleeper.app.service_new.UserService
import com.soundsleeper.app.data.remote.dto.response.UserResponse
import com.soundsleeper.app.enum_.AuthProvider
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.User
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/user")
class UserController(
    private val userService: UserService,
) {
    /**
     * 닉네임 길이·중복·변경 쿨다운처럼 사용자가 고칠 수 있는 거절은 그 이유가 화면까지
     * 가야 한다. 핸들러가 없으면 Spring 이 /error 로 포워드하면서 메시지가 사라지고,
     * 앱에는 본문 없는 5xx 만 남는다.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): ResponseEntity<String> =
        ResponseEntity.badRequest().body(e.message)

    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(e: IllegalStateException): ResponseEntity<String> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(e.message)

    /** AuthApi.getUserInfo */
    @GetMapping("/info")
    fun getUserInfo(@AuthenticationPrincipal principal: User?): ResponseEntity<UserResponse> {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(userService.getUserInfo(principal.username.toLong()))
    }

    /**
     * AuthApi.updateProfile — 기존 updateProfile + refreshSocialProfile 통합
     *  - socialProvider(+X-Social-Token) 만 보내면: 소셜에서 프로필 이미지를 새로 받아 갱신
     *  - nickname / email / profileImage 를 보내면: 그 값으로 갱신
     *  - 이미지는 직접 업로드가 소셜 이미지보다 우선
     *  갱신된 유저를 돌려주므로 앱은 별도 재조회 없이 상태를 교체하면 됨
     */
    @PostMapping("/profile", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateProfile(
        @AuthenticationPrincipal principal: User?,
        @RequestParam(value = "nickname", required = false) nickname: String?,
        @RequestParam(value = "email", required = false) email: String?,
        @RequestParam(value = "resetImage", required = false, defaultValue = "false") resetImage: Boolean,
        @RequestParam(value = "socialProvider", required = false) socialProvider: AuthProvider?,
        @RequestHeader(value = "X-Social-Token", required = false) socialToken: String?,
        @RequestPart(value = "profileImage", required = false) profileImage: MultipartFile?
    ): ResponseEntity<UserResponse> {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(
            userService.updateProfile(
                userId = principal.username.toLong(),
                email = email,
                nickname = nickname,
                profileImage = profileImage,
                resetImage = resetImage,
                socialProvider = socialProvider,
                socialToken = socialToken?.removePrefix("Bearer "),
            )
        )
    }
}