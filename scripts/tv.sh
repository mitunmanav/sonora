# Sourced by the other scripts. Set SONORA_TV to another device's adb address.
export PATH="$PATH:$HOME/Android/Sdk/platform-tools"
SONORA_TV=${SONORA_TV:-192.168.68.104:5555}

tv() { adb -s "$SONORA_TV" "$@"; }

adb connect "$SONORA_TV" >/dev/null 2>&1 || true

# Sends key codes only while the expected app has the window. Another app can take
# the focus at any moment, and a key sent then lands in that app. Set APP to the
# package the keys are meant for, or they stop.
keys() {
  local gap=${GAP:-0.5} focus app=${APP:-com.mitun.sonora}
  for key in "$@"; do
    focus=$(tv shell dumpsys window | grep mCurrentFocus || true)
    if [[ $focus != *"$app/"* ]]; then
      sleep 1.5
      focus=$(tv shell dumpsys window | grep mCurrentFocus || true)
      if [[ $focus != *"$app/"* ]]; then
        echo "stopped, focus is elsewhere: $focus" >&2
        return 3
      fi
    fi
    tv shell input keyevent "$key"
    sleep "$gap"
  done
}

# Succeeds when the pixel at x,y on the screen is nearly white: a focused surface.
# Check this before sending OK, so a miscounted key cannot open somebody's app.
lit() {
  tv exec-out screencap -p | python3 -c "
import sys, io
from PIL import Image
r, g, b = Image.open(io.BytesIO(sys.stdin.buffer.read())).convert('RGB').getpixel(($1, $2))
sys.exit(0 if min(r, g, b) > 225 else 1)"
}
