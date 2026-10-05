#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
linedraw_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
linedraw_adb="$linedraw_sdk/platform-tools/adb"
linedraw_serial="${ANDROID_SERIAL:?請指定專用模擬器的 ANDROID_SERIAL}"
case "$linedraw_serial" in emulator-*) ;; *) echo '只允許專用模擬器，拒絕操作實體手機。' >&2; exit 1;; esac
linedraw_avd="$("$linedraw_adb" -s "$linedraw_serial" emu avd name | tr -d '\r' | head -1)"
case "$linedraw_avd" in LineDraw_*) ;; *) echo '只允許名稱以 LineDraw_ 開頭的專用 AVD。' >&2; exit 1;; esac
./gradlew :app:testDebugUnitTest :app:assembleDebug :fixture:assembleDebug --console=plain
"$linedraw_adb" -s "$linedraw_serial" install -r fixture/build/outputs/apk/debug/fixture-debug.apk
ANDROID_SERIAL="$linedraw_serial" ./gradlew :app:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.linedraw.app.UsageConsentUiTest,com.linedraw.app.AppUiTest,com.linedraw.app.FiveLinkAreaUiTest,com.linedraw.app.AccessibilityFlowTest#endedPeriodInNativeAndWebViewsContinuesToNextDraw,com.linedraw.app.AccessibilityFlowTest#friendThenDrawAndAlreadyContinueWithoutDuplicateSubmit,com.linedraw.app.AccessibilityFlowTest#captchaPausesBeforeAnySideEffect,com.linedraw.app.AccessibilityFlowTest#missingUsageConsentStopsPersistedBatchBeforeAnyDispatch' \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --console=plain
