import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.googleGmsServices)
    alias(libs.plugins.firebaseCrashlytics)
    alias(libs.plugins.aboutLibraries)
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
// 오픈소스 라이선스 목록은 이 모듈에서 생성한다.
//
// 예전에는 :shared 에 플러그인을 걸었는데, 그 모듈은 androidTarget() 없이
// com.android.kotlin.multiplatform.library 를 쓴다. AboutLibraries 수집기는 Android variant 의
// 런타임 클래스패스를 훑으므로 그 variant 모델이 없으면 의존성을 하나도 해석하지 못하고,
// res/raw/aboutlibraries.json 이 {"libraries":[],"licenses":{}} 30바이트로 생성됐다
// (빌드는 성공하고 화면만 비어 있어서 원인을 찾기 어려웠다).
// 평범한 com.android.application 모듈인 여기서는 debug/release variant 가 정상이라
// 실제 의존성 목록이 담긴다.
aboutLibraries {
    collect {
        includePlatform.set(true)
    }
}
