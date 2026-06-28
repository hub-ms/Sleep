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


    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation(libs.kotlinx.datetime)

    runtimeOnly("org.postgresql:postgresql:42.7.10")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.register<JavaExec>("runSpringApp") {
    group = "application"
    description = "Runs the main Kotlin/Spring application"

    val main = sourceSets.main.get()
    classpath = main.runtimeClasspath
    mainClass.set("com.sleepytime.app.SleepApplicationKt")
    standardInput = System.`in`
}
