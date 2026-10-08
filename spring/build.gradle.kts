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
