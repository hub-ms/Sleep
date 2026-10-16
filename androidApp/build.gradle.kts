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

// 서버 주소는 git 에 올라가지 않는 local.properties(또는 CI 의 환경변수)에서 읽는다.
// debug/release 를 나눠 두지 않으면 개발 중에 뽑은 release APK 가 개발 서버(ngrok 터널 등)를
// 가리킨 채로 스토어에 올라간다. 해석 순서: 환경변수 → variant 별 키 → 공용 키.
fun resolveBaseUrl(variant: String): String {
    val raw = System.getenv("SERVER_BASE_URL_${variant.uppercase()}")
        ?: System.getenv("SERVER_BASE_URL")
        ?: localProperties.getProperty("server.base.url.$variant")
        ?: localProperties.getProperty("server.base.url")
        ?: ""
    val trimmed = raw.trim()
    // Ktor 가 defaultRequest { url(baseUrl) } 과 baseUrl + "auth/refresh" 형태로 이어 붙이기 때문에
    // 끝 슬래시가 빠지면 마지막 경로 한 단계가 잘려서 토큰 재발급부터 깨진다.
    return if (trimmed.isEmpty() || trimmed.endsWith("/")) trimmed else "$trimmed/"
}

// 에뮬레이터에서 호스트 PC 의 8080 을 가리키는 주소가 개발 기본값이다.
val debugBaseUrl = resolveBaseUrl("debug").ifEmpty { "http://10.0.2.2:8080/" }
// 운영 주소는 기본값을 두지 않는다. 비어 있으면 아래 preReleaseBuild 가드가 빌드를 멈춘다.
val releaseBaseUrl = resolveBaseUrl("release")
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
        versionCode = 3
        versionName = "1.0.0"
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
        // BASE_URL 만 variant 별로 주입한다(나머지 키는 debug/release 공용).
        getByName("debug") {
            buildConfigField("String", "BASE_URL", "\"$debugBaseUrl\"")
        }
        getByName("release") {
            buildConfigField("String", "BASE_URL", "\"$releaseBaseUrl\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

// 운영 주소가 비어 있거나 평문이면 조용히 잘못된 APK 가 나오는 대신 빌드를 멈춘다.
// 구성 단계에서 던지면 assembleDebug 까지 막히므로 release 태스크 체인 앞에만 붙인다.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    // 지역 변수로 복사해서 넘긴다. doFirst 람다가 스크립트 최상위 val 을 직접 참조하면
    // 그 람다가 빌드 스크립트 객체를 붙들어 configuration cache 직렬화가 실패한다.
    val baseUrl = releaseBaseUrl
    doFirst {
        require(baseUrl.isNotBlank()) {
            "release 빌드에는 운영 서버 주소가 필요합니다. local.properties 에 " +
                "server.base.url.release=https://<운영 도메인>/ 을 추가하거나 " +
                "SERVER_BASE_URL_RELEASE 환경변수를 설정하세요."
        }
        require(baseUrl.startsWith("https://")) {
            "운영 서버 주소는 https 여야 합니다(현재: $baseUrl)."
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
