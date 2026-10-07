#!/bin/sh
# Installs the release APK over the debug one (as an update from build 75 or
# earlier would on a TV), checks it is not debuggable, and that it starts and
# keeps running.
set -e
./gradlew -q assembleDebug assembleRelease
adb uninstall com.iyftv.app >/dev/null 2>&1 || true
adb install app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/release/app-release.apk
if adb shell dumpsys package com.iyftv.app | grep -q 'flags=.*DEBUGGABLE'; then
  echo "release APK is debuggable"; exit 1
fi
adb logcat -c
adb shell am start -W -n com.iyftv.app/.MainActivity
sleep 20
if ! adb shell pidof com.iyftv.app; then
  adb logcat -d -s AndroidRuntime:E | tail -60; echo "release app is not running"; exit 1
fi
adb logcat -d -s AndroidRuntime:E | grep -q 'FATAL EXCEPTION' && { adb logcat -d -s AndroidRuntime:E | tail -60; exit 1; }
echo "release APK installed over debug and runs"
