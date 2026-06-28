import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)

    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)

    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.sqlDelight)

    alias(libs.plugins.googleGmsServices)
    id("kotlin-parcelize")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val kmaKey = localProperties.getProperty("kma.service.key") ?: ""
val serverBaseUrl = localProperties.getProperty("server.base.url") ?: "http://localhost/"
val googleClientId = localProperties.getProperty("google.oauth.client.id") ?: ""
val kakaoKey = localProperties.getProperty("kakao.native.app.key") ?: ""

compose.resources {
    publicResClass = true
    packageOfResClass = "com.sleepytime.shared.resources"
    generateResClass = auto
}

kotlin {
    androidTarget()
    jvm()
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            val composeVersion = libs.versions.compose.multiplatform.get()

            implementation("org.jetbrains.compose.components:components-resources:$composeVersion")
            implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
            implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
            implementation("org.jetbrains.compose.material3:material3:$composeVersion")
            implementation("org.jetbrains.compose.ui:ui:$composeVersion")
            implementation("org.jetbrains.compose.components:components-ui-tooling-preview:$composeVersion")

            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.viewmodel.savedstate)

            implementation(libs.benasher44.uuid)

            // 네트워크
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)

            // 코루틴 & 직렬화
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            // KMP 공통 의존성
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
            api(libs.napier)

            implementation(libs.voyager.screenmodel)
            implementation(libs.voyager.koin)
            implementation(libs.voyager.navigator)
            implementation(libs.sqldelight.coroutines.extensions)

            implementation(libs.coil.compose)
            implementation(libs.coil.network)
            implementation(libs.annotations)

            implementation(libs.firebase.bom)
        }
        androidMain.dependencies {
            implementation(libs.google.gson)
            implementation(libs.sqldelight.android.driver)

            implementation(libs.koin.test)

            implementation(libs.koin.android)
            implementation(libs.kotlinx.coroutines.android)

            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.media3.session)
            implementation(libs.androidx.media3.common)

            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.play.services)

            implementation(libs.androidx.activity.compose)

            implementation(libs.google.play.services.location)
            implementation(libs.tensorflow.lite)

            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.googleid)
            implementation(libs.kakao.v2.user)
            implementation(libs.firebase.auth)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

sqldelight {
    databases {
        create("SleepDatabase") {
            packageName.set("com.sleepytime.shared.data.local.generated")
        }
    }
}

extensions.configure<ApplicationExtension> {
    namespace = "com.sleepytime.shared"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.sleepytime.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "KMA_SERVICE_KEY", "\"$kmaKey\"")
        buildConfigField("String", "BASE_URL", "\"$serverBaseUrl\"")
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", "\"$googleClientId\"")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoKey\"")

        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoKey
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    sourceSets {
        getByName("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            res.directories.add("src/androidMain/res")
            java.directories.add("src/androidMain/kotlin")
            assets.directories.add("src/androidMain/assets")
        }
    }
}

