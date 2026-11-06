#!/usr/bin/env bash
set -euo pipefail
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -n com.petalix.tv.guidebeta/com.openguide.tv.NativeTvActivity
sleep 9
adb logcat -d -v threadtime > native-v06-log.txt
adb shell dumpsys activity activities > native-v06-state.txt
adb exec-out screencap -p > native-v06-launch.png
if grep -q 'FATAL EXCEPTION' native-v06-log.txt; then
  grep -A 40 -B 4 'FATAL EXCEPTION' native-v06-log.txt | tail -n 90
  exit 1
fi
if ! grep -Eq '(ActivationActivity|NativeTvActivity)' native-v06-state.txt; then
  echo 'Neither PIN activation nor native home was on screen'
  grep -E 'ResumedActivity|topResumedActivity' native-v06-state.txt || true
  exit 1
fi
echo 'PASS: native launcher reaches activation screen without Android startup crash'
