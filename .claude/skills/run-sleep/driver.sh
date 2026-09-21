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
APK="$ROOT/shared/build/outputs/apk/debug/shared-debug.apk"
APP_ID="com.sleepytime.app"
MAIN_ACTIVITY="$APP_ID/com.sleepytime.shared.MainActivity"
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
  ./gradlew.bat :shared:assembleDebug --console=plain
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
    echo "Usage: driver.sh <avd-setup|emulator-start|emulator-stop|build-app|install-app|launch-app|ss [path]|tap x y|text str|all-android|spring-start|spring-stop|spring-smoke>" >&2
    exit 1
    ;;
esac
