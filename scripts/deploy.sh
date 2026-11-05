#!/usr/bin/env bash
# Builds, tests, installs and relaunches on the TV.
#   scripts/deploy.sh                the launcher
#   scripts/deploy.sh files          the Files app
#   scripts/deploy.sh all            both
# BUILD=debug uses the debug build. The default "perf" build is the release build
# signed with the debug key: measure this one, a debug build runs uncompiled.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/tv.sh

target=${1:-app}
build=${BUILD:-perf}
Build=${build^}
tasks=()
[[ $target == app || $target == all ]] && tasks+=(:app:testDebugUnitTest ":app:assemble$Build")
[[ $target == files || $target == all ]] && tasks+=(:files-core:testDebugUnitTest ":files:assemble$Build")

./gradlew --offline -q "${tasks[@]}" 2>&1 | grep -E "error:|FAILED|BUILD |tests completed|warning: \[" | head -30 || true

install() { tv install -r -t "$1/build/outputs/apk/$build/$1-$build.apk" 2>&1 | tail -1; }
[[ $target == files || $target == all ]] && install files
if [[ $target == app || $target == all ]]; then
  install app
  tv shell cmd package compile -m speed -f com.mitun.sonora >/dev/null
  tv shell am force-stop com.mitun.sonora
  tv shell am start -n com.mitun.sonora/.HomeActivity >/dev/null
fi
