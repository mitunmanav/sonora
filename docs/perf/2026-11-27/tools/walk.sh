#!/usr/bin/env bash
# D-pad walk for the Sonora home screen. Sends keys only while Sonora has focus. No OK.
T=${SONORA_TV:?Set SONORA_TV to the target device ADB address}
APP=${APP:-com.mitun.sonora}
GAP=${GAP:-0.45}
tv() { adb -s "$T" "$@"; }
keys() {
  for key in "$@"; do
    f=$(tv shell dumpsys window | grep mCurrentFocus)
    if [[ $f != *"$APP/"* ]]; then
      sleep 1.5
      f=$(tv shell dumpsys window | grep mCurrentFocus)
      if [[ $f != *"$APP/"* ]]; then echo "stopped, focus is elsewhere: $f" >&2; return 3; fi
    fi
    tv shell input keyevent "$key"
    sleep "$GAP"
  done
}
pkg=${PKG:-com.mitun.sonora}
ACT=${ACT:-.HomeActivity}
APP=$pkg
tv shell am force-stop $pkg
tv shell am start -n $pkg/$ACT >/dev/null
sleep 6
tv shell dumpsys gfxinfo $pkg reset >/dev/null
keys 22 22 22 22 20 22 22 21 21 19 21 21
echo "== frames"
tv shell dumpsys gfxinfo $pkg | grep -E "Total frames|Janky|percentile|Frame deadline|Slow UI|Slow issue|Slow bitmap"
echo "== memory"
tv shell dumpsys meminfo $pkg | grep -E "Java Heap:|Native Heap:|Graphics:|Code:|TOTAL PSS:|Views:|Activities:"
