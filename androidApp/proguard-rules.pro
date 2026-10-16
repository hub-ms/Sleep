# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ─────────────────────────────────────────────────────────────────────
# 크래시 리포트 가독성 — 이게 없으면 Play Console/Crashlytics 스택트레이스에
# 소스 파일명도 줄 번호도 안 남아서 조사가 불가능하다. release 가 minify 되는
# 이상 이 두 줄은 선택이 아니다. mapping.txt 와 함께 봐야 복원된다.
# ─────────────────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 제네릭 타입 정보. Ktor 의 typeInfo<T>() 와 kotlinx-serialization 의 제네릭
# serializer 해석이 Signature 를 읽는다. 지우면 List<T>/Map<K,V> DTO 의 응답
# 역직렬화가 런타임에만 깨진다(빌드는 통과한다).
-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions
-keepattributes *Annotation*,RuntimeVisibleAnnotations,AnnotationDefault

# ─────────────────────────────────────────────────────────────────────
# 서드파티
# ─────────────────────────────────────────────────────────────────────
-keep class com.kakao.sdk.**.model.* { <fields>; }

# https://github.com/square/okhttp/pull/6792
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.*
-dontwarn org.openjsse.**

# Voyager: ScreenModelStore 가 KClass 를 키로 쓰고, Screen 은 저장된 상태에서
# 복원될 수 있다. 몇 KB 비용으로 "디버그에선 되는데 릴리즈에선 빈 화면" 부류를 막는다.
-keep class * implements cafe.adriel.voyager.core.screen.Screen { *; }
-keep class * extends cafe.adriel.voyager.core.model.ScreenModel { *; }

# TFLite: JNI + 델리게이트 리플렉션 로딩. AAR 자체 규칙의 커버리지가 일정하지 않다.
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.** { native <methods>; }
-dontwarn org.tensorflow.lite.**

# 채널톡: 난독화에 취약한 벤더 SDK 이고, 실패가 크래시가 아니라 "빈 메신저"로
# 나타나서 QA 에서 놓치기 쉽다.
-keep class io.channel.** { *; }
-keep class com.zoyi.** { *; }
-dontwarn io.channel.**
-dontwarn com.zoyi.**

-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# ─────────────────────────────────────────────────────────────────────
# 의도적으로 넣지 않은 것
#
# kotlinx-serialization / Koin / Firebase / Play Billing / Play Review /
# Coil / Compose 는 각자 artifact 안에 consumer ProGuard 규칙을 넣어 배포한다.
# 선제적으로 -keep class kotlinx.serialization.** { *; } 류를 넣으면 APK 만
# 커지고, 정작 필요한 한 줄이 무엇인지 가려진다.
#
# release 스모크에서 아래가 보이면 그때 좁은 규칙을 추가한다:
#   SerializationException: Serializer for class 'X' is not found
#     -> @Serializable 의 $serializer/Companion 이 제거된 것
#   NoDefinitionFound / NoBeanDefFoundException (Koin)
#     -> single<Interface> 의 인터페이스가 병합된 것
#        -> -keep interface com.soundsleeper.app.domain.repository.** { *; }
#
# 참고: 기존에 있던 Retrofit 규칙 6줄은 제거했다. 이 프로젝트는 Ktor 를 쓰고
# Retrofit 은 클래스패스에 아예 없어서 무용지물이었다.
# ─────────────────────────────────────────────────────────────────────
