import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqlDelight)
    alias(libs.plugins.aboutLibraries)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.soundsleeper.app.resources"
    generateResClass = auto
}

kotlin {
    // androidTarget()을 호출하지 않는다. com.android.kotlin.multiplatform.library 플러그인의
    // 아래 android { } 블록이 android 타깃을 직접 만든다(둘을 같이 쓰면 중복 등록이 된다).
    jvm()
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    android {
        namespace = "com.soundsleeper.app"
        compileSdk = 37
        minSdk = 31

        // 기본값이 false다. 켜지 않으면 src/androidMain/res가 AAR에 들어가지 않아
        // :androidApp 매니페스트의 @style/Theme.SoundSleeper 등이 해석되지 않는다.
        androidResources {
            enable = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.components.resources)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.ui.tooling.preview)
            implementation(libs.ktor.client.auth)

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

            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.aboutlibraries.compose)

            // Lottie 애니메이션 (온보딩/페이월 일러스트)
            implementation(libs.compottie)
            implementation(libs.compottie.resources)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)

            implementation(libs.koin.android)
            implementation(libs.kotlinx.coroutines.android)

            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.media3.session)
            implementation(libs.androidx.media3.common)

            implementation(libs.ktor.client.okhttp)

            implementation(libs.androidx.activity.compose)
            // 앱 내 언어 전환(AppCompatDelegate.setApplicationLocales)에 필요하다.
            implementation(libs.androidx.appcompat)

            implementation(libs.tensorflow.lite)

            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.googleid)
            implementation(libs.kakao.v2.user)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.messaging)
            implementation(libs.firebase.crashlytics)
            implementation(libs.channel.plugin.android)
            implementation(libs.billing.ktx)
            implementation(libs.revenuecat.purchases)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("SleepDatabase") {
            packageName.set("com.soundsleeper.app.data.local.generated")
        }
    }
}
