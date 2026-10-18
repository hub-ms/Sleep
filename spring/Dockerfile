# syntax=docker/dockerfile:1

# ── builder ───────────────────────────────────────────────────────────────────
#
# :spring 은 project(":shared") 에 의존하고, :shared 는 android { } 타깃을 가진 KMP
# 모듈이다. Gradle 은 :spring 만 빌드할 때도 :shared 를 **구성**하므로, Android SDK 가
# 없으면 bootJar 가 아니라 구성 단계에서 먼저 실패한다. 그래서 JDK 만 있는 이미지로는
# 빌드할 수 없고 여기에 commandline-tools 를 깔아 둔다.
#
# local.properties 는 git 에 없다. sdk.dir 대신 ANDROID_HOME 환경변수를 읽게 한다.
FROM eclipse-temurin:21-jdk AS builder

ENV ANDROID_HOME=/opt/android-sdk \
    GRADLE_USER_HOME=/home/gradle/.gradle \
    DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl unzip \
 && rm -rf /var/lib/apt/lists/*

# commandline-tools 는 cmdline-tools/latest 경로에 있어야 sdkmanager 가 동작한다.
RUN mkdir -p "$ANDROID_HOME/cmdline-tools" \
 && curl -fsSL -o /tmp/tools.zip \
      https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
 && unzip -q /tmp/tools.zip -d "$ANDROID_HOME/cmdline-tools" \
 && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
 && rm /tmp/tools.zip

# compileSdk/targetSdk 는 37 이다(androidApp/build.gradle.kts). 버전을 올릴 때 같이 고친다.
#
# "platforms;android-37" 라는 패키지는 존재하지 않는다 - 처음 빌드해 보고서야 드러난 점이다
# ("Failed to find package 'platforms;android-37'"로 죽었다). 37 세대부터 플랫폼이
# 37.0/37.1/37.2 포인트 릴리즈로 나뉘어 있어 밑줄 없는 "android-37" 자체가 저장소에 없다.
# `sdkmanager --list` 로 확인한 최신 안정 포인트 릴리즈를 명시한다 - compileSdk=37 이
# 정수라 AGP 가 설치된 37.x 중 가장 높은 것을 그대로 쓴다.
RUN yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null \
 && "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
      "platform-tools" "platforms;android-37.2" "build-tools;35.0.0" > /dev/null

WORKDIR /build

# 의존성 해석 결과를 레이어 캐시에 남기려고 빌드 스크립트를 먼저 복사한다. 소스만 바뀐
# 재배포에서는 이 레이어가 그대로 재사용돼 빌드가 훨씬 짧아진다.
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY shared/build.gradle.kts shared/
COPY spring/build.gradle.kts spring/
COPY androidApp/build.gradle.kts androidApp/
# gradlew 가 CRLF 로 체크인된 적이 있었다 - 셔뱅이 "#!/bin/sh\r" 가 되어 Linux 에서
# "./gradlew: not found" (exit 127)로 죽었다. .gitattributes 로 재발을 막아 뒀지만,
# 체크아웃 환경에 따라 다시 섞여 들어올 수 있으니 빌드 쪽에서도 한 번 더 정리한다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY shared/ shared/
COPY spring/ spring/

# 데몬은 컨테이너 한 번 쓰고 버릴 것이라 의미가 없고, 파일 와처는 컨테이너에서 자주 깨진다.
RUN ./gradlew :spring:bootJar --no-daemon --no-watch-fs -x test

# ── runtime ───────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre AS runtime

# SubscriptionService 와 UserDeletionScheduler 가 ZoneId.systemDefault() 를 쓴다.
# 컨테이너 기본 UTC 로 두면 구독 만료와 탈퇴 유예 계산이 9시간 어긋난다.
ENV TZ=Asia/Seoul \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# root 로 돌릴 이유가 없다.
RUN useradd --system --create-home --uid 10001 app
USER app
WORKDIR /app

# 글롭(*.jar)이 아니라 이름을 고정한다. `jar` 태스크가 같이 돌면 spring-plain.jar 가
# 나란히 생겨 COPY 가 "multiple files" 로 깨진다. 이름은 프로젝트명(spring) + 버전
# 미설정이라 spring.jar 다 - settings.gradle.kts 의 모듈명을 바꾸면 여기도 바뀐다.
COPY --from=builder --chown=app:app /build/spring/build/libs/spring.jar app.jar

# 이 ENV 는 플랫폼이 PORT 를 주지 않을 때의 기본값일 뿐이다. ENTRYPOINT 가 exec 형식이라
# 셸 확장이 없으므로 여기서 $PORT 를 참조할 수는 없다 - 우선순위는 application.yml 의
# `server.port: ${PORT:${SERVER_PORT:8080}}` 가 정한다(PORT > SERVER_PORT > 8080).
ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
