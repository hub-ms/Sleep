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
        val authHeader = request.getHeader("Authorization")

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)

            if (jwtTokenProvider.validateToken(token)) {
                try {
                    val userId = jwtTokenProvider.extractUserId(token)
                    val username = userId.toString()

                    val userDetails = User.withUsername(username)
                        .password("")
                        .authorities(emptyList())
                        .build()

                    val authentication = UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.authorities
                    )
                    SecurityContextHolder.getContext().setAuthentication(authentication)
                    logger.debug("JWT 인증 성공 - userId: $userId")
                } catch (e: Exception) {
                    logger.error("JWT 인증 처리 중 예외: ${e.message}")
                    SecurityContextHolder.clearContext()
                }
            } else {
                SecurityContextHolder.clearContext()
                logger.debug("JWT 만료 또는 유효하지 않음 - anonymous로 진행")
            }
        }
        filterChain.doFilter(request, response)
    }
}