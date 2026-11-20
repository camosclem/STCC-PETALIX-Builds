#!/usr/bin/env bash
set -euo pipefail

apk_root=launcher/app/build/outputs/apk
app_apk=$(find "$apk_root/full/debug" -type f -name '*.apk' -print -quit)
test_apk=$(find "$apk_root/androidTest/full/debug" -type f -name '*.apk' -print -quit)
adb install -r -t "$app_apk"
adb install -r -t "$test_apk"
adb shell wm size 1920x1080
adb shell wm density 320
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard

package=com.petalix.iptv.hometest
runner=$(adb shell pm list instrumentation | tr -d '\r' | sed -n "s/^instrumentation:\([^ ]*\) (target=$package)$/\1/p")
test -n "$runner"
classes=com.nuvio.tv.petalix.PetalixGlassRemoteTest,com.nuvio.tv.livetv.sports.SportsMatcherPlaybackTest
mkdir -p verification out
adb shell am instrument -w -e class "$classes" "$runner" | tee verification/tv-tests.txt
adb logcat -d -s AndroidRuntime:E TestRunner:E > verification/android-errors.txt
grep -Eq '^OK \(8 tests\)' verification/tv-tests.txt
for name in Petalix-Guide Petalix-Categories Petalix-Guide-Settings Petalix-Play-Bar Petalix-Service-Details; do
    adb exec-out cat "/sdcard/Pictures/PetalixVerification/$name.png" > "out/$name.png"
done
python3 - <<'PY'
from PIL import Image, ImageChops
from pathlib import Path
frames = []
for name in ("Petalix-Guide", "Petalix-Categories", "Petalix-Guide-Settings", "Petalix-Play-Bar", "Petalix-Service-Details"):
    with Image.open(Path("out") / (name + ".png")) as image:
        image.load()
        assert image.width >= 1280 and image.width > image.height
        frames.append(image.convert("RGB"))
assert all(frame.size == frames[0].size for frame in frames)
assert all(ImageChops.difference(frames[0], frame).getbbox() for frame in frames[1:])
PY
