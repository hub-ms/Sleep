package com.soundsleeper.app.config

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64

/**
 * Google Play Developer API 클라이언트.
 *
 * 이 빈이 저장소에 아예 없어서 [com.soundsleeper.app.service_new.GooglePlayBillingService] 의
 * `ObjectProvider<AndroidPublisher>` 가 항상 비어 있었다. 결과적으로 `/api/billing/verify` 와
 * `/api/billing/restore` 는 어떤 환경에서도 NoSuchBeanDefinitionException 으로 실패했다.
 *
 * `@Lazy` 는 의도적이다. 서비스가 ObjectProvider 로 받는 이유가 "키가 없는 로컬 환경에서
 * 서버 기동 자체는 되게 한다" 였으므로, 빈을 즉시 만들면 그 의도가 깨진다. 실제 결제 검증이
 * 호출되는 시점까지 생성을 미루고, 그때 설정이 없으면 읽히는 메시지로 실패한다.
 */
@Configuration
class GooglePlayApiConfig {

    private val log = LoggerFactory.getLogger(GooglePlayApiConfig::class.java)

    @Bean
    @Lazy
    fun androidPublisher(props: GooglePlayProperties): AndroidPublisher {
        val credentials = openKeyStream(props).use { stream ->
            GoogleCredentials.fromStream(stream)
                .createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))
        }
        return AndroidPublisher.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(credentials),
        )
            .setApplicationName(props.packageName)
            .build()
    }

    /**
     * 키를 환경변수에서 먼저 찾고, 없으면 파일 경로에서 찾는다.
     *
     * 운영(컨테이너)에는 키 파일을 둘 자리가 없다 - 키는 gitignore 돼 있어 이미지에 들어가지
     * 않으며, 들어가면 안 된다. 그래서 환경변수가 우선이다. 파일 경로는 로컬 개발용으로만 남긴다.
     */
    private fun openKeyStream(props: GooglePlayProperties): InputStream {
        props.serviceAccountKeyJson?.takeIf { it.isNotBlank() }?.let { raw ->
            log.info("Google Play 서비스 계정 키를 환경변수에서 읽는다")
            return ByteArrayInputStream(decodeKey(raw))
        }
        props.serviceAccountKeyPath?.takeIf { it.isNotBlank() }?.let { path ->
            val file = Paths.get(path)
            check(Files.isReadable(file)) {
                "Google Play 서비스 계정 키 파일을 읽을 수 없다: ${file.toAbsolutePath()}. " +
                    "컨테이너에서는 파일 대신 GOOGLE_PLAY_SERVICE_ACCOUNT_KEY_B64 를 쓴다."
            }
            log.info("Google Play 서비스 계정 키를 파일에서 읽는다: {}", path)
            return Files.newInputStream(file)
        }
        error(
            "Google Play 서비스 계정 키가 설정되지 않아 구독 검증을 할 수 없다. " +
                "GOOGLE_PLAY_SERVICE_ACCOUNT_KEY_B64(운영) 또는 " +
                "GOOGLE_PLAY_SERVICE_ACCOUNT_KEY_PATH(로컬) 중 하나를 설정한다."
        )
    }

    /**
     * 원본 JSON 과 base64 를 모두 받는다.
     *
     * 플랫폼 환경변수 입력란은 줄바꿈을 그대로 보존하지 않는 경우가 있어 base64 쪽이 안전하다.
     * 다만 사람이 JSON 을 그대로 붙여 넣는 일도 흔하므로 양쪽을 받아 준다. 판별은 첫 글자로
     * 한다 - base64 알파벳에 `{` 가 없어서 모호하지 않다. 줄바꿈이 섞인 base64 도 받도록
     * MIME 디코더를 쓴다.
     */
    private fun decodeKey(raw: String): ByteArray {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("{")) {
            trimmed.toByteArray()
        } else {
            runCatching { Base64.getMimeDecoder().decode(trimmed) }.getOrElse {
                error(
                    "Google Play 서비스 계정 키를 해석할 수 없다. JSON 원본이거나 " +
                        "base64 로 인코딩된 JSON 이어야 한다."
                )
            }
        }
    }
}
