import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.googleGmsServices)
    alias(libs.plugins.firebaseCrashlytics)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val serverBaseUrl = localProperties.getProperty("server.base.url") ?: "http://localhost/"
val googleClientId = localProperties.getProperty("google.oauth.client.id") ?: ""
val kakaoKey = localProperties.getProperty("kakao.native.app.key") ?: ""
val channelTalkPluginKey = localProperties.getProperty("channeltalk.plugin.key") ?: ""
val revenueCatApiKey = localProperties.getProperty("revenuecat.api.key") ?: ""

extensions.configure<ApplicationExtension> {
    // AGP는 모듈/라이브러리마다 고유한 namespace를 요구한다. 앱의 코드와 리소스는 전부
    // :shared(namespace = com.soundsleeper.app)에 있고 이 모듈은 얇은 래퍼라서,
    // 충돌을 피하는 쪽을 여기로 잡았다. 설치되는 패키지명은 아래 applicationId 그대로다.
    namespace = "com.soundsleeper.app.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.soundsleeper.app"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "BASE_URL", "\"$serverBaseUrl\"")
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", "\"$googleClientId\"")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoKey\"")
        buildConfigField("String", "CHANNELTALK_PLUGIN_KEY", "\"$channelTalkPluginKey\"")
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatApiKey\"")

        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoKey
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.koin.android)
    implementation(libs.kakao.v2.user)
    implementation(libs.channel.plugin.android)
    implementation(libs.firebase.crashlytics)
    implementation(libs.revenuecat.purchases)
}