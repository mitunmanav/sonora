#!/usr/bin/env bash
# Same D-pad walk as walk.sh, but saves the raw per-frame data. No OK key is sent.
# usage: PKG=... ACT=... OUT=file capture.sh
T=${SONORA_TV:?Set SONORA_TV to the target device ADB address}
pkg=${PKG:-com.mitun.sonora}
ACT=${ACT:-.HomeActivity}
GAP=${GAP:-0.45}
tv() { adb -s "$T" "$@"; }
keys() {
  for key in "$@"; do
    f=$(tv shell dumpsys window | grep mCurrentFocus)
    if [[ $f != *"$pkg/"* ]]; then
      sleep 1.5
      f=$(tv shell dumpsys window | grep mCurrentFocus)
      if [[ $f != *"$pkg/"* ]]; then echo "stopped, focus is elsewhere: $f" >&2; return 3; fi
    fi
    tv shell input keyevent "$key"
    sleep "$GAP"
  done
}
tv shell am force-stop "$pkg"
tv shell am start -n "$pkg/$ACT" >/dev/null
sleep 6
tv shell dumpsys gfxinfo "$pkg" reset >/dev/null
: > "$OUT"
# The system keeps only the last 120 frames, so dump after every 3 keys; stages.py merges them.
IFS='|' read -ra GROUPS_ <<< "${KEYSEQ:-22 22 22|22 20 22|22 21 21|19 21 21}"
for group in "${GROUPS_[@]}"; do
  keys $group
  tv shell dumpsys gfxinfo "$pkg" framestats >> "$OUT"
done
sleep 1
tv shell dumpsys gfxinfo "$pkg" framestats >> "$OUT"
wc -l "$OUT"
