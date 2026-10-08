#!/usr/bin/env bash
# Driver for the Sleep repo: Android app (:shared) + Spring backend (:spring).
# Run from the repo root with Git Bash (the Bash tool). Windows paths only —
# no WSL/xvfb/tmux here, this is a real Windows dev machine, not a container.
#
# Usage: .claude/skills/run-sleep/driver.sh <command> [args]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$ROOT"

# --- Android SDK location (from local.properties, sdk.dir=...) ---
if [ -z "${ANDROID_HOME:-}" ]; then
  ANDROID_HOME=$(grep '^sdk.dir=' local.properties | cut -d= -f2- | sed 's/\\\\/\//g; s/\\:/:/g')
fi
ADB="$ANDROID_HOME/platform-tools/adb.exe"
EMULATOR="$ANDROID_HOME/emulator/emulator.exe"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager.bat"
export SKIP_JDK_VERSION_CHECK=1

AVD_NAME="run_skill_phone"
APK="$ROOT/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
APK_RELEASE="$ROOT/androidApp/build/outputs/apk/release/androidApp-release.apk"
APP_ID="com.soundsleeper.app"
MAIN_ACTIVITY="$APP_ID/com.soundsleeper.app.MainActivity"
OUT_DIR="$ROOT/.artifacts/run-skill"
SPRING_LOG="$OUT_DIR/spring_boot.log"
SPRING_PID_FILE="$OUT_DIR/spring.pid"

mkdir -p "$OUT_DIR"

cmd_avd_setup() {
  if "$EMULATOR" -list-avds | grep -qx "$AVD_NAME"; then
    echo "AVD '$AVD_NAME' already exists."
    return
  fi
  echo "no" | "$AVDMANAGER" create avd -n "$AVD_NAME" \
    -k "system-images;android-34;google_apis;x86_64" -d pixel_6 --force
}

cmd_emulator_start() {
  if "$ADB" devices | grep -q "^emulator-"; then
    echo "Emulator already running."
  else
    nohup "$EMULATOR" -avd "$AVD_NAME" -no-audio -no-boot-anim \
      -gpu swiftshader_indirect -no-snapshot > "$OUT_DIR/emulator.log" 2>&1 &
    echo "Emulator launching (pid $!), log: $OUT_DIR/emulator.log"
  fi
  "$ADB" wait-for-device
  for _ in $(seq 1 60); do
    boot=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    [ "$boot" = "1" ] && { echo "BOOTED"; return 0; }
    sleep 5
  done
  echo "Timed out waiting for boot" >&2
  return 1
}

cmd_emulator_stop() {
  "$ADB" emu kill || true
}

cmd_build_app() {
  ./gradlew.bat :androidApp:assembleDebug --console=plain
}

cmd_build_release() {
  ./gradlew.bat :androidApp:assembleRelease --console=plain
}

cmd_install_app() {
  "$ADB" install -r "$APK"
}

cmd_launch_app() {
  "$ADB" shell am start -n "$MAIN_ACTIVITY"
}

cmd_ss() {
  local out="${1:-$OUT_DIR/screenshot.png}"
  "$ADB" exec-out screencap -p > "$out"
  echo "Saved $out"
}

cmd_tap() {
  "$ADB" shell input tap "$1" "$2"
}

cmd_text() {
  "$ADB" shell input text "$1"
}

cmd_all_android() {
  cmd_avd_setup
  cmd_emulator_start
  cmd_build_app
  cmd_install_app
  cmd_launch_app
  sleep 5
  cmd_ss "$OUT_DIR/launch.png"
}

# --- tests ---

# 단위 테스트. Postgres/Redis/에뮬레이터 없이 돌아가야 한다 — 그래서 커밋 전 게이트로 쓸 수 있다.
cmd_unit() {
  ./gradlew.bat :shared:jvmTest --console=plain
}

# 커밋 전 기본 게이트: 조립 + 단위 테스트.
# 에뮬레이터(기동 수 분)와 Spring(DB/Redis 필요)은 의도적으로 제외한다.
cmd_verify() {
  echo "== [1/3] compile =="
  ./gradlew.bat :shared:compileKotlinJvm :spring:compileKotlin --console=plain || return 1
  echo "== [2/3] assemble debug apk =="
  cmd_build_app || return 1
  echo "== [3/3] unit tests =="
  if ! cmd_unit; then
    echo "단위 테스트 실패. 리포트: shared/build/reports/tests/jvmTest/index.html" >&2
    return 1
  fi
  echo "VERIFY OK"
}

cmd_instr() {
  # :shared는 com.android.kotlin.multiplatform.library 모듈이고 device test를 opt-in하지
  # 않았다(shared/src/androidDeviceTest가 없다). 그래서 connectedDebugAndroidTest 같은
  # 태스크는 아예 존재하지 않는다. 필요해지면 shared/build.gradle.kts의 android { } 안에
  # withDeviceTestBuilder { sourceSetTreeName = "test" } 를 추가하고 이 함수를 되살린다.
  echo "instrumented 테스트가 설정되어 있지 않다. :shared:jvmTest(driver.sh unit)를 쓸 것." >&2
  return 1
}

# minify된 release 빌드를 실제로 설치해 띄워 본다.
# proguard keep 규칙 누락으로 생기는 런타임 실패는 debug 빌드에서는 절대 재현되지 않는다.
cmd_release_smoke() {
  cmd_build_release || return 1
  if [ ! -f "$APK_RELEASE" ]; then
    echo "release APK가 없다. 서명 설정이 없으면 *-unsigned.apk 로 나온다:" >&2
    ls -1 "$ROOT/androidApp/build/outputs/apk/release/" >&2 || true
    return 1
  fi
  "$ADB" install -r "$APK_RELEASE" || return 1
  "$ADB" logcat -c
  cmd_launch_app
  sleep 8
  cmd_ss "$OUT_DIR/release_smoke.png"
  echo "-- release 빌드 치명 오류 --"
  "$ADB" logcat -d | grep -E "FATAL|ClassNotFoundException|NoSuchMethodError|NoClassDefFoundError|SerializationException|NoDefinitionFound|UnsatisfiedLinkError" | head -20 || echo "(없음)"
}

# --- Spring backend ---

cmd_spring_start() {
  nohup ./gradlew.bat :spring:bootRun --console=plain > "$SPRING_LOG" 2>&1 &
  echo $! > "$SPRING_PID_FILE"
  echo "Spring Boot launching (pid $(cat "$SPRING_PID_FILE")), log: $SPRING_LOG"
  for _ in $(seq 1 60); do
    grep -q "Started SleepApplicationKt" "$SPRING_LOG" 2>/dev/null && { echo "STARTED"; return 0; }
    grep -qi "APPLICATION FAILED TO START\|BUILD FAILED" "$SPRING_LOG" 2>/dev/null && { echo "FAILED"; tail -n 40 "$SPRING_LOG"; return 1; }
    sleep 5
  done
  echo "Timed out waiting for Spring Boot to start" >&2
  return 1
}

cmd_spring_stop() {
  if [ -f "$SPRING_PID_FILE" ]; then
    powershell -Command "Stop-Process -Id (Get-CimInstance Win32_Process -Filter \"CommandLine LIKE '%GradleDaemon%' OR CommandLine LIKE '%bootRun%'\" | Select-Object -ExpandProperty ProcessId) -Force -ErrorAction SilentlyContinue" || true
  fi
  powershell -Command "Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess | ForEach-Object { Stop-Process -Id \$_ -Force -ErrorAction SilentlyContinue }" || true
}

cmd_spring_smoke() {
  echo "-- public endpoint (expect 200/handled error, not empty 403) --"
  curl -s -i -X POST http://localhost:8080/auth/email/send \
    -H "Content-Type: application/json" -d '"driver-smoke-test@example.com"' | head -1
  echo "-- protected endpoint without token (expect 403) --"
  curl -s -i -X GET http://localhost:8080/api/sleep-session/user/1 | head -1
}

cmd="${1:-}"
shift || true
case "$cmd" in
  avd-setup) cmd_avd_setup ;;
  emulator-start) cmd_emulator_start ;;
  emulator-stop) cmd_emulator_stop ;;
  build-app) cmd_build_app ;;
  build-release) cmd_build_release ;;
  unit) cmd_unit ;;
  verify) cmd_verify ;;
  instr) cmd_instr ;;
  release-smoke) cmd_release_smoke ;;
  install-app) cmd_install_app ;;
  launch-app) cmd_launch_app ;;
  ss) cmd_ss "$@" ;;
  tap) cmd_tap "$@" ;;
  text) cmd_text "$@" ;;
  all-android) cmd_all_android ;;
  spring-start) cmd_spring_start ;;
  spring-stop) cmd_spring_stop ;;
  spring-smoke) cmd_spring_smoke ;;
  *)
    echo "Usage: driver.sh <avd-setup|emulator-start|emulator-stop|build-app|build-release|install-app|launch-app|ss [path]|tap x y|text str|all-android|unit|verify|instr|release-smoke|spring-start|spring-stop|spring-smoke>" >&2
    exit 1
    ;;
esac
