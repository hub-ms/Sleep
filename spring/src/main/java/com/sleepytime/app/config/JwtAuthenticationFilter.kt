package com.sleepytime.app.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val authHeader = request.getHeader("Authorization")

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                val token = authHeader.substring(7)

                // JwtTokenProvider의 validateToken() & extractUserId() 사용 ⭐
                if (jwtTokenProvider.validateToken(token)) {
                    val userId = jwtTokenProvider.extractUserId(token)
                    val username = userId.toString() // subject가 userId.toString()이므로

                    val userDetails = User.withUsername(username)
                        .password("") // JWT 기반 인증이므로 패스워드는 빈 문자열 처리
                        .authorities(emptyList()) // 별도의 권한 목록이 없다면 빈 리스트 주입
                        .build()

                    val authentication = UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.authorities
                    )

                    // SecurityContext 설정 ⭐
                    SecurityContextHolder.getContext().setAuthentication(authentication)

                    logger.debug("JWT 인증 성공 - userId: $userId")
                }
            }
        } catch (e: Exception) {
            logger.error("JWT 인증 실패: ${e.message}")
            SecurityContextHolder.clearContext()
        }

        filterChain.doFilter(request, response)
    }
}