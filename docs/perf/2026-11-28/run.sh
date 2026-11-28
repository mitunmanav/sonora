#!/usr/bin/env bash
set -euo pipefail

component=${1:?Pass the launcher Home component}
out=$(realpath -m "${2:?Pass an output directory}")
pkg=${component%/*}
activity=${component#*/}
[[ $activity != .* ]] || activity=$pkg$activity
cd "$(dirname "$0")/../../.."
source scripts/tv.sh
mkdir -p "$out"

mixed=(22 22 22 22 20 22 22 21 21 19 21 21)
horizontal=(22 21 22 21 22 21 22 21 22 21 22 21)
vertical=(20 19 20 19 20 19 20 19 20 19 20 19)
trials=${TRIALS:-3}
read -r -a workloads <<< "${WORKLOADS:-mixed horizontal vertical}"
[[ $trials =~ ^[1-9][0-9]*$ ]]
for workload in "${workloads[@]}"; do
  case $workload in
    mixed|horizontal|vertical) ;;
    *) echo "Unknown workload: $workload" >&2; exit 2 ;;
  esac
done

check_home() {
  local focus
  focus=$(tv shell dumpsys window | grep 'mCurrentFocus=' || true)
  if [[ $focus != *"$pkg/$activity}"* && $focus != *"$component}"* ]]; then
    echo "Expected $component, found $focus" >&2
    return 1
  fi
}

for ((trial = 1; trial <= trials; trial++)); do
  for workload in "${workloads[@]}"; do
    prefix="$out/$trial-$workload"
    declare -n sequence=$workload
    echo "Running $pkg trial $trial, $workload"
    tv shell am force-stop "$pkg"
    tv shell am start -n "$component" > "$prefix.start.txt" 2>&1
    sleep 6
    check_home
    if [[ $workload == mixed ]]; then
      tv shell dumpsys meminfo "$pkg" > "$prefix.idle.txt"
    fi
    tv shell dumpsys gfxinfo "$pkg" reset > /dev/null
    APP=$pkg GAP=0.45 keys "${sequence[@]}"
    check_home
    tv shell dumpsys gfxinfo "$pkg" framestats > "$prefix.frames.txt"
    if [[ $workload == mixed ]]; then
      tv shell dumpsys meminfo "$pkg" > "$prefix.after.txt"
    fi
    awk '/Total frames rendered:|Number Frame deadline missed:|99th percentile:/ && !seen[$0]++' "$prefix.frames.txt"
  done
done
