# HANDOFF — Aerial: a 1:1 tvOS-style launcher for the Xiaomi MiTV

Copy everything below this line into the new session as the prompt. Nothing else is needed.

---

## PROMPT FOR THE NEW SESSION

You are continuing work on **Aerial**, an Android TV launcher that replicates the Apple tvOS home screen 1:1, is extremely light on system resources, has many features, and is fully customizable. It also ships a screen saver (aerial screensaver), because the previous session on this TV removed MiTV's own screen saver.

The project lives at `/home/mitun/aerial`. The target device is a **Xiaomi MiTV-AYFR0, Android 11 (API 30), 1920x1080 @ 320dpi (so 960x540dp)**, reachable over ADB at `192.168.68.108:5555` (it drops offline often — reconnect with `adb connect 192.168.68.108:5555`).

Work in **ponytail mode (full)**: smallest complete change, no speculative abstractions, comment only the "why", no features nobody asked for. Keep the whole app dependency-free (no AndroidX, no Compose, no networking).

### What already exists and works

A complete, building, installed app. **Do not rewrite it — continue from it.**

```
/home/mitun/aerial
├── settings.gradle, build.gradle (root, intentionally empty), gradle.properties, local.properties
├── app/build.gradle            # AGP 8.13.0, compileSdk 35, minSdk 23, targetSdk 35,
│                               # Java 17, junit:junit:4.13.2 (test only), NO runtime deps
├── app/proguard-rules.pro      # keeps HomeActivity + AerialDreamService
└── app/src
    ├── main/AndroidManifest.xml
    ├── main/res/values/{strings.xml,styles.xml}
    ├── main/res/drawable-nodpi/
    │   ├── aerial_ngorongoro.jpg  aerial_vigo.jpg  aerial_zhuravlyne.jpg
    │   ├── aerial_punjab.jpg      aerial_dulmen.jpg      (5 aerial stills, CC BY-SA 4.0)
    │   ├── banner.png  ic_launcher.png
    ├── main/java/com/mitun/aerial/
    │   ├── HomeActivity.java          # main screen, D-pad focus, app menu, settings, idle screensaver
    │   ├── AppRepository.java         # queries leanback+launcher intents, renders icons/banners
    │   ├── AppEntry.java              # model: label, package, component, icon bitmap, banner bitmap, accent
    │   ├── AppOrdering.java           # pure ordering logic (saved order + hidden set) — unit tested
    │   ├── Prefs.java                 # all user prefs
    │   ├── AerialScenes.java          # the 5 bundled scenes: res id + name + credit
    │   ├── dream/AerialDreamService.java   # system Daydream (screen saver) entry point
    │   └── ui/
    │       ├── Ui.java                 # dp/sp, glass drawable, bitmap decode, gear icon, immersive mode
    │       ├── HeroBackgroundView.java # full-bleed wallpaper + accent tint + scrim
    │       ├── AppIconView.java        # the 5:3 app card (banner art, or generated brand card), focus sheen
    │       ├── AppTileView.java        # focusable tile: scale-up focus, parallax sway, label modes
    │       ├── GlassPillView.java      # top-right clock + profile pill (opens Settings)
    │       ├── GlassDrawable.java      # cheap "Liquid Glass" fill/edge/highlight, no blur
    │       ├── SettingsOverlay.java    # tvOS-style settings list
    │       ├── ChoiceOverlay.java      # tvOS-style list picker
    │       └── AerialScreensaverView.java  # Ken Burns crossfade screensaver, 2 bitmaps max alive
    └── test/java/com/mitun/aerial/AppOrderingTest.java   # 3 tests, all passing
```

Build / test / install commands (gradle 8.13 + JDK 21 are already on this box):

```bash
cd /home/mitun/aerial
GRADLE=$(echo /home/mitun/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle)
$GRADLE --offline :app:assembleDebug
$GRADLE --offline :app:testDebugUnitTest      # currently 3 tests / 0 failures
adb -s 192.168.68.108:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.68.108:5555 shell am force-stop com.mitun.aerial
adb -s 192.168.68.108:5555 shell am start -n com.mitun.aerial/.HomeActivity
adb -s 192.168.68.108:5555 shell screencap -p /sdcard/x.png
adb -s 192.168.68.108:5555 pull /sdcard/x.png /tmp/opencode/x.png
```

State on the TV right now:
- `com.mitun.aerial` is installed (debug build, 3.2 MB APK, ~3000 lines of Java).
- The launcher runs and renders correctly (screenshots verified).
- The **system screen saver has been pointed at Aerial**:
  `settings put secure screensaver_components com.mitun.aerial/com.mitun.aerial.dream.AerialDreamService`
  and `settings put secure screensaver_enabled 1` (both already applied and confirmed).
  `settings put secure screensaver_activate_on_sleep 1` was also set.
- `com.mitv.dream` (MiTV's original screen saver) was **disabled in the previous session** — that is exactly why Aerial needs its own.

### The tvOS reference research (do not redo it)

I researched the real tvOS look exhaustively. Key findings, so the next session doesn't repeat the search:

- **tvOS 26 (current) home screen**: full-bleed "Top Shelf" artwork of the focused app with the app name in large light type at top-left; a **clock + profile avatar in a translucent pill at top-right**; a **row of wide 5:3 app icons in a rounded translucent glass bar** floating over the artwork near the bottom; further app rows below, scrollable. Verified against Apple's own press image (`tvos26-hero.jpg`, `tvos-home-screen.jpg`) and AppleInsider/MacRumors review screenshots.
- **tvOS icons are 5:3 landscape rectangles with ~18.5% corner radius**, not squares. App names are *not* shown under icons by default (tvOS 17+ shows the name only for the focused tile).
- **Focus effect**: focused icon scales up (~1.10x) and lifts with a stronger shadow; neighbouring icons lean away with a subtle 3D parallax/tilt; a specular sheen sweeps across the focused card. This is what `AppTileView` + `AppIconView` implement.
- **Apple HIG grid metrics** (developer.apple.com/design/human-interface-guidelines/layout): 6-column grid = 260pt content width, 40pt horizontal spacing, 100pt minimum vertical spacing; 7-column = 217pt.
- **tvOS app icon asset sizes**: home screen icon 400x240pt (800x480px @2x); App Store 1280x768; Top Shelf 1920x720 (wide: 2320x720).
- **Screensaver (Aerial)**: slow-moving aerial footage; swiping left/right on the remote changes location; tapping the touch surface reveals the location name. tvOS 27.2 added a "Shuffle" mode cycling Aerials/Memories/Portraits.
- Note: tvOS 26's Liquid Glass redesign only applies to 2nd-gen+ Apple TV 4K hardware.

Reference images already downloaded to `/tmp/opencode/tvos-research/` (may be wiped; re-fetch from `https://www.apple.com/newsroom/images/2025/09/new-versions-of-apples-software-platforms-are-available-today/article/Apple-OS-availability-tvOS-26-Home-Screen_big.jpg.large_2x.jpg` and `https://9to5mac.com/wp-content/uploads/sites/6/2025/06/tvos-home-screen.jpg` if needed).

### Visual assets

The 5 aerial stills are **real photographs from Wikimedia Commons, CC BY-SA 4.0**, resized to 1920x1080 JPEG (total ~3 MB). Credits are already stored in `AerialScenes.java` and shown in the About text and in the screen saver overlay:
- Ngorongoro, Tanzania — Tasha~visual
- Vigo di Fassa, Italy — Syrio
- Zhuravlyne Lake, Ukraine — Medoffer
- Punjab, India — Bhullargraphic
- Dülmen, Germany — Dietmar Rabich

If new imagery is added, keep the same attribution discipline (CC BY-SA / CC0 only, never Apple assets).

### What is still open (the actual next work)

1. **Verify the screen saver actually runs as a system dream.** `dumpsys dreams` still shows `mCurrentDreamName=null` and the ADB-triggered dream start fails with `Requires permission android.permission.BIND_DREAM_SERVICE` (expected — the shell cannot bind it). Test it the real way: put the TV to sleep (`adb shell input keyevent 26`) and screenshot, or use `adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME` then let it idle. The in-app idle screensaver (`HomeActivity.showScreensaver()` after the configured delay, default 300 s) is a separate path that *does* work and should also be verified.
2. **Make Aerial the default launcher** and confirm the TV boots/returns to it: `adb shell cmd package set-home-activity com.mitun.aerial/.HomeActivity` (then `cmd package clear-home-activity` to undo). Do this only after the UI is right.
3. **Fidelity pass against real tvOS screenshots.** Highest-value remaining deltas:
   - The top-left title block should sit where tvOS puts it and update with the focused app; consider moving the app name into the glass bar area.
   - Focused-tile label behaviour: tvOS shows the app name for the focused tile only; `Prefs.LABELS_FOCUSED` is the default.
   - Row spacing/insets vs the tvOS grid metrics (40pt horizontal, 100pt vertical).
   - The scrim gradient should keep the artwork readable but not crush it.
4. **Settings + app menu still need on-device QA**: open the clock pill → Settings overlay; every row (wallpaper, apps per row, labels, 12/24h clock, date, seconds, screen-saver delay/scene length/clock, hidden apps, reset order, reload apps, set as default launcher, about); the long-press app menu (Open / Move to front / Move to end / Hide from Home / App info).
5. **Watch memory/CPU.** Goal is "less to nothing" system resources. Two full-screen bitmaps alive in the screen saver, one in the hero. Avoid per-frame allocations in `onDraw` (the screen saver already draws at ~30 fps with a `postInvalidateDelayed` loop).
6. **Release build**: `minifyEnabled true` + `shrinkResources true` are already configured in `app/build.gradle`; verify the release APK builds and the ProGuard rules keep the dream service reachable, then install the release APK.

### Constraints and gotchas

- **No network permission, no AndroidX, no Compose** — that is what keeps it light. Do not add dependencies.
- `AppRepository` renders each app's `android:banner` (TV banner) into the wide 5:3 card when present; otherwise it generates a branded card from the icon + accent colour. This is why the icons on screen look like real tvOS app cards.
- `Ui.drawCenterCrop` uses a shared static `Paint` — if you add an alpha variant, restore the previous alpha (a bug where alpha leaked across draws and painted the blurred banner at full opacity over the wallpaper was already fixed once; don't reintroduce it).
- `HeroBackgroundView` deliberately does **not** paint the focused app's banner over the whole wallpaper (that is not what tvOS does — the artwork is the focused app's *content*, which a launcher doesn't have).
- `AppOrdering` is pure and unit-tested; keep ordering logic out of `HomeActivity` so the tests stay meaningful.
- The ADB connection drops frequently; reconnect before each device command.
- `local.properties` already points `sdk.dir` at `/home/mitun/Android/Sdk`.
- Gradle must run `--offline`; all needed AGP/Gradle artifacts are already in the local cache.

### Definition of done

Aerial is the TV's home screen: it boots into a pixel-close tvOS 26 home screen (glass pill clock top-right, glass app bar over full-bleed aerial artwork, 5:3 cards, focus scale+sheen+parallax), the D-pad navigates like tvOS, long-press gives per-app actions, Settings is fully customizable, and the system screen saver plays Aerial's aerials with a location overlay and swipe-to-change. It stays under a few MB, holds at most two screen-sized bitmaps, and adds no background processes.

---
