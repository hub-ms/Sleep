package com.soundsleeper.app.config

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
        ).setApplicationName("SoundSleeper").build()
    }
}
