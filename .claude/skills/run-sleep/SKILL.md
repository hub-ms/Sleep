---
name: run-sleep
description: Build, run, and drive the Sleep repo's Android app (:shared, Compose UI on an emulator) and Spring backend (:spring, REST API). Use when asked to start the app, launch the emulator, build the APK, take a screenshot of the UI, start the backend, or smoke-test its endpoints.
---

Three modules share this repo root: `:shared` (KMP 라이브러리 — Compose UI와 Android 플랫폼 코드 전부가 여기 있다. `com.android.kotlin.multiplatform.library` 플러그인), `:androidApp` (`com.android.application` — Application 클래스/매니페스트/BuildConfig/minify만 담은 얇은 래퍼) and `:spring` (Spring Boot REST API, depends on `:shared`'s JVM target). Drive both via `.claude/skills/run-sleep/driver.sh <command>` (Git Bash / the Bash tool — this is a real Windows dev machine, not a Linux container, so there's no xvfb/tmux/apt-get here).

All paths below are relative to the repo root (`C:\Users\dream\study\Sleep`).

## Prerequisites

- Windows machine with Android Studio's SDK already installed at the path in `local.properties` (`sdk.dir=...`). The driver reads that automatically.
- A `system-images;android-34;google_apis;x86_64` image already downloaded (check `%ANDROID_HOME%\system-images\android-34\google_apis\x86_64`). If missing: `sdkmanager.bat "system-images;android-34;google_apis;x86_64"`.
- JDK 25 is on `PATH` (`java -version`). `avdmanager.bat` refuses to run on it ("Java version 17 or higher is required") unless `SKIP_JDK_VERSION_CHECK=1` is set — the driver sets this itself.
- For `:spring`: PostgreSQL and Redis running as local Windows services (not Docker — none is installed here), and `spring/.env` populated (`DB_*`, `REDIS_*`, `MAIL_*`, `JWT_SECRET`). Verified present this session; if missing, `spring:bootRun` fails fast in the Hikari/Redis connection step.

```bash
# confirms both services are reachable before starting spring
powershell -Command "Test-NetConnection -ComputerName localhost -Port 5432 -WarningAction SilentlyContinue | Select-Object TcpTestSucceeded"
powershell -Command "Test-NetConnection -ComputerName localhost -Port 6379 -WarningAction SilentlyContinue | Select-Object TcpTestSucceeded"
```

## Build

```bash
./gradlew.bat :androidApp:assembleDebug --console=plain   # ~3-7 min, produces androidApp/build/outputs/apk/debug/androidApp-debug.apk
./gradlew.bat :spring:compileKotlin --console=plain    # ~40s cold
```

Both succeeded via plain CLI in this session (no IDE needed).

## Run (agent path)

### Android app (`:shared`)

```bash
bash .claude/skills/run-sleep/driver.sh all-android
```

This creates the `run_skill_phone` AVD if missing (Pixel 6 shape, API 34 `google_apis` image — no Play Store, boots faster), boots the emulator, builds the debug APK, installs it, launches `MainActivity`, and saves a screenshot to `.artifacts/run-skill/launch.png`. First boot of a fresh AVD ends with a transient "System UI isn't responding" ANR dialog (harmless — see Gotchas); a second `launch-app` + `ss` a few seconds later shows the real UI.

Individual steps (reuse a device you already booted):

| command | what it does |
|---|---|
| `driver.sh avd-setup` | create the AVD if it doesn't exist yet |
| `driver.sh emulator-start` | boot it (or no-op if one's already running), blocks until `sys.boot_completed=1` |
| `driver.sh build-app` | `gradlew.bat :androidApp:assembleDebug` |
| `driver.sh build-release` | `gradlew.bat :androidApp:assembleRelease` (minified; currently **unsigned** — no release signingConfig exists) |
| `driver.sh install-app` | `adb install -r` the debug APK |
| `driver.sh launch-app` | `adb shell am start` on `com.soundsleeper.app/com.sleepytime.shared.MainActivity` |
| `driver.sh ss [path]` | `adb exec-out screencap -p` to a PNG (default `.artifacts/run-skill/screenshot.png`) |
| `driver.sh tap X Y` / `driver.sh text STR` | `adb shell input tap/text` |
| `driver.sh emulator-stop` | `adb emu kill` |
| `driver.sh unit` | `gradlew.bat :shared:jvmTest` — needs no emulator, DB or Redis |
| `driver.sh verify` | **commit-time gate**: compile → assemble debug APK → unit tests. Deliberately excludes the emulator (minutes to boot) and Spring (needs Postgres+Redis) so it always runs |
| `driver.sh instr` | **사용 불가** — `:shared`는 KMP 라이브러리 모듈이고 device test를 opt-in하지 않아 `connectedDebugAndroidTest` 태스크가 없다 |
| `driver.sh release-smoke` | builds+installs the minified release APK, launches it, screenshots, and greps logcat for R8-class failures (`ClassNotFoundException`, `SerializationException`, `NoDefinitionFound`, …) |

Screenshots/logs land in `.artifacts/run-skill/`.

### Spring backend (`:spring`)

```bash
bash .claude/skills/run-sleep/driver.sh spring-start   # blocks until "Started SleepApplicationKt" or failure, logs to .artifacts/run-skill/spring_boot.log
bash .claude/skills/run-sleep/driver.sh spring-smoke    # curls one public + one protected endpoint, prints status lines
bash .claude/skills/run-sleep/driver.sh spring-stop     # kills whatever is bound to :8080
```

`spring-start` really boots Tomcat on port 8080 and opens a live Hikari pool to `sleep_db` — confirmed in this session (`Started SleepApplicationKt in 13.057 seconds`).

## Run (human path)

Android Studio → Run on the `shared` app config → picks an emulator/device and installs normally. For Spring, `./gradlew.bat :spring:bootRun` from a terminal, Ctrl+C to stop — same as `driver.sh spring-start`/`spring-stop` but foregrounded.

## Test

Test infrastructure now exists (it did not before 2026-10-01). `shared/src/commonTest` is the main stage, run on the JVM target:

```bash
./gradlew.bat :shared:jvmTest --console=plain     # ~4s warm; report at shared/build/reports/tests/jvmTest/index.html
bash .claude/skills/run-sleep/driver.sh verify     # compile + assemble + unit tests
```

Wiring notes, so you don't re-derive them:
- `commonTest` has `kotlin("test")`, `kotlinx-coroutines-test`, `ktor-client-mock`; `jvmTest` adds `sqldelight-sqlite-driver`. Declared in `shared/build.gradle.kts`'s `sourceSets` block.
- **Robolectric, MockK and Compose UI test are deliberately absent.** Most logic lives in `commonMain`, where MockK cannot be used at all; hand-written fakes are the convention here. Robolectric adds three independent risks (JDK support, no SDK-37 jar, runtime jar downloads) for coverage the emulator already gives.
- JDK 25 + Gradle 9.6.0 + `org.gradle.configuration-cache=true` runs `jvmTest` fine — **no Java toolchain pin is needed.** Verified, don't add one speculatively.
- `:spring` has `spring-boot-starter-test` declared but still no `src/test`. `@WebMvcTest` slices are the intended path (no Docker on this machine, so Testcontainers is out).

---

## Gotchas

- **`avdmanager.bat` hard-rejects JDK 25** with "Java version 17 or higher is required" even though 25 ≥ 17 — it's a known upper-bound check in the cmdline-tools script, not a real incompatibility. Fix: `export SKIP_JDK_VERSION_CHECK=1` before calling it (the driver already does this).
- **Every unhandled exception on a `permitAll()` endpoint still comes back as a bare, empty-body `403`**, not the real status. Cause: `JwtAuthenticationFilter` runs on *every* request (including whitelisted ones) and throws when the `Authorization` header is absent/malformed; combined with `/error` not being in `SecurityConfig`'s `permitAll()` list, Spring's internal forward to `/error` itself gets denied, masking the real error (verified with two different root causes this session — a `jakarta.mail.internet.AddressException` from a malformed request body, and a real `MailAuthenticationException` from a stale Gmail app-password in `.env` — both surfaced to `curl` as identical empty `403`s). To see what actually failed, always check `.artifacts/run-skill/spring_boot.log`, never trust the HTTP status alone.
- **`@RequestBody email: String` on `/auth/email/send` expects a bare JSON string** (`"foo@example.com"`), not `{"email": "foo@example.com"}` — sending the object form gets deserialized as literal text and fails mail parsing instead of 400ing cleanly.
- **A freshly created AVD's first `am start` can land on a "System UI isn't responding" ANR dialog** instead of the app — this is the emulator's own SystemUI catching up right after boot, unrelated to the app under test. Tap "Wait" (`driver.sh tap 550 1240` at the AVD's default resolution) or just wait ~10s and re-run `launch-app`; it does not recur once the emulator has been up for a bit.
- **`./gradlew.bat :shared:compileAndroidMain` built successfully via plain CLI in this session even with Android Studio open** in the background — so don't assume a file-lock conflict without actually trying the build first.
- Kotlin 2.x prints `Kotlin does not yet support 25 JDK target, falling back to Kotlin JVM_23 JVM target` — harmless noise, not a build failure. (AGP는 9.4.1, compileSdk는 37, Gradle wrapper는 9.6.0이다 — AGP 9.4는 Gradle 9.6.0 미만을 거부한다.)

## Troubleshooting

- **`java.lang.Exception` about DB connection / Hikari failing to start**: Postgres or Redis Windows service isn't running. `powershell -Command "Get-Service postgresql-x64-18, Redis"` to check, `Start-Service` to fix.
- **`spring-smoke` hangs on the mail-send call**: it's actually reaching Gmail's SMTP over the real network (`mail.properties.mail.smtp.debug: true` in `application.yml` logs the handshake) — check `.artifacts/run-skill/spring_boot.log` for `AuthenticationFailedException` if the configured app password is stale; this is a credentials issue, not a driver/build issue.
