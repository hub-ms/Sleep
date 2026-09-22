package com.sleepytime.app.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.http.HttpStatus

@Configuration
class SecurityConfig(
    private val jwtTokenProvider: JwtTokenProvider
) {

    @Bean
    fun passwordEncoder(): BCryptPasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .anonymous { }
            // 인증 없음/만료된 JWT는 401을 반환해야 클라이언트(Ktor Auth 플러그인)의
            // refreshTokens{} 자동 재발급 로직이 트리거된다. 이 설정이 없으면 Spring Security
            // 기본값인 403이 반환되어(Ktor는 401에서만 재시도) 토큰이 만료될 때마다 재로그인 없이는
            // 아무 보호된 API도 호출할 수 없게 된다.
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .authorizeHttpRequests {
                it.requestMatchers("/auth/social/disconnect/**", "/auth/email/disconnect",
                    "/auth/connect/**", "/auth/change/**", "/auth/withdraw",
                    "/user/**").authenticated()
                it.requestMatchers(HttpMethod.POST, "/auth/social/*").permitAll()
                it.requestMatchers(HttpMethod.POST, "/auth/email/send", "/auth/refresh").permitAll()
                it.requestMatchers(HttpMethod.GET, "/auth/email/verify-token").permitAll()
                it.anyRequest().authenticated()
            }
            // ✅ FIX #3: Filter ordering is correct
            .addFilterBefore(
                JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter::class.java
            )

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            // ✅ ngrok 주소 및 와일드카드를 포함하여 허용 패턴 수정
            allowedOriginPatterns = listOf(
                "http://localhost:*",
                "http://localhost:3000",
                "https://*.ngrok-free.dev" // 👈 ngrok 도메인을 통한 접근을 허용합니다.
            )

            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            allowedHeaders = listOf("*")
            allowCredentials = false
            exposedHeaders = listOf("Authorization", "Content-Type")
            maxAge = 3600
        }

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}