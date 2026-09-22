package com.sleepytime.app.config

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import java.io.FileInputStream

// AndroidPublisher 빈은 @Lazy로 등록한다. serviceAccountKeyPath가 아직 준비되지 않은
// 로컬 개발 환경에서도, 실제로 결제 검증 기능을 호출하기 전까지는 서버가 정상 기동되어야 하기 때문이다.
@Configuration
@EnableConfigurationProperties(GooglePlayProperties::class)
class GooglePlayBillingConfig(
    private val props: GooglePlayProperties
) {
    @Bean
    @Lazy
    fun androidPublisher(): AndroidPublisher {
        val keyPath = props.serviceAccountKeyPath?.takeIf { it.isNotBlank() }
            ?: error("app.google-play.service-account-key-path 가 설정되어 있지 않습니다.")
        val credentials = FileInputStream(keyPath).use { stream ->
            GoogleCredentials.fromStream(stream).createScoped(AndroidPublisherScopes.ANDROIDPUBLISHER)
        }
        return AndroidPublisher.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(credentials)
        ).setApplicationName("SleepyTime").build()
    }
}
