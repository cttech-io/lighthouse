#!/bin/bash
# sweep.sh <port> <tag>: every device profile -> HOME, a platform tab, Settings, game options
cd ~/lighthouse-compat
PORT=$1; TAG=$2
while read -r -u 3 name w h dpi; do
  ./compat.sh profile $PORT $w $h $dpi >/dev/null
  ./compat.sh shot $PORT "$TAG-$name-1home" >/dev/null
  ./compat.sh keys $PORT R1 R1 R1 R1 >/dev/null; ./compat.sh shot $PORT "$TAG-$name-2tab" >/dev/null
  ./compat.sh keys $PORT START >/dev/null; sleep 1; ./compat.sh shot $PORT "$TAG-$name-3settings" >/dev/null
  ./compat.sh keys $PORT B SELECT >/dev/null; sleep 1; ./compat.sh shot $PORT "$TAG-$name-4options" >/dev/null
  ./compat.sh keys $PORT B HOME >/dev/null
  echo "done $name"
done 3<<PROFILES
portal 1920 1080 288
odin2 1920 1080 360
odin2mini 1920 1080 420
rpmini 1240 1080 320
rg406h 960 720 240
rgcube 720 720 240
rg405m 640 480 240
PROFILES
