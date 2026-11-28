#!/usr/bin/env bash
set -euo pipefail

launcher=${1:?Pass the launcher Home component}
out=$(realpath -m "${2:?Pass an output directory}")
shift 2
(( $# > 0 )) || { echo 'Pass one or more application package names' >&2; exit 2; }
pkg=${launcher%/*}
cd "$(dirname "$0")/../../.."
source scripts/tv.sh
mkdir -p "$out"

focus() { tv shell dumpsys window | grep 'mCurrentFocus='; }

for app in "$@"; do
  [[ $app =~ ^[a-zA-Z0-9_.]+$ ]]
  activity=$(tv shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
    -c android.intent.category.LEANBACK_LAUNCHER -p "$app" | tail -n 1 | tr -d '\r')
  [[ $activity == "$app/"* ]]
  for trial in {0..3}; do
    prefix="$out/$app-$trial"
    echo "Running $app on $pkg, trial $trial"
    [[ $(focus) == *"$pkg/"* ]]
    tv shell am start -W -a android.intent.action.MAIN \
      -c android.intent.category.LEANBACK_LAUNCHER -f 0x10200000 -n "$activity" > "$prefix.launch.txt" 2>&1
    sleep 5
    [[ $(focus) == *"$app/"* ]]
    if (( trial > 0 )); then
      tv shell dumpsys meminfo "$pkg" > "$prefix.launcher-background.txt"
      tv shell dumpsys meminfo "$app" > "$prefix.app-foreground.txt"
      tv shell cat /proc/meminfo > "$prefix.system-memory.txt"
    fi
    tv shell dumpsys gfxinfo "$pkg" reset > /dev/null
    tv shell "start=\$(cut -d ' ' -f 1 /proc/uptime)
      input keyevent 3
      for n in \$(seq 1 60); do
        current=\$(dumpsys window | grep mCurrentFocus)
        case \"\$current\" in
          *'$pkg/'*) end=\$(cut -d ' ' -f 1 /proc/uptime); echo \"\$start \$end\"; exit 0 ;;
        esac
        sleep 0.05
      done
      exit 1" > "$prefix.home-focus.txt"
    sleep 2
    [[ $(focus) == *"$pkg/"* ]]
    if (( trial > 0 )); then
      tv shell dumpsys gfxinfo "$pkg" > "$prefix.home-frames.txt"
      tv shell dumpsys meminfo "$pkg" > "$prefix.launcher-returned.txt"
    fi
  done
done
