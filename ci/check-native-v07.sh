#!/usr/bin/env bash
set -euo pipefail
PKG=com.petalix.tv.guidebeta
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Seed a small synthetic local-channel catalog on the *emulator only*.
adb shell run-as "$PKG" mkdir -p shared_prefs files
printf '%s\n' '<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><boolean name="demo_mode" value="true" /></map>' \
 | adb shell run-as "$PKG" tee shared_prefs/petalix_pairing.xml >/dev/null
printf '%s\n' '[{"id":"1","name":"ABC WSOC TV Charlotte","group":"NC Locals","url":"https://example.com/abc.m3u8"},{"id":"2","name":"CBS WBTV Charlotte","group":"NC Locals","url":"https://example.com/cbs.m3u8"},{"id":"3","name":"NBC WCNC Charlotte","group":"NC Locals","url":"https://example.com/nbc.m3u8"},{"id":"4","name":"FOX WJZY Charlotte","group":"NC Locals","url":"https://example.com/fox.m3u8"}]' \
 | adb shell run-as "$PKG" tee files/petalix-native-catalog.jsonl >/dev/null
adb logcat -c
adb shell am start -n "$PKG/com.openguide.tv.NativeTvActivity"
sleep 5
adb shell uiautomator dump /sdcard/petalix-home.xml >/dev/null
adb shell cat /sdcard/petalix-home.xml > petalix-v062-home.xml
adb exec-out screencap -p > petalix-v062-home.png
if ! grep -q 'My Locals' petalix-v062-home.xml; then
  echo 'Native My Locals navigation is missing' >&2
  exit 1
fi
# Tap the actual button bounds rather than assuming TV screen dimensions.
read x y < <(python3 - <<'PY'
import xml.etree.ElementTree as ET,re
root=ET.parse('petalix-v062-home.xml').getroot()
for node in root.iter('node'):
  if node.get('text')=='My Locals' and node.get('clickable')=='true':
    a,b,c,d=map(int,re.findall(r'\d+',node.get('bounds','')))
    print((a+c)//2,(b+d)//2)
    break
else: raise RuntimeError('My Locals button was not clickable')
PY
)
adb shell input tap "$x" "$y"
sleep 2
adb shell uiautomator dump /sdcard/petalix-locals.xml >/dev/null
adb shell cat /sdcard/petalix-locals.xml > petalix-v062-locals.xml
adb exec-out screencap -p > petalix-v062-locals.png
if ! grep -q 'Find my local stations' petalix-v062-locals.xml; then
  echo 'Native ZIP screen did not open' >&2
  exit 1
fi
# Exercise ZIP input and lookup using native focusable controls.
read x y < <(python3 - <<'PY'
import xml.etree.ElementTree as ET,re
root=ET.parse('petalix-v062-locals.xml').getroot()
for node in root.iter('node'):
  if node.get('class')=='android.widget.EditText':
    a,b,c,d=map(int,re.findall(r'\d+',node.get('bounds','')))
    print((a+c)//2,(b+d)//2);break
else: raise RuntimeError('ZIP edit field missing')
PY
)
adb shell input tap "$x" "$y"
adb shell input text 28601
adb shell input keyevent KEYCODE_BACK
# Request focus and activate the action using an Android TV remote key.
adb shell uiautomator dump /sdcard/petalix-zipped.xml >/dev/null
adb shell cat /sdcard/petalix-zipped.xml > petalix-v062-zipped.xml
read x y < <(python3 - <<'PY'
import xml.etree.ElementTree as ET,re
root=ET.parse('petalix-v062-zipped.xml').getroot()
for node in root.iter('node'):
  if node.get('text')=='Find my local stations':
    a,b,c,d=map(int,re.findall(r'\d+',node.get('bounds','')))
    print((a+c)//2,(b+d)//2);break
else: raise RuntimeError('ZIP find button missing')
PY
)
adb shell input tap "$x" "$y"
sleep 2
adb shell uiautomator dump /sdcard/petalix-zip-result.xml >/dev/null
adb shell cat /sdcard/petalix-zip-result.xml > petalix-v062-result.xml
adb exec-out screencap -p > petalix-v062-result.png
if ! grep -q 'Charlotte / Hickory' petalix-v062-result.xml; then
  echo 'ZIP 28601 did not resolve to Charlotte/Hickory market' >&2
  exit 1
fi
adb logcat -d -v threadtime > petalix-v062-log.txt
if grep -q 'FATAL EXCEPTION' petalix-v062-log.txt; then
  grep -A 40 -B 3 'FATAL EXCEPTION' petalix-v062-log.txt | tail -n 100
  exit 1
fi
echo 'PASS: native TV navigation, native ZIP screen and Hickory market lookup work without startup crash'
# Confirm Home and Guide are reached as native Activities; this beta must not launch web Home.
adb shell am start -n "$PKG/com.openguide.tv.NativeTvActivity"
sleep 2
adb shell uiautomator dump /sdcard/petalix-v07-nav.xml >/dev/null
adb shell cat /sdcard/petalix-v07-nav.xml > petalix-v07-nav.xml
if ! grep -q 'PETALIX' petalix-v07-nav.xml; then
  echo 'Native Petalix navigation missing' >&2
  exit 1
fi
# Enter the private Guide through the app's real Live tab, not an external
# shell intent (Android correctly blocks external launch of non-exported Activities).
read gx gy < <(python3 - <<'PY'
import xml.etree.ElementTree as ET,re
root=ET.parse('petalix-v07-nav.xml').getroot()
for node in root.iter('node'):
  if node.get('text')=='Live' and node.get('clickable')=='true':
    parts=[int(item) for item in re.findall('[0-9]+',node.get('bounds',''))]
    if len(parts)!=4: raise RuntimeError('Live tab has invalid bounds: '+repr(node.attrib))
    a,b,c,d=parts
    print((a+c)//2,(b+d)//2)
    break
else: raise RuntimeError('Native Live tab missing')
PY
)
adb shell input tap "$gx" "$gy"
sleep 3
adb shell dumpsys activity activities > petalix-v07-activity.txt
if ! grep -E 'topResumedActivity|mResumedActivity' petalix-v07-activity.txt | grep -q 'GuideActivity'; then
  echo 'Native Guide did not open from Live tab' >&2
  grep -E 'topResumedActivity|mResumedActivity' petalix-v07-activity.txt || true
  exit 1
fi
adb exec-out screencap -p > petalix-v07-guide.png
echo 'PASS: Guide opened via native remote navigation'
adb logcat -d -v threadtime > petalix-v062-log.txt
if grep -q 'FATAL EXCEPTION' petalix-v062-log.txt; then
  grep -A 35 -B 2 'FATAL EXCEPTION' petalix-v062-log.txt | tail -n 100
  exit 1
fi
