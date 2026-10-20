#!/bin/bash
# Lighthouse compatibility driver for headless Android emulators (Linux + KVM).
#   compat.sh boot <api> <port>          create (once) and boot an emulator, install Lighthouse + test games
#   compat.sh profile <port> <W> <H> <dpi>   switch the screen to a device profile and restart Lighthouse
#   compat.sh keys <port> KEY...         press keys (KEYCODE_ names, short names allowed: A B START DOWN…)
#   compat.sh shot <port> <name>         screenshot to ~/lighthouse-compat/shots/<name>.png
#   compat.sh reset <port>               clear Lighthouse's data (back to first-run setup)
#   compat.sh stop <port>
set -e
export ANDROID_HOME=$HOME/Android/Sdk
PATH=$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
W=$HOME/lighthouse-compat
PKG=io.cttech.lighthouse.profile
cmd=$1; shift

adbs() { adb -s "emulator-$PORT" "$@"; }
start_home() {
  adbs shell cmd role add-role-holder android.app.role.HOME $PKG 0 >/dev/null 2>&1 || true
  adbs shell am force-stop $PKG
  adbs shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
  sleep 4
}

case $cmd in
boot)
  API=$1; PORT=$2; AVD=lh$API
  if ! avdmanager list avd -c | grep -qx "$AVD"; then
    echo no | avdmanager create avd -n "$AVD" -k "system-images;android-$API;google_apis;x86_64" -d pixel_6 >/dev/null
    cat >> "$HOME/.android/avd/$AVD.avd/config.ini" <<EOF
hw.lcd.width=1920
hw.lcd.height=1080
hw.lcd.density=280
hw.initialOrientation=landscape
hw.keyboard=yes
hw.dPad=yes
hw.ramSize=4096
disk.dataPartition.size=6G
EOF
  fi
  nohup emulator -avd "$AVD" -port "$PORT" -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect \
    >"$W/emulator-$API.log" 2>&1 &
  adbs wait-for-device
  until [ "$(adbs shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 2; done
  adbs shell settings put system user_rotation 1
  adbs shell settings put system accelerometer_rotation 0
  adbs install -r -g "$W/lighthouse.apk" >/dev/null
  adbs push "$W/Games" /sdcard/ >/dev/null
  adbs shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow
  adbs shell cmd package set-home-activity $PKG/io.cttech.lighthouse.MainActivity >/dev/null
  start_home
  echo "booted API $API on emulator-$PORT"
  ;;
profile)
  PORT=$1; adbs shell wm size "$2x$3"; adbs shell wm density "$4"; start_home ;;
keys)
  PORT=$1; shift
  for k in "$@"; do
    case $k in A|B|X|Y|L1|R1|START|SELECT) k=KEYCODE_BUTTON_$k ;; UP|DOWN|LEFT|RIGHT) k=KEYCODE_DPAD_$k ;; HOME) k=KEYCODE_HOME ;; esac
    adbs shell input keyevent "$k"; sleep 0.5
  done ;;
shot)
  PORT=$1; mkdir -p "$W/shots"; sleep 1; adbs exec-out screencap -p > "$W/shots/$2.png"; echo "$W/shots/$2.png" ;;
reset)
  PORT=$1; adbs shell pm clear $PKG >/dev/null; adbs shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow; start_home ;;
stop)
  PORT=$1; adbs emu kill ;;
*) echo "unknown command $cmd"; exit 1 ;;
esac
