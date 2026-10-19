#!/usr/bin/env bash
# Build, install and launch Lighthouse on a USB-connected device, then save a screenshot.
# Usage: scripts/deploy.sh [sideload|play]   (default: sideload)
# Launches with `am start` — never `monkey`, which can inject rotation events.
set -euo pipefail
cd "$(dirname "$0")/.."

flavour="${1:-sideload}"
Flavour="$(tr '[:lower:]' '[:upper:]' <<<"${flavour:0:1}")${flavour:1}"
: "${JAVA_HOME:=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export JAVA_HOME

./gradlew --console=plain -q "assemble${Flavour}Debug"
adb install -r "app/build/outputs/apk/${flavour}/debug/app-${flavour}-debug.apk"
adb shell input keyevent KEYCODE_WAKEUP
adb shell am start -n io.cttech.lighthouse.debug/io.cttech.lighthouse.MainActivity
sleep 2
mkdir -p build/screens
shot="build/screens/$(date +%Y%m%d-%H%M%S).png"
adb exec-out screencap -p > "$shot"
echo "Screenshot: $shot"
