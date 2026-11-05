#!/usr/bin/env bash
# Measures the home screen: memory when fresh, then frames over one fixed walk
# across the grid at remote speed. The numbers to compare are "deadline missed" and
# the percentiles. The "janky" figure counts input latency from `adb shell input`,
# so it reads high whatever the app does.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/tv.sh
pkg=${1:-com.mitun.sonora}

tv shell am force-stop "$pkg"
tv shell am start -n "$pkg/.HomeActivity" >/dev/null
sleep 6
tv shell dumpsys gfxinfo "$pkg" reset >/dev/null
GAP=0.45 keys 22 22 22 22 20 22 22 21 21 19 21 21

echo "== frames"
tv shell dumpsys gfxinfo "$pkg" | grep -E "Total frames|Janky|percentile|Frame deadline|Slow UI|Slow issue|Slow bitmap"
tv shell dumpsys gfxinfo "$pkg" | grep -E "Total GPU memory|Scratch|RenderTarget|Image:|Texture:" | head -8
echo "== memory"
tv shell dumpsys meminfo "$pkg" | grep -E "Java Heap:|Native Heap:|Graphics:|Code:|TOTAL PSS:|Views:|Activities:"
