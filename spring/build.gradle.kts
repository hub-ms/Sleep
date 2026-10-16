import org.gradle.language.jvm.tasks.ProcessResources
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSpring)
    alias(libs.plugins.kotlinJpa)
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyManagement)
}

dependencies {
    implementation(project(":shared")) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
        exclude(group = "io.insert-koin", module = "koin-android")
        exclude(group = "androidx.media3", module = "media3-exoplayer")
        exclude(group = "androidx.media3", module = "media3-ui")
        exclude(group = "androidx.media3", module = "media3-session")
        exclude(group = "androidx.media3", module = "media3-common")
        exclude(group = "io.coil-kt.coil3", module = "coil-network-ktor")
        exclude(group = "io.coil-kt.coil3", module = "coil-compose")
        exclude(group= "benasher44", module = "uuid")
    }


    implementation(libs.jackson.module.kotlin)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.kotlin.reflect)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.kotlinx.datetime)

    // Google Play 서버사이드 구독 검증 (Android Publisher API) + RTDN(Pub/Sub) OIDC 토큰 검증
    implementation(libs.google.api.services.androidpublisher)
    implementation(libs.google.auth.library.oauth2.http)
    implementation(libs.google.api.client)

    runtimeOnly(libs.postgresql)
}

// 약관·개인정보처리방침 JSON 은 앱 번들본이 유일한 원본이다.
//
// 서버가 자기 복사본을 따로 들고 있으면 개정 때 한쪽만 고쳐져 조용히 벌어진다. 실제로
// privacy.json 의 문의 이메일이 번들본만 오타("privacy@dsoundsleeper.app")인 채 벌어져
// 있었는데, version 이 양쪽 다 같아서 LegalRepositoryImpl 의 버전 비교로도 걸러지지
// 않았다. 빌드 때 원본에서 그대로 가져와 드리프트 자체를 불가능하게 만든다.
//
// LegalService 는 생성자에서 두 문서를 즉시 읽으므로, 이 복사가 빠지면 404 가 아니라
// 스프링 컨텍스트 기동 실패로 드러난다.
tasks.named<ProcessResources>("processResources") {
    from(project(":shared").file("src/commonMain/composeResources/files")) {
        include("terms.json", "privacy.json")
        into("legal")
    }
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

tasks.register<JavaExec>("runSpringApp") {
    group = "application"
    description = "Runs the main Kotlin/Spring application"

    val main = sourceSets.main.get()
    classpath = main.runtimeClasspath
    mainClass.set("com.soundsleeper.app.SleepApplicationKt")
    standardInput = System.`in`
}

tasks.withType<BootJar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
