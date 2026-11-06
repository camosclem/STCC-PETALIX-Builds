#!/usr/bin/env bash
set -euo pipefail
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -n com.petalix.tv.guidebeta/com.openguide.tv.MainActivity
sleep 8
adb logcat -d -v threadtime > startup-log.txt
adb shell dumpsys activity activities > activity-state.txt
adb exec-out screencap -p > launch.png
if grep -q 'FATAL EXCEPTION' startup-log.txt; then
  grep -A 50 -B 3 'FATAL EXCEPTION' startup-log.txt | tail -n 100
  exit 1
fi
if ! grep -q 'com.petalix.tv.guidebeta/com.openguide.tv.ActivationActivity' activity-state.txt; then
  echo "PIN activity not visible in Android activity state"
  grep -E 'mResumedActivity|mFocusedApp|topResumedActivity' activity-state.txt || true
  exit 1
fi
echo "PASS: PIN activation screen remains open with no uncaught Android exception."
