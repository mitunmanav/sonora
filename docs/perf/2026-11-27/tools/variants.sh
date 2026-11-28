#!/usr/bin/env bash
# Builds throwaway Sonora variants from the clean debaa31 copy in ~/sonora-build.
# Each variant changes one line of HomeActivity.applyGridScroll. Output: /mnt/c/.../Temp/var-*.apk
set -euo pipefail
cd ~/sonora-build
export ANDROID_HOME=$HOME/Android/Sdk
F=app/src/main/java/com/mitun/sonora/HomeActivity.java
cp "$F" /tmp/HomeActivity.orig
OUT=~/AppData/Local/Temp

apply() { # name old new
  cp /tmp/HomeActivity.orig "$F"
  python3 -I - "$F" "$2" "$3" <<'PY'
import sys
p, old, new = sys.argv[1:4]
s = open(p).read()
assert s.count(old) == 1, (old, s.count(old))
open(p, "w").write(s.replace(old, new))
PY
  ./gradlew :app:assemblePerf --console=plain -q 2>&1 | grep -E "error:|FAILED" || true
  cp app/build/outputs/apk/perf/app-perf.apk "$OUT/var-$1.apk"
  echo "built $1"
}

BACKDROP='gridBackdrop.setAlpha(open);'
SHELF='shelf.setAlpha(Math.max(0f, 1f - open * 2f));'
LENS='dockMaterial.setLens(Math.round((1f - open) * 16f) / 16f);'
HERO='hero.setVisibility(open >= 1f ? View.INVISIBLE : View.VISIBLE);'

apply nobackdropfade "$BACKDROP" 'gridBackdrop.setAlpha(open > 0f ? 1f : 0f);'
apply noshelffade    "$SHELF"    'shelf.setAlpha(open > 0f ? 0f : 1f);'
apply nolens         "$LENS"     '// lens repaint removed for the test'
apply herohideearly  "$HERO"     'hero.setVisibility(open > 0f ? View.INVISIBLE : View.VISIBLE);'

# all four together
cp /tmp/HomeActivity.orig "$F"
python3 -I - "$F" <<'PY'
import sys
p = sys.argv[1]
s = open(p).read()
for old, new in [
    ('gridBackdrop.setAlpha(open);', 'gridBackdrop.setAlpha(open > 0f ? 1f : 0f);'),
    ('shelf.setAlpha(Math.max(0f, 1f - open * 2f));', 'shelf.setAlpha(open > 0f ? 0f : 1f);'),
    ('dockMaterial.setLens(Math.round((1f - open) * 16f) / 16f);', '// lens repaint removed for the test'),
    ('hero.setVisibility(open >= 1f ? View.INVISIBLE : View.VISIBLE);', 'hero.setVisibility(open > 0f ? View.INVISIBLE : View.VISIBLE);'),
]:
    assert s.count(old) == 1, old
    s = s.replace(old, new)
open(p, "w").write(s)
PY
./gradlew :app:assemblePerf --console=plain -q 2>&1 | grep -E "error:|FAILED" || true
cp app/build/outputs/apk/perf/app-perf.apk "$OUT/var-all.apk"
echo "built all"
cp /tmp/HomeActivity.orig "$F"
