#!/usr/bin/env bash
# Build, install and launch Lighthouse on a USB-connected device, then save a screenshot.
# Usage: scripts/deploy.sh [sideload|play] [profile|debug]   (default: sideload profile)
# "profile" is release-optimised and is the build to judge smoothness on; "debug" builds faster but is much slower to run.
# Launches with `am start` — never `monkey`, which can inject rotation events.
# Does not wake, unlock or press buttons on the device: the person testing drives it.
set -euo pipefail
cd "$(dirname "$0")/.."

flavour="${1:-sideload}"
buildtype="${2:-profile}"
cap() { echo "$(tr '[:lower:]' '[:upper:]' <<<"${1:0:1}")${1:1}"; }
: "${JAVA_HOME:=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export JAVA_HOME

./gradlew --console=plain -q "assemble$(cap "$flavour")$(cap "$buildtype")"
adb install -r "app/build/outputs/apk/${flavour}/${buildtype}/app-${flavour}-${buildtype}.apk"
adb shell am start -n "io.cttech.lighthouse.${buildtype}/io.cttech.lighthouse.MainActivity"
adb shell dumpsys gfxinfo "io.cttech.lighthouse.${buildtype}" reset >/dev/null   # fresh frame stats for this test
echo "Installed and launched (${flavour} ${buildtype})."
echo "Frame stats after testing: adb shell dumpsys gfxinfo io.cttech.lighthouse.${buildtype} | grep -E 'Janky|percentile'"
