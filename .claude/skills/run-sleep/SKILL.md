---
name: run-sleep
description: Build, run, and drive the Sleep repo's Android app (:shared, Compose UI on an emulator) and Spring backend (:spring, REST API). Use when asked to start the app, launch the emulator, build the APK, take a screenshot of the UI, start the backend, or smoke-test its endpoints.
---

Two independently runnable units share this repo root: `:shared` (Kotlin Multiplatform Android app, Compose UI) and `:spring` (Spring Boot REST API, depends on `:shared`'s JVM target). Drive both via `.claude/skills/run-sleep/driver.sh <command>` (Git Bash / the Bash tool — this is a real Windows dev machine, not a Linux container, so there's no xvfb/tmux/apt-get here).

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
./gradlew.bat :shared:assembleDebug --console=plain   # ~3 min cold, produces shared/build/outputs/apk/debug/shared-debug.apk
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
| `driver.sh build-app` | `gradlew.bat :shared:assembleDebug` |
| `driver.sh install-app` | `adb install -r` the debug APK |
| `driver.sh launch-app` | `adb shell am start` on `com.sleepytime.app/com.sleepytime.shared.MainActivity` |
| `driver.sh ss [path]` | `adb exec-out screencap -p` to a PNG (default `.artifacts/run-skill/screenshot.png`) |
| `driver.sh tap X Y` / `driver.sh text STR` | `adb shell input tap/text` |
| `driver.sh emulator-stop` | `adb emu kill` |

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

No test suite was exercised this session (not asked); `./gradlew.bat :shared:test :spring:test` is the standard Gradle path if needed later.

---

## Gotchas

- **`avdmanager.bat` hard-rejects JDK 25** with "Java version 17 or higher is required" even though 25 ≥ 17 — it's a known upper-bound check in the cmdline-tools script, not a real incompatibility. Fix: `export SKIP_JDK_VERSION_CHECK=1` before calling it (the driver already does this).
- **Every unhandled exception on a `permitAll()` endpoint still comes back as a bare, empty-body `403`**, not the real status. Cause: `JwtAuthenticationFilter` runs on *every* request (including whitelisted ones) and throws when the `Authorization` header is absent/malformed; combined with `/error` not being in `SecurityConfig`'s `permitAll()` list, Spring's internal forward to `/error` itself gets denied, masking the real error (verified with two different root causes this session — a `jakarta.mail.internet.AddressException` from a malformed request body, and a real `MailAuthenticationException` from a stale Gmail app-password in `.env` — both surfaced to `curl` as identical empty `403`s). To see what actually failed, always check `.artifacts/run-skill/spring_boot.log`, never trust the HTTP status alone.
- **`@RequestBody email: String` on `/auth/email/send` expects a bare JSON string** (`"foo@example.com"`), not `{"email": "foo@example.com"}` — sending the object form gets deserialized as literal text and fails mail parsing instead of 400ing cleanly.
- **A freshly created AVD's first `am start` can land on a "System UI isn't responding" ANR dialog** instead of the app — this is the emulator's own SystemUI catching up right after boot, unrelated to the app under test. Tap "Wait" (`driver.sh tap 550 1240` at the AVD's default resolution) or just wait ~10s and re-run `launch-app`; it does not recur once the emulator has been up for a bit.
- **`./gradlew.bat :shared:compileDebugKotlinAndroid` built successfully via plain CLI in this session even with Android Studio open** in the background — so don't assume a file-lock conflict without actually trying the build first.
- Kotlin 2.x prints `Kotlin does not yet support 25 JDK target, falling back to Kotlin JVM_23 JVM target` and AGP 8.7.3 warns about `compileSdk = 36` on every build — both are harmless noise, not build failures.

## Troubleshooting

- **`java.lang.Exception` about DB connection / Hikari failing to start**: Postgres or Redis Windows service isn't running. `powershell -Command "Get-Service postgresql-x64-18, Redis"` to check, `Start-Service` to fix.
- **`spring-smoke` hangs on the mail-send call**: it's actually reaching Gmail's SMTP over the real network (`mail.properties.mail.smtp.debug: true` in `application.yml` logs the handshake) — check `.artifacts/run-skill/spring_boot.log` for `AuthenticationFailedException` if the configured app password is stale; this is a credentials issue, not a driver/build issue.
