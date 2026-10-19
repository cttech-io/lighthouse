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
# Lighthouse is a home app: launch it through the HOME intent so there is exactly one instance
# (an explicit `am start -n` creates a second, ordinary task with its own state). If Lighthouse isn't the
# default home app this opens whichever is, so fall back to an explicit start.
default_home=$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r')
if [[ "$default_home" == io.cttech.lighthouse.${buildtype}/* ]]; then
  adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
else
  adb shell am start -n "io.cttech.lighthouse.${buildtype}/io.cttech.lighthouse.MainActivity"
fi
adb shell dumpsys gfxinfo "io.cttech.lighthouse.${buildtype}" reset >/dev/null   # fresh frame stats for this test
echo "Installed and launched (${flavour} ${buildtype})."
echo "Frame stats after testing: adb shell dumpsys gfxinfo io.cttech.lighthouse.${buildtype} | grep -E 'Janky|percentile'"
