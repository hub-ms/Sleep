package com.soundsleeper.app.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "app.google-play")
@Validated
data class GooglePlayProperties(
    @field:NotBlank val packageName: String,
    // 파일 경로. 로컬 개발 전용이다 - 컨테이너 이미지에는 키 파일이 들어가지 않는다
    // (.gitignore 로 제외돼 있고, 제외돼 있는 것이 맞다).
    val serviceAccountKeyPath: String? = null,
    // 운영에서 쓰는 쪽. 키 JSON 을 그대로, 또는 base64 한 줄로 받는다. Railway 같은
    // 플랫폼에는 파일을 둘 자리가 없고 환경변수만 있다.
    val serviceAccountKeyJson: String? = null,
    val rtdnAudience: String? = null,
    val rtdnAllowedServiceAccountEmail: String? = null,
)
