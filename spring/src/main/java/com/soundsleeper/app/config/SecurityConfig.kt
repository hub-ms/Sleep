package com.soundsleeper.app.config

import org.springframework.beans.factory.annotation.Value
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
    private val jwtTokenProvider: JwtTokenProvider,
    // 브라우저에서 접근하는 출처만 적는다(안드로이드 네이티브 클라이언트는 CORS 대상이 아니다).
    // 운영에서는 APP_CORS_ALLOWED_ORIGINS 환경변수로 지정하고, 없으면 로컬 개발용 기본값만 쓴다.
    @Value($$"${app.cors.allowed-origins:http://localhost:*}")
    private val corsAllowedOrigins: List<String>,
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
                it.requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                it.requestMatchers(HttpMethod.GET, "/legal/**").permitAll()
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
            // 이름을 corsAllowedOrigins 로 둔 이유: apply 블록 안에서 allowedOrigins 라고 쓰면
            // 수신 객체(CorsConfiguration)의 동명 프로퍼티(= null)가 잡혀서 아무 출처도 허용되지
            // 않는다(모든 CORS 요청이 403 "Invalid CORS request"). 컴파일은 통과하므로 런타임에만 드러난다.
            allowedOriginPatterns = corsAllowedOrigins

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