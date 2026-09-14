package com.sleepytime.app.config

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import javax.crypto.SecretKey
import java.util.Date

@Service
class JwtTokenProvider(
    private val jwtProperties: JwtProperties,
) {
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secretKey.encodeToByteArray())
    private val logger = LoggerFactory.getLogger(JwtTokenProvider::class.java)

    fun getAccessToken(userId: Long): String {
        val now = System.currentTimeMillis()
        val expire = Date(now+jwtProperties.accessTokenExpiry)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("userId",userId)
            .claim("tokenType","access")
            .claim("app","sleep-app-v1")
            .issuedAt(Date(now))
            .expiration(expire)
            .signWith(secretKey)
            .compact()
    }
    fun getRefreshToken(userId: Long): String {
        val now = System.currentTimeMillis()
        val expire = Date(now+jwtProperties.refreshTokenExpiry)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("tokenType","refresh")
            .claim("app","sleep-app-v1")
            .issuedAt(Date(now))
            .expiration(expire)
            .signWith(secretKey)
            .compact()

    }
    fun validateToken(token: String): Boolean {
        return try {
            parseClaims(token)
            true
        } catch (e: Exception) {
            logger.error("JWT 검증 실패: ${e.message}")
            false
        }
    }
    fun extractUserId(token: String): Long {
        val claims = parseClaims(token)
        return (claims["userId"] as Number).toLong()
    }
    fun getRemainingTime(token: String): Long {
        return try {
            val exp = parseClaims(token).expiration.time
            (exp - System.currentTimeMillis()).coerceAtLeast(0)
        } catch (_: Exception) {
            0L
        }
    }
    private fun parseClaims(token: String) =
        Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
}
