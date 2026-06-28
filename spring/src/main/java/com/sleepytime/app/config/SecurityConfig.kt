package com.sleepytime.app.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

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
            .authorizeHttpRequests { auth ->
                // 🔓 Public endpoints (문자열만 쉼표로 나열)
                auth.requestMatchers(
                    "/auth/social/**",
                    "/auth/email/**",
                    "/auth/verify-email-token"
                ).permitAll()

                // 🔒 Protected endpoints
                auth.requestMatchers(
                    "/auth/connect/**",
                    "/auth/logout",
                    "/auth/withdraw",
                    "/auth/refresh",
                    "/auth/primary-provider",
                    "/auth/provider",
                    "/auth/email"
                ).authenticated()

                // Explicit catchall for any other requests
                auth.anyRequest().authenticated()
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