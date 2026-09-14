package com.sleepytime.app.service_new

import com.sleepytime.app.config.EmailAuthProperties
import com.sleepytime.shared.data.remote.dto.request.EmailVerifyRequest
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration

@Component
class EmailAuthManager(
    private val redisTemplate: StringRedisTemplate,
    private val mailSender: JavaMailSender,
    private val authProperties: EmailAuthProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendAuthCode(email: String) {
        log.debug("authcode requested: $email")
        val cleanEmail = email.replace("\"", "").trim()
        val code = generateSecureCode()
        val hashedCode = hashSha256(code)
        val key = "${authProperties.codePrefix}$cleanEmail"

        redisTemplate.opsForValue().set(
            key,
            hashedCode,
            Duration.ofSeconds(authProperties.codeExpirationTime)
        )

        try {
            sendEmail(cleanEmail, code)
        } catch (e: Exception) {
            redisTemplate.delete(key)
            throw e
        }
    }
    fun verifyAuthCode(request: EmailVerifyRequest): Boolean {
        val cleanEmail = request.email
            .replace("\"", "")
            .replace("=3D", "")
            .replace("=", "")
            .trim()
        val cleanCode = request.code
            .replace("=3D", "")
            .replace("=", "")
            .trim()

        val key = "${authProperties.codePrefix}$cleanEmail"
        val storedHash = redisTemplate.opsForValue().get(key) ?: return false

        return if (storedHash == hashSha256(cleanCode)) {
            redisTemplate.delete(key)
            true
        } else false
    }

    private fun generateSecureCode(): String {
        val secureRandom = SecureRandom()
        return (100000 + secureRandom.nextInt(900000)).toString()
    }
    private fun hashSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun sendEmail(toEmail: String, code: String) {
        val encodedEmail = URLEncoder.encode(toEmail, StandardCharsets.UTF_8.toString())
        log.debug("Encoded email: $encodedEmail")
        val encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8.toString())

        val verifyUrl = "https://hub-ms.github.io/verify?email=$encodedEmail&code=$encodedCode"

        val mimeMessage = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(mimeMessage, true, "UTF-8")

        val htmlContent = """
        <div style='text-align:center;padding:40px;background:linear-gradient(135deg,#667eea,#764ba2);color:white;border-radius:20px;max-width:500px;margin:0 auto;box-shadow:0 20px 40px rgba(0,0,0,0.2);'>
            <h1 style='font-size:36px;margin-bottom:20px;'>😴 수면 앱 인증</h1>
            <p style='font-size:18px;margin-bottom:30px;'>아래 버튼을 클릭하면<br><strong>인증이 자동 완료</strong>됩니다!</p>
            
            <a href="$verifyUrl" style='
                display:inline-block;
                background:linear-gradient(135deg,#10B981,#059669 100%);
                color:white;
                padding:20px 60px;
                font-size:20px;
                font-weight:700;
                border-radius:50px;
                text-decoration:none;
                box-shadow:0 15px 35px rgba(16,185,129,0.4);
            '>
                수면 앱으로 인증 완료
            </a>
            
            <p style='margin-top:20px; font-size:16px;'>인증 코드: <strong style='font-size:22px; color:#FBBF24;'>$code</strong></p>
            <p style='margin-top:15px;font-size:14px;color:rgba(255,255,255,0.9);'>
                유효시간: ${authProperties.codeExpirationTime}초
            </p>
        </div>
        """.trimIndent()

        helper.setTo(toEmail)
        helper.setFrom(authProperties.fromEmail)
        helper.setSubject("수면 앱 - 이메일 인증 (${authProperties.codeExpirationTime}초)")
        helper.setText(htmlContent, true)
        mailSender.send(mimeMessage)
    }
}
