#!/usr/bin/env bash
set -euo pipefail

component=${1:?Pass the launcher Home component}
out=$(realpath -m "${2:?Pass an output directory}")
pkg=${component%/*}
cd "$(dirname "$0")/../../.."
source scripts/tv.sh
mkdir -p "$out"

focus() { tv shell dumpsys window | grep 'mCurrentFocus='; }

wait_focus() {
  local attempt
  for attempt in {1..10}; do
    if [[ $(focus) == *"$1/"* ]]; then return 0; fi
    sleep 0.25
  done
  return 1
}

settings=$(tv shell cmd package resolve-activity --brief -a android.settings.SETTINGS | tail -n 1 | tr -d '\r')
[[ $settings == */* ]]
settings_pkg=${settings%/*}
tv shell dumpsys thermalservice > "$out/thermal-before.txt"
for trial in {1..7}; do
  path="$out/$trial-cold.txt"
  echo "Running $pkg cold launch $trial"
  tv shell am start -W -a android.settings.SETTINGS > "$out/$trial-settings.txt" 2>&1
  if ! wait_focus "$settings_pkg"; then
    echo "Settings did not take focus; cold launch is unverified" >&2
    exit 1
  fi
  tv shell am force-stop "$pkg"
  if [[ -n $(tv shell pidof "$pkg" || true) ]]; then
    echo "Launcher restarted before the cold launch" >&2
    exit 1
  fi
  tv shell am start -W -n "$component" > "$path" 2>&1
  if ! wait_focus "$pkg" || ! grep -q '^Status: ok' "$path" ||
    grep -Eq '^LaunchState: (WARM|HOT)' "$path" || ! grep -Eq '^TotalTime: [1-9][0-9]*' "$path"; then
    echo "No valid cold launch timing or launcher focus in trial $trial" >&2
    exit 1
  fi
  grep '^TotalTime:' "$path"
done

echo "Measuring $pkg idle for 60 seconds"
sleep 15
pid=$(tv shell pidof "$pkg" | tr -d '\r')
[[ $pid =~ ^[0-9]+$ && $(focus) == *"$pkg/"* ]]
tv shell dumpsys meminfo "$pkg" > "$out/steady-idle-before.txt"
tv shell dumpsys gfxinfo "$pkg" reset > /dev/null
tv shell top -b -n 5 -d 15 -p "$pid" > "$out/steady-idle-cpu.txt"
[[ $(tv shell pidof "$pkg" | tr -d '\r') == "$pid" && $(focus) == *"$pkg/"* ]]
tv shell dumpsys gfxinfo "$pkg" > "$out/steady-idle-frames.txt"
tv shell dumpsys meminfo "$pkg" > "$out/steady-idle-after.txt"
tv shell dumpsys thermalservice > "$out/thermal-after.txt"
