> **This is the older handoff (last touched before 2026-11-04). The current, exhaustive tvOS 1:1 handoff is [`HANDOFF-2026-11-04.md`](HANDOFF-2026-11-04.md). Read that one first.**

# HANDOFF — Sonora

Everything needed to start cold. Copy the block below the line into the new
session; nothing else is needed.

---

## PROMPT FOR THE NEW SESSION

You are continuing work on **Sonora**, an Android TV launcher that replicates
the Apple tvOS 26 home screen, is extremely light on system resources, and is
fully customisable. It ships a screen saver (Daydream) because the previous
session on this TV removed MiTV's own screen saver.

The project lives at `/home/mitun/aerial`. **Git repo, working tree clean, 19
commits** — use git, it exists so large refactors are safe.

Work in **ponytail mode (full)**: smallest complete change, no speculative
abstractions, comment only the "why", no features nobody asked for. Keep the
app **dependency-free** (no AndroidX, no Compose, no networking libs).

### THE GOAL

Sonora is a **launcher + TV utility suite** in **one APK**:

- The **launcher is the main product**. It must stay the best thing in the app.
- **Optional, off by default** extras, bundled but switchable per feature:
  a **browser with an ad blocker**, a **file manager**, and **file transfer**.
- Plus: an **app manager** (disable / uninstall / manage every installed app),
  a **guided step-by-step live debugging guide**, and a **debug panel**.

The headline requirement is **"extremely light, little to no usage of system
resources"**:

- **No background services.** No WorkManager, no JobScheduler, no alarms, no
  boot receiver, no foreground service. A launcher that idles at 0% CPU *is*
  the product.
- The only Service is the Daydream `DreamService`, which exists solely while
  the system is showing the dream.
- The **LAN file server runs on a thread inside the visible Share activity and
  dies with it** — no listening socket when the app is closed.
- `INTERNET` permission is declared but **no code touches the network** unless
  the browser is explicitly enabled.
- Target: **< 6 MB APK** (currently **3.27 MB**), at most two screen-sized
  bitmaps alive.

### USER DECISIONS ALREADY MADE (do not re-litigate)

| Question | Answer |
|---|---|
| App name | **Sonora** (renamed from "Aerial" — package is now `com.mitun.sonora`) |
| Browser packaging | **One APK, browser off by default** (hidden until enabled in Settings) |
| File transfer | **Full-fledged file manager + file transfer**, bundled and optional like the browser |

### THE USER'S EXACT FEATURE REQUESTS (verbatim)

> "To enable debugging, we need a guided, step-by-step live guide that is
> helpful. Once enabled, our app should be able to uninstall, save, and perform
> other actions through a built-in file manager and file-share feature that is
> fast, easy to connect, and lightweight. The file manager should be advanced
> yet simple to use. We also need to rename the app and add an optional
> browser with an ad blocker; the browser should be lightweight as well."

> "include a lot of features like deep loader, debugging, removing, and
> disabling things. Once a person opens this APK on any Android TV, it's going
> to set it up."

### THE APPLE-DESIGN SKILL IS INSTALLED AND IS THE DESIGN AUTHORITY

The user asked for Apple's design principles to be applied to **every** screen,
the launcher included. The skill is installed at:

```
/home/mitun/.config/opencode/skills/apple-design/SKILL.md
```

(23,036 bytes, fetched from
`https://github.com/emilkowalski/skills/tree/main/skills/apple-design`. Load it
with the `skill` tool, id `apple-design`.)

**Read it before any UI work.** It is written for the web, so translate it to
platform views, but the principles are not negotiable:

- **Springs, not tweens.** Interruptible, re-targetable, velocity-carrying.
- **Response on the press, not on release.**
- **Spatial consistency:** a screen that comes in from the right leaves to the
  right. Enter and exit along the same path.
- **Reduced motion** is a gentler equivalent, never no feedback. The system
  `ANIMATOR_DURATION_SCALE` is the honest signal.
- **Typography:** tracking and leading follow the size. Large text is tracked
  tighter, small text slightly open. Never one global value.
- **Materials:** translucent layers, heavier material for bigger surfaces,
  never stack a light translucent surface on another. Dim to focus.
- **Restraint.** Every element earns its place.

**Android translation used in this codebase** (`ui/Motion.java`,
`ui/Spring.java`, `ui/Type.java`):

- Android has **no platform spring animation** — `SpringAnimation` is
  **AndroidX**, and this app has no dependencies. So `ui/Spring.java` solves
  the damped harmonic oscillator itself on a `Choreographer`, with a fixed
  1/240 s integration step. It keeps the value *and the velocity* when
  re-targeted, which is what makes it interruptible. **Do not replace it with
  a `ViewPropertyAnimator` tween.**
- Parameters are Apple's: damping ratio 1.0 = no overshoot (the default),
  below 1.0 = settle. "Response" is seconds-to-target, not a duration.
  `Motion.DAMPING_CRITICAL`, `Motion.DAMPING_SPRINGY`, `Motion.RESPONSE_MOVE`,
  `Motion.RESPONSE_PANEL`, `Motion.RESPONSE_FOCUS`.
- `ui/Type.java` is the type scale: `DISPLAY, TITLE, HEADING, BODY, CAPTION,
  MICRO`, each with its own size, weight, tracking and leading. **Use it
  instead of ad-hoc `setTextSize` calls.**

### CURRENT STATE — what exists and works

A complete, building app, **verified on the physical TV**. Do not rewrite it.

```
/home/mitun/aerial
├── PLAN.md                     # the plan; keep it updated
├── HANDOFF.md                  # this file
├── .agents/skills/             # official Google Android skills
├── settings.gradle             # rootProject.name = "Sonora"
├── build.gradle                # root, intentionally empty
├── gradle.properties           # useAndroidX=false, optimizedResourceShrinking=true
├── local.properties            # sdk.dir=/home/mitun/Android/Sdk
├── app/build.gradle            # AGP 8.13.0, compileSdk 35, minSdk 23, targetSdk 35,
│                               # Java 17, junit:junit:4.13.2 (test only), NO runtime deps
├── app/proguard-rules.pro      # keeps HomeActivity, dream, tools.**, browser.**
└── app/src
    ├── main/AndroidManifest.xml
    ├── main/res/values/{strings.xml,styles.xml}
    ├── main/res/raw/adlist.txt # the browser's bundled ad list (hosts-style)
    ├── main/res/drawable-nodpi/
    │   ├── sonora_ngorongoro.jpg  sonora_vigo.jpg  sonora_zhuravlyne.jpg
    │   ├── sonora_punjab.jpg      sonora_dulmen.jpg   (5 aerials, CC BY-SA 4.0)
    │   ├── banner.png  ic_launcher.png
    ├── main/java/com/mitun/sonora/
    │   ├── HomeActivity.java      # grid, D-pad, app menu, idle screensaver
    │   ├── Grid.java              # PURE: runtime-sized grid maths       [tested]
    │   ├── AppOrdering.java       # PURE: order + hidden set            [tested]
    │   ├── BackPolicy.java        # PURE: what BACK means on the grid   [tested]
    │   ├── AppEntry.java          # model: label, pkg, component, icon, banner, accent
    │   ├── AppRepository.java     # launchable apps + artwork, cached, off the main thread
    │   ├── AppManager.java        # ALL installed packages, cached 30s
    │   ├── PackageActions.java    # enable/disable/uninstall/clear/force-stop, guarded
    │   ├── SystemInfo.java        # cached device facts + byte formatting
    │   ├── Prefs.java             # every user choice, one file
    │   ├── Scenes.java            # the 5 bundled scenes: res id + name + credit
    │   ├── SonoraProvider.java    # content URIs for local files (for share / open with)
    │   ├── dream/SonoraDreamService.java  # system Daydream entry point
    │   ├── tools/
    │   │   ├── ToolActivity.java  # the host for every panel; one ScreenStack
    │   │   ├── SettingsPanel.java # the Settings screen
    │   │   ├── ToolsPanel.java    # the Tools menu + the on/off flow for the extras
    │   │   ├── AppPanel.java      # app manager: all packages, filters, lazy icons
    │   │   ├── DebugPanel.java    # device, processes, battery, network, log, self check
    │   │   ├── SetupGuide.java    # the guided live debugging guide
    │   │   ├── FilePanel.java     # file manager: browse, sort, search, copy/move/rename/delete
    │   │   ├── FileServer.java    # the LAN HTTP server; runs on a thread, dies with its screen
    │   │   └── ShareActivity.java # the visible screen that runs the server
    │   ├── browser/
    │   │   ├── BrowserActivity.java  # WebView, off by default
    │   │   └── AdBlock.java          # local hosts-style blocker, no network round trip
    │   └── ui/
    │       ├── Ui.java              # dp/sp, glass drawable, bitmap decode, gear, immersive
    │       ├── Motion.java          # the spring API + reduced-motion handling
    │       ├── Spring.java          # the spring solver (no AndroidX available)
    │       ├── Type.java            # the type scale
    │       ├── HeroBackgroundView.java  # full-bleed wallpaper + accent tint + scrim
    │       ├── AppIconView.java     # the 5:3 app card, focus sheen
    │       ├── AppTileView.java     # focusable tile: scale-up focus, parallax sway
    │       ├── GlassPillView.java   # top-right clock + profile pill (opens Settings)
    │       ├── GlassIconPill.java   # a round glass button: search, control centre, mic
    │       ├── GlassDrawable.java   # cheap "Liquid Glass" fill/edge/highlight, no blur
    │       ├── SearchOverlay.java   # the tvOS-style search screen: field + result rail
    │       ├── SettingRow.java      # one list row, six kinds
    │       ├── ScreenStack.java     # push/pop list screens, shared by every panel
    │       ├── ChoiceOverlay.java   # tvOS-style list picker
    │       └── ScreensaverView.java # Ken Burns crossfade screensaver, 2 bitmaps max
    └── test/java/com/mitun/sonora/
        ├── AppOrderingTest.java   # 8 tests
        ├── AppSearchTest.java     # 10 tests
        ├── BackPolicyTest.java    # 4 tests
        ├── GridTest.java          # 10 tests
        ├── SpringTest.java        # 9 tests
        └── tools/FileServerTest.java # 7 tests: the request the LAN server must survive
```

**Build / test commands** (gradle 8.13 + JDK 21 are on this box):

```bash
cd /home/mitun/aerial
GRADLE=$(echo /home/mitun/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle)
$GRADLE --offline :app:assembleDebug
$GRADLE --offline :app:testDebugUnitTest      # 48 tests, all passing
$GRADLE --offline :app:assembleRelease        # minified + shrunk, 3.27 MB
```

Gradle **must** run `--offline`; all AGP/Gradle artifacts are cached locally.

### THE PHYSICAL TV — how to test on the device

The user asked to try it on the TV, so **device verification is wanted**. The
device is a **Xiaomi MiTV-AYFR0, Android 11 (API 30), 1920×1080 @ 320dpi =
960×540dp**, which is the 6-column case.

```bash
export PATH=$PATH:/home/mitun/Android/Sdk/platform-tools
adb connect 192.168.68.105:5555     # may refuse for a while; retry every ~6s
adb install -r -t /home/mitun/aerial/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.mitun.sonora/.HomeActivity
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png /tmp/opencode/s.png
```

- **The adb connection drops** (the TV's wireless adb listener closes). Retry
  in a loop; it comes back within a minute.
- **The user is often using the remote at the same time.** Their key presses
  interleave with yours and will confuse a key-sequence test. Check
  `adb shell dumpsys window | grep mCurrentFocus` before and after, and prefer
  verifying with the view tree over guessing:
  ```bash
  adb shell dumpsys activity top | grep -oE "AppTileView\{[^}]*\}" | grep '\.F'
  ```
- **`ToolActivity`, `ShareActivity`, `BrowserActivity` are `exported=false`**,
  so `adb shell am start` on them is refused. Reach them through the UI:
  Settings tile on the home screen → OK.
- **`com.mitun.aerial` (the old package) is no longer installed** — only
  `com.mitun.sonora` is on the TV.
- **Sonora IS the home app now** (2026-10-31). `cmd package set-home-activity`
  and the HOME role both point at it, but Google TV's launcher declares its
  HOME filter with `priority=2` and wins the resolver anyway; the shell may
  not disable just its component (`SecurityException`), so the package was
  disabled: `pm disable-user --user 0 com.google.android.tvlauncher`.
  Undo with `pm enable com.google.android.tvlauncher`.

### WHAT THE LAST SESSION FOUND AND FIXED (all verified on the TV)

These were **live bugs**, not theories. Do not reintroduce them.

1. **`dispatchKeyEvent` swallowed every key while an overlay was open.** It
   returned `true` for all keys, so the focused row never saw the event:
   Settings, the app menu and every picker were dead — no focus movement, no
   OK, no click.
2. **Arrow keys escaped an overlay into the grid behind it**, where OK would
   launch an app the user never picked. Fixed with `Ui.moveListFocus`, which
   moves focus explicitly inside the list instead of letting the framework
   search for it.
3. **`Ui.moveListFocus` asked `list.findFocus()` whether the list had focus.**
   `findFocus()` asks the *parent* whether *it* is focused, so it always
   answered no, and DOWN always jumped back to the first row. It now uses
   `list.getFocusedChild()`.
4. **`ScreenStack.focusTop()` focused child 0**, which is a heading and
   cannot take focus, so no row was focused at all. It now finds the first
   *focusable* child, and a new screen starts scrolled to the top.
5. **`AppTileView.updateFocus()` called `bringToFront()`** on the focused
   tile. `bringToFront()` **moves a view to the end of its row**, so the tiles
   were re-ordered under the user while the D-pad links, built from the
   original order, still pointed at the old neighbours. The tile now raises
   itself with `setZ()` only.
6. **The framework's spatial focus search overrode the grid's links** and
   jumped to whatever happened to be nearest on screen. The D-pad is now
   driven by `HomeActivity.moveFocus()`: along the row, then between the rows,
   with the clock pill above the first row. `wireFocus()` was deleted.
7. **The first tile did not get the focus at startup** — the grid is not laid
   out when it is built, so `requestFocus()` failed and the framework picked
   one. It is now requested after the layout, and again on window focus.
8. **A label in "focused only" mode stayed visible until the tile had been
   focused once.** Unfocused labels now start hidden.
9. **Screens in the stack were transparent**, so the screen being replaced
   showed through the one sliding in. Each screen is now opaque.
10. **The app menu was drawn over the app title** and the grid showed through
    it. The scrim is heavier and the menu starts below the title.
11. **BACK on the grid fell through to the system** and exited the launcher.
    Now: overlay → screensaver → scroll the grid to the top and focus the
    first tile → and only leave if Sonora is *not* the default launcher.

Also fixed earlier, and still true: the app list and its artwork are cached
(`AppRepository`), artwork is stored at the size the tile actually draws at
and the icon bitmap is dropped when the app has a banner it never uses, the
wallpaper is decoded once instead of on every resume, `onTrimMemory` releases
it, and a device with no launchable apps gets a real empty state.

### THE EDGE CASES — walked through the code on 2026-10-31

The §4 checklist of `PLAN.md` was walked item by item and everything that
was broken was fixed. The full write-up is `PLAN.md` §10. **Do not
reintroduce these:**

1. **The LAN file server could be killed from the network.** A request line
   of nothing but spaces split into no parts and `parts[0]` took the app
   down; a broken `%` escape made `URLDecoder` throw, uncatchably; the
   request line had no length cap; every folder link in the index was dead;
   the client threads were not daemons. The parsing is now three pure
   methods with `FileServerTest` over them.
2. **The screen saver decoded a broken scene 30 times a second, for ever**,
   because the scene clock was never set. `OutOfMemoryError` was not caught
   on that path. And **"previous scene" went forwards**: `previousScene()`
   called the same code as `nextScene()`.
3. **A TV with no web engine took the app down.** `new WebView(this)` is the
   only probe there is — the class is framework code — so it is made inside
   a try, and the browser says the TV has no browser.
4. **The app manager could crash on a package with no label** (`loadLabel()`
   returns null) or no `applicationInfo`. Both were dereferenced on the
   thread that loads the list.
5. **One package the system would not answer for emptied the whole app
   grid**: two `PackageManager` calls sat outside every try. The same was
   true in `setHidden()` and in the app manager.
6. **Hiding or uninstalling an app left it on the home screen**, because
   the cache was kept and the epoch never moved. Both now drop the cache and
   move the epoch.
7. **An uninstall was never reported.** `REQUEST_DELETE_PACKAGES` was not
   declared, so the system refused while Sonora said "Confirm to
   uninstall". The permission is declared and the result is read back on
   resume.
8. **With no apps at all there was no way into Settings**: the empty state
   replaced the grid, tile included.
9. **Popping a screen lost the focus** of the screen underneath, so UP did
   nothing.
10. **The setup guide replaced the whole stack**, so BACK from it left the
    tools activity instead of going back.
11. The picker opened with nothing ticked and nothing focused when the
    value was not on the list; the wallpaper picker replaced a photo with
    the first scene; the process list rebuilt the screen underneath it; a
    file was opened as `*/*`; the app's own folder was `/data/sonora`, which
    the app cannot write to; the long-press menu opened apps silently when
    the launch failed; the ad blocker cut the port before the userinfo; the
    address bar had no "Go"; a page could read the TV's files on Android 6
    to 9; a spring with a target that is not a number never rested; a
    re-target threw away a waiting callback; the margins did not mirror in
    RTL and the chevron pointed the wrong way; the dream showed the place
    from the first frame in "on input" mode; the favourites and hidden-apps
    screens read the app list on the main thread.


### THE GRID — why it matters, do not undo it

The old code hardcoded 6 columns and 44dp padding. That is wrong on most TVs:
Google's density table maps 720p/1080p/4K to 960×540dp, **but many Chinese
boxes report 240dpi, which is 1280×720dp**. Hardcoding breaks on exactly the
hardware this app targets.

`Grid` derives everything from the runtime width, using proportions **measured
off Apple's own tvOS 26 press renders**:

| Quantity | Value | Source |
|---|---|---|
| Icon width | 14.7% of screen width | measured on tvOS 26 hero image |
| Screen margin | 2.8% each side | measured |
| Gutter | 1.2% of screen width | measured |
| Icon aspect | 5:3 landscape | Apple asset spec (400×240pt) |
| Content width | 844dp of a 960dp canvas | Google's TV layout guide |

Because the proportions are scale-invariant, **every 16:9 screen lands on the
same column count** — which is exactly what tvOS does. Below 640dp wide,
columns are *dropped* rather than icons shrunk (`MIN_TILE_WIDTH_DP = 92`).
`GridTest` has 8 tests covering all of this, including the scale-invariance
invariant. **Keep ordering/geometry logic out of Activities so tests stay
meaningful.**

### HARD CONSTRAINTS (non-negotiable)

- **No network permission in practice.** `INTERNET` is declared, but no code
  path uses it unless the browser is switched on.
- **No AndroidX, no Compose, no third-party libraries of any kind.** Platform
  APIs only. This is why the spring had to be written by hand.
- **No background processes.** No Service except the DreamService. No receivers.
- `minSdk 23` — Play requires TV apps to keep minSdk ≤ 31. **Do not raise it.**
- `QUERY_ALL_PACKAGES` is declared (needed for the app manager).
- `android.software.leanback` and `android.hardware.touchscreen` are both
  `required="false"` — **never** make them required.
- The Play TV checklist wants a **320×180 banner** (exists) and an icon
  **≥160×160 at xhdpi** (exists, 512×512).
- `usesCleartextTraffic="false"` — do not remove it.

### RESEARCH FINDINGS (do not redo — these were expensive)

**tvOS reference** (already measured, images may be wiped; re-fetch from
`https://www.apple.com/newsroom/images/2025/09/new-versions-of-apples-software-platforms-are-available-today/article/Apple-OS-availability-tvOS-26-Home-Screen_big.jpg.large_2x.jpg`):

- tvOS 26 home screen = full-bleed "Top Shelf" artwork of the focused app with
  the app name in large light type top-left; a **clock + profile avatar in a
  translucent pill at top-right**; a **row of wide 5:3 app icons in a rounded
  translucent glass bar** floating over the artwork near the bottom; further
  rows below, scrollable.
- App names are **not** shown under icons by default — only the focused tile
  shows its name (tvOS 17+). `Prefs.LABELS_FOCUSED` is the default. ✔
- Focus effect: focused icon scales ~1.10× and lifts with a stronger shadow;
  neighbours lean away with subtle 3D parallax/tilt; a specular sheen sweeps
  the focused card. ✔ in `AppTileView` + `AppIconView`.
- Apple's HIG grid: 6-column = 260pt content width, 40pt horizontal, 100pt vertical.
- tvOS app icon assets: home 400×240pt; App Store 1280×768; Top Shelf 1920×720.
- The 5 bundled aerials are **real CC BY-SA 4.0 photographs from Wikimedia
  Commons** (Ngorongoro/Tasha~visual, Vigo di Fassa/Syrio, Zhuravlyne/Medoffer,
  Punjab/Bhullargraphic, Dülmen/Dietmar Rabich). Credits live in `Scenes.java`
  and are shown in About and the screen-saver overlay. **Never use Apple assets.**
  If new imagery is added, keep the same attribution discipline.

**Android TV platform rules** (from developer.android.com, verified):

- **Never re-query `PackageManager` in `onResume`.** TVs kill background
  processes and "transition to the Launcher can be significantly delayed".
  **Cache the app list and its icons.** ✔ `AppRepository` does this.
- **`ActivityManager.isLowRamDevice()`** is the official way to detect a 1GB
  stick — never infer from RAM. It now halves the artwork size.
- **BACK is breadcrumb-style only.** Never a toggle, never a confirmation
  dialog, no on-screen back button. Consecutive presses must always reach the
  TV home screen.
- **Dream picker is hidden on Google TV and Philips-style sets.** Ship a
  user-launched screensaver *activity* as the primary path (the in-app idle
  saver) plus a deep link to `Settings.ACTION_DREAM_SETTINGS`.
- **A normal app CAN hide bloatware** via
  `PackageManager.setComponentEnabledSetting(..., COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP)`
  — `CHANGE_COMPONENT_ENABLED_STATE` is a **normal** permission. ✔ `PackageActions`.
- **Uninstalling a preinstalled app is per-user removal**, not deletion from
  `/system`. The UI says this plainly.
- **`clearApplicationUserData` for another app is NOT possible** from a normal
  app. `PackageActions.clearData` opens the system app-info page and tells the
  user to use "Clear storage". **Do not try to work around this.**
- `Settings.Secure.screensaver_components` is a **Secure** setting — a normal
  app cannot write it.
- Google TV enters **Ambient Mode after 10 min inactivity**.
- Play TV quality rules: minSdk ≤ 31; 320×180 banner; no touchscreen requirement.

**Device class reality** (so the app works on cheap sticks too):

- Chipset/OS tiers: Android 6/7 (S905, RK3228) · Android 9/11 (S905X2/X3,
  S922X, RK3318, RK3566) · Android 12/13/14 (MTK9456, RTD286x).
- **dp viewports**: 720p@240dpi = 853×480dp · 1080p@320dpi = 960×540dp ·
  1080p@240dpi = 1280×720dp · 4K@480dpi = 1280×720dp. **Read at runtime.**
- 1GB sticks are `isLowRamDevice() == true` — the common case.
- Samsung/Tizen is **not** Android TV, out of scope.

### OFFICIAL ANDROID SKILLS INSTALLED

Google's official `android/skills` repo is at `.agents/skills/`:

| Skill | Use for |
|---|---|
| `r8-analyzer` | release APK size, ProGuard keep-rule analysis. **It needs R8 ≥ 9.4.34 to produce the report and this box is offline — apply its `references/CONFIGURATION.md` checklist by hand.** Already applied: `minifyEnabled`, `shrinkResources`, `proguard-android-optimize.txt`, `android.r8.optimizedResourceShrinking=true`. Verify `android.enableR8.fullMode=false` is absent (it is). |
| `android-profiler` | CPU/memory/heap profiling, allocation churn, LMK analysis. |
| `anr-audit-and-fix` | ANR hunting — relevant since this app must never block the main thread. |
| `android-permissions-security` | auditing the manifest permissions and IPC components. |
| `android-intent-security` | auditing intent handling, `exported` components, intent redirection. |

Re-install with `android skills add --all --project=.`. The CLI is at
`/home/mitun/.local/bin/android`.

### GOTCHAS / TRAPS (learned the hard way)

- **`Ui.drawCenterCrop` uses a shared static `Paint`.** If you add an alpha
  variant you must restore the previous alpha — a bug where alpha leaked across
  draws was already fixed once here. Do not reintroduce it.
- **AndroidX's `SpringAnimation` does not exist in the platform SDK.** The
  spring is hand-written in `ui/Spring.java`. Do not reach for a tween.
- **`View.findFocus()` on a container is not "the focused child".** Use
  `getFocusedChild()`.
- **`bringToFront()` reorders children.** It is not a z-order operation; use
  `setZ()`.
- **`requestFocus()` before the first layout silently fails.** Post it, or ask
  for it on window focus.
- **`hero.setEntry` deliberately does not paint the focused app's banner** over
  the wallpaper (tvOS shows the app's *content* there, which a launcher does
  not have).
- `AppRepository` renders each app's `android:banner` (TV banner) into the wide
  5:3 card when present, else generates a branded card from icon + accent.
- `AppOrdering` and `BackPolicy` are pure and unit-tested — keep ordering and
  BACK logic out of Activities.
- `ScreenStack.refresh()` takes the new title, subtitle and rows and rebuilds
  the top screen in place, keeping its position in the stack.
- `SettingRow.DANGER` draws its label in the warning colour — use it for
  destructive rows. It is a real constant, not decorative.
- `Prefs.columns()` returns **0 = Auto**; `Grid.resolve(widthDp, stored)`
  handles the fallback. The settings row shows "Auto (N)".
- `FilePanel` writes only where the platform allows: on Android 11 the shared
  storage can be read but not written; the app's own folder
  (`getExternalFilesDir`) is always writable. **Say so in the UI rather than
  failing silently** — that is a hard project rule.

### NEXT WORK, IN PRIORITY ORDER

**P0 — The search screen (done, needs the TV).** The search was rebuilt the
tvOS way: a round glass search button in the top row, the field inline at the
top with 30 sp text, our own magnifier and microphone marks, the result cards
built **once** and re-bound instead of rebuilt per keystroke, and an
`ACTION_ASSIST` mic that falls back to the keyboard when the TV has no
assistant. `PLAN.md` §9 has the whole plan and the references. **Not yet
verified on the device** — check the keyboard comes up, that the results rail
takes the D-pad, and that the mic either opens the assistant or falls back.

**P1 — The strict list, in order.** Every item is evidence-backed (see
`GOOGLE-TV-TEARDOWN.md` §8-§10); do them in this order:
1. **Working uninstall** — done in part: `REQUEST_DELETE_PACKAGES` is
   declared, the system uninstaller is asked, and the result is read back on
   resume ("Uninstalled X" or "X was not uninstalled"). Still to do:
   `PackageInstaller.uninstall(pkg, callback)` for a result the app is told
   about directly, and **"Uninstall" in the long-press menu**.
2. **The long-press menu order** — Google TV's verified order is
   **Open · Move · Favourites · Info · Uninstall**. Sonora has Open,
   Favourites, Search apps, Edit Home Screen, Hide from Home, App info. Cut
   "Search apps" (redundant now), merge "Edit Home Screen" into "Move"
   (which starts arrange mode on that app), and add "Uninstall".
3. **The D-pad dead-end** — `HomeActivity.moveFocus` returns `true` even when
   it found no target, so LEFT at column 0 and RIGHT at the last column do
   nothing at all. Google's grid scrolls the row instead; the smallest fix
   that removes the dead end is to wrap to the next row.
4. **`stateNotNeeded="true"`** on `HomeActivity` — Google has it on
   `MainActivity`; one manifest line.
5. **Marquee for long app names** — Google uses `ellipsize="marquee"`.
6. **Errors that fix the problem** — Google's error screen has a *primary
   action that fixes it*; Sonora says "Could not open X" in a toast.
7. **Empty states say something** — "No apps match …", not a blank rail.
8. **The edge fade** — Google's exact recipe: `saveLayer` the strip, draw,
   then a `LinearGradient` with `PorterDuff.Mode.DST_IN`. ~40 lines, RTL-aware.
9. **A directional move menu** — Google has Move up/down/left/right, each
   disabled with a reason ("Can't move"). A visible-but-disabled row teaches
   the rule; a missing row teaches nothing.
10. **A drawn arrow for edit mode** — `OverlayTutorialArrow` points at the
    control to press. "Edit Home Screen" has still never been verified on the
    device.

**P2 — Finish the design pass with the apple-design skill.** The skill was
installed late, so only the foundations are in place. Do the rest:
- Apply `Motion` (springs) to the launcher: the focus scale and the parallax
  sway are still fixed-duration tweens with an `OvershootInterpolator`. Make
  them springs so an interrupted focus change continues instead of restarting.
- Apply `Type` to the remaining screens: the home shelf, the clock pill, the
  app tile labels, the screensaver overlay. `SettingRow`, `ScreenStack`,
  `ChoiceOverlay` already use it.
- The materials: `GlassDrawable` exists. The skill asks for a **bright top
  edge** (light catching the material), **material weight encoding hierarchy**
  (heavier for the bigger surfaces), and **never stacking a light translucent
  surface on another**. Check the clock pill, the app bar and the tool panels.
- Wire **reduced motion** properly: `Motion.isReduced()` reads the system
  `ANIMATOR_DURATION_SCALE` and `Prefs.reduceMotion()`. `Prefs.reduceMotion()`
  exists but **has no settings row yet** — add one. Check that the focus scale,
  the parallax, the sheen and the screen transitions all degrade to something
  gentle rather than nothing.
- The screen transitions exist (a screen slides in from the right and leaves
  to the right). Confirm the *easing* is mirrored and that the transition can
  be interrupted mid-flight.

**P3 — Verify on the TV and finish what is half-built.** The tools were
written and largely verified, but these are **not yet checked on the device**:
- The app manager's per-app detail screen (Open / App info / Enabled toggle /
  Force stop / Clear storage / Uninstall), and the filter tabs.
- The debug panel: processes, battery, network, the logcat tail, the self check.
- The file manager and the LAN share: the Share screen, the address, and
  pulling a file from a laptop.
- The browser: it must be switched on in Settings first, then check the ad
  blocker actually blocks.
- The **first-run setup wizard** ("once a person opens this APK on any Android
  TV, it's going to set it up") is **not built at all**: wallpaper → grid →
  clock → screen saver → offer to become the default launcher. Runs once,
  skippable, never blocks. `Prefs.welcomeSeen()` already exists for it.

**P4 — Ship.**
- Keep `proguard-rules.pro` in sync as components are added. A stale keep rule
  means a **silently broken release build** — that is exactly the bug that was
  already fixed once. Re-verify with:
  `dexdump -f app/build/outputs/apk/release/app-release-unsigned.apk | grep <ClassName>`
  (and check the provider's `openFile`/`query` methods survived, not just the class).
- Re-check the release APK size against the < 6 MB budget as features land.
- Add unit tests for any new pure logic. Keep pure logic out of Activities.

**P5 — Nice to have, only if the above is done.**
- A **deep scan** / storage breakdown with reclaim, which the user asked for
  ("deep loader").
- Search in the file manager is a plain text filter; a proper search box on a
  TV is awkward, so this may be better left as is.

### WORKING STYLE

- **Make a plan always when working** — the user asked for this explicitly.
  `PLAN.md` is the source of truth; update it as you go.
- **Work in a loop**: implement → build → test → fix → commit. Keep the build
  green at every commit.
- **Verify on the physical TV** when the change is about controls, focus or
  layout. The user is watching the TV and will try it.
- **Do not blow up your context window.** Prefer subagents for research
  (`explore`/`general`) and read files surgically.
- Commit often; the repo exists so you can.
- When something genuinely cannot be done on a retail non-rooted Android TV
  (e.g. clearing another app's data, deleting from /system, writing to shared
  storage on Android 11), **say so in the UI and in your report to the user**
  rather than shipping a fake feature.

### DEFINITION OF DONE

Sonora is the TV's home screen: pixel-close tvOS 26 (glass pill clock
top-right, glass app bar over full-bleed aerial artwork, 5:3 cards, focus
scale + sheen + parallax), D-pad navigation like tvOS, long-press per-app
actions, fully customisable settings, and a system screen saver playing
Aerial's aerials with a location overlay and swipe-to-change. On top of that:
an app manager that really disables and uninstalls, a guided live debugging
guide, a file manager with LAN transfer, and an optional ad-blocking browser.
It stays under 6 MB, holds at most two screen-sized bitmaps, adds no
background processes, and idles at essentially zero CPU.

---
