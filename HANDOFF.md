# HANDOFF — Sonora

Copy everything below the line into the new session. Nothing else is needed.

---

## PROMPT FOR THE NEW SESSION

You are continuing work on **Sonora**, an Android TV launcher that replicates the
Apple tvOS 26 home screen, is extremely light on system resources, and is fully
customizable. It ships a screen saver (Daydream) because the previous session on
this TV removed MiTV's own screen saver.

The project lives at `/home/mitun/aerial`. **Git repo, working tree clean, 3
commits** — use git, it was added specifically so large refactors are safe.

Work in **ponytail mode (full)**: smallest complete change, no speculative
abstractions, comment only the "why", no features nobody asked for. Keep the app
**dependency-free** (no AndroidX, no Compose, no networking libs, no QR libs).

### THE GOAL (this changed mid-project — read carefully)

Sonora is no longer "just a tvOS launcher". The user expanded the scope to a
**launcher + TV utility suite**, in **one APK**:

- The **launcher is the main product**. It must stay the best thing in the app.
- **Optional, off by default** extras, bundled but switchable per-feature:
  a **browser with an ad blocker**, a **file manager**, and **file transfer**.
- Plus: an **app manager** (disable / uninstall / manage every installed app), a
  **guided step-by-step live debugging guide**, and a **debug panel**.

**The headline requirement: "extremely light, little to no usage of system
resources".** Concretely:
- **No background services.** No WorkManager, no JobScheduler, no alarms, no
  boot receiver, no foreground service. A launcher that idles at 0% CPU *is* the
  product.
- The only Service is the Daydream `DreamService`, which exists solely while the
  system is showing the dream.
- The **LAN file server runs on a thread inside the visible Share activity and
  dies with it** — no listening socket when the app is closed.
- `INTERNET` permission is declared but **no code touches the network** unless
  the browser or the transfer client is explicitly enabled.
- Target: **< 6 MB APK**, at most two screen-sized bitmaps alive.

### USER DECISIONS ALREADY MADE (do not re-litigate)

| Question | Answer |
|---|---|
| App name | **Sonora** (renamed from "Aerial" — package is now `com.mitun.sonora`) |
| Browser packaging | **One APK, browser off by default** (hidden until enabled in Settings) |
| File transfer | **Full-fledged file manager + file transfer**, bundled and optional like the browser |

The file transfer answer was free-text: *"a full fledge file transfer and a full
fledge file manger optinal bundled like browser as well"*. So implement a real
file manager (browse, copy, move, rename, delete, properties) **and** a real
transfer mechanism. The user asked for "fast, easy to connect". Recommended
approach that satisfies "easy to connect" without a big dependency: a small
**LAN HTTP server** (so a phone/laptop types the TV's IP and pulls files) plus
the **Android share sheet** for sending out. Avoid SMB (big to implement well).
Keep the server on a thread inside the Share activity — **no Service**.

### THE USER'S EXACT FEATURE REQUESTS (verbatim, all still open)

> "To enable debugging, we need a guided, step‑by‑step live guide that is
> helpful. Once enabled, our app should be able to uninstall, save, and perform
> other actions through a built‑in file manager and file‑share feature that is
> fast, easy to connect, and lightweight. The file manager should be advanced yet
> simple to use. We also need to rename the app and add an optional browser with
> an ad blocker; the browser should be lightweight as well."

> "include a lot of features like deep loader, debugging, removing, and disabling
> things. Once a person opens this APK on any Android TV, it's going to set it
> up."

Interpretation to build:
1. **Guided live debugging guide** — reads *live* device state (developer
   options on? adb authorised? wireless debugging on?), step by step, each step
   able to open the exact Settings screen it needs, copy-to-clipboard adb
   commands, show the TV's IP. Never pretend a step is done when it isn't.
2. **App manager** — disable ("removing and disabling things"), uninstall,
   clear data, force stop, app info, per-app detail. Plus a **deep loader /
   deep scan** of storage and installed apps.
3. **First-run setup** — "once a person opens this APK on any Android TV, it's
   going to set it up": a wizard that configures wallpaper, grid, clock, screen
   saver and offers to become the default launcher. Runs once, skippable.
4. **File manager** — advanced yet simple.
5. **Optional browser with ad blocker** — lightweight.

### CURRENT STATE — what exists and works

A complete, building app. **Do not rewrite it — continue from it.**

```
/home/mitun/aerial
├── PLAN.md                     # the full plan, keep it updated
├── HANDOFF.md                  # this file
├── .agents/skills/             # official Google Android skills (see below)
├── settings.gradle             # rootProject.name = "Sonora"
├── build.gradle                # root, intentionally empty
├── gradle.properties           # useAndroidX=false, optimizedResourceShrinking=true
├── local.properties            # sdk.dir=/home/mitun/Android/Sdk
├── app/build.gradle            # AGP 8.13.0, compileSdk 35, minSdk 23, targetSdk 35,
│                               # Java 17, junit:junit:4.13.2 (test only), NO runtime deps
├── app/proguard-rules.pro      # keeps HomeActivity + SonoraDreamService  (NEEDS UPDATING)
└── app/src
    ├── main/AndroidManifest.xml
    ├── main/res/values/{strings.xml,styles.xml}
    ├── main/res/drawable-nodpi/
    │   ├── sonora_ngorongoro.jpg  sonora_vigo.jpg  sonora_zhuravlyne.jpg
    │   ├── sonora_punjab.jpg      sonora_dulmen.jpg   (5 aerials, CC BY-SA 4.0)
    │   ├── banner.png  ic_launcher.png
    ├── main/java/com/mitun/sonora/
    │   ├── HomeActivity.java      # home screen: grid, D-pad focus, app menu,
    │   │                          # settings overlay, idle screensaver  (~830 lines)
    │   ├── Grid.java              # PURE: runtime-sized grid maths       [tested]
    │   ├── AppOrdering.java       # PURE: order + hidden set            [tested]
    │   ├── AppEntry.java          # model: label, pkg, component, icon, banner, accent
    │   ├── AppRepository.java     # launchable apps, rendered off the main thread
    │   ├── AppManager.java        # ALL installed packages, cached 30s
    │   ├── PackageActions.java    # enable/disable/uninstall/clear/force-stop, guarded
    │   ├── SystemInfo.java        # cached device facts + byte formatting
    │   ├── Prefs.java             # every user choice, one file
    │   ├── Scenes.java            # the 5 bundled scenes: res id + name + credit
    │   ├── dream/SonoraDreamService.java  # system Daydream entry point
    │   └── ui/
    │       ├── Ui.java              # dp/sp, glass drawable, bitmap decode, gear, immersive
    │       ├── HeroBackgroundView.java  # full-bleed wallpaper + accent tint + scrim
    │       ├── AppIconView.java     # the 5:3 app card, focus sheen
    │       ├── AppTileView.java     # focusable tile: scale-up focus, parallax sway
    │       ├── GlassPillView.java   # top-right clock + profile pill (opens Settings)
    │       ├── GlassDrawable.java   # cheap "Liquid Glass" fill/edge/highlight, no blur
    │       ├── SettingsOverlay.java # tvOS-style settings list (LEGACY - see below)
    │       ├── ChoiceOverlay.java   # tvOS-style list picker
    │       ├── SettingRow.java      # NEW: one list row, four kinds (header/value/action/push)
    │       ├── ScreenStack.java     # NEW: push/pop list screens, shared by every panel
    │       └── ScreensaverView.java # Ken Burns crossfade screensaver, 2 bitmaps max
    └── test/java/com/mitun/sonora/
        ├── AppOrderingTest.java   # 3 tests
        └── GridTest.java          # 8 tests
```

**Build / test commands** (gradle 8.13 + JDK 21 already on this box):

```bash
cd /home/mitun/aerial
GRADLE=$(echo /home/mitun/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle)
$GRADLE --offline :app:assembleDebug
$GRADLE --offline :app:testDebugUnitTest      # 11 tests, all passing
$GRADLE --offline :app:assembleRelease        # minified + shrunk
```

Gradle **must** run `--offline`; all AGP/Gradle artifacts are cached locally.

### WHAT WAS COMPLETED IN THE LAST SESSION

1. **Renamed Aerial → Sonora** — package `com.mitun.aerial` → `com.mitun.sonora`,
   classes renamed (`AerialScenes`→`Scenes`, `AerialDreamService`→`SonoraDreamService`,
   `AerialScreensaverView`→`ScreensaverView`), resources `aerial_*.jpg`→`sonora_*.jpg`,
   prefs file `aerial`→`sonora`, `Theme.Aerial`→`Theme.Sonora`. Done with `git mv`
   so history is preserved.
2. **`Grid.java`** — pure, unit-tested, resolution-independent grid maths. This is
   the single most important change so far; details below.
3. **`SettingRow.java`** + **`ScreenStack.java`** — the shared list-row and
   push/pop-screen primitives that every new panel will use. Built but **not yet
   wired into anything**.
4. **`SystemInfo.java`**, **`AppManager.java`**, **`PackageActions.java`** — the
   data/action layer for the app manager and debug panel. Built, **not yet
   wired into any UI**.
5. **`gradle.properties`**: added `android.r8.optimizedResourceShrinking=true`
   (required for AGP 8.12/8.13 per the r8-analyzer skill).
6. **Git repo created** with 3 commits and a `.gitignore`.

### THE GRID — why it matters, do not undo it

The old code hardcoded 6 columns and 44dp padding. That is wrong on most TVs:
Google's own density table maps 720p/1080p/4K to 960×540dp, **but many Chinese
boxes report 240dpi, which is 1280×720dp**. Hardcoding breaks on exactly the
hardware this app targets.

`Grid` now derives everything from the runtime width, using proportions
**measured off Apple's own tvOS 26 press renders** (`tvos-research/shots/apple-home.jpg`):

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

- **No network permission in practice.** `INTERNET` may be declared, but no code
  path may use it unless the browser/transfer feature is enabled by the user.
- **No AndroidX, no Compose, no third-party libraries of any kind.** Platform
  APIs only.
- **No background processes.** No Service except the DreamService. No receivers.
- `minSdk 23` — Play requires TV apps to keep minSdk ≤ 31. **Do not raise it.**
- `QUERY_ALL_PACKAGES` is already declared (needed for the app manager). If you
  add `<queries>`, keep it consistent.
- `android.software.leanback` and `android.hardware.touchscreen` are both
  `required="false"` — **never** make them required, it blocks installation on
  some TVs.
- The Play TV checklist wants a **320×180 banner** (exists) and an icon
  **≥160×160 at xhdpi** (exists, 512×512).

### RESEARCH FINDINGS (do not redo — these were expensive)

**tvOS reference** (already measured, images may be wiped; re-fetch from
`https://www.apple.com/newsroom/images/2025/09/new-versions-of-apples-software-platforms-are-available-today/article/Apple-OS-availability-tvOS-26-Home-Screen_big.jpg.large_2x.jpg`):

- tvOS 26 home screen = full-bleed "Top Shelf" artwork of the focused app with
  the app name in large light type top-left; a **clock + profile avatar in a
  translucent pill at top-right**; a **row of wide 5:3 app icons in a rounded
  translucent glass bar** floating over the artwork near the bottom; further
  rows below, scrollable.
- App names are **not** shown under icons by default — only the focused tile
  shows its name (tvOS 17+). `Prefs.LABELS_FOCUSED` is the default. ✔ already done.
- Focus effect: focused icon scales ~1.10× and lifts with a stronger shadow;
  neighbours lean away with subtle 3D parallax/tilt; a specular sheen sweeps the
  focused card. ✔ already implemented in `AppTileView` + `AppIconView`.
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
  **Cache the app list and its icons.** ← `AppManager` does this; `HomeActivity`
  still needs to stop re-loading on resume.
- **`ActivityManager.isLowRamDevice()`** is the official way to detect a 1GB
  stick — never infer from RAM. Use it to size caches.
- **BACK is breadcrumb-style only.** It must never act as a toggle, must never
  show a confirmation dialog, there must be no on-screen back button, and
  consecutive presses must always eventually reach the TV home screen. On the
  top-level app grid, BACK should scroll to the first row / clear focus —
  **never exit the launcher.** ← **not yet implemented in `HomeActivity`.**
- **Dream picker is hidden on Google TV and Philips-style sets.** Ship a
  user-launched screensaver *activity* as the primary path (already exists as the
  in-app idle saver) plus a deep link to `Settings.ACTION_DREAM_SETTINGS`.
- **A normal app CAN hide bloatware** via
  `PackageManager.setComponentEnabledSetting(..., COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP)`
  — `CHANGE_COMPONENT_ENABLED_STATE` is a **normal** permission. This is the
  only reliable hide mechanism for a non-privileged app. ✔ in `PackageActions`.
- **Uninstalling a preinstalled app is per-user removal**, not deletion from
  `/system` (that needs the hidden `DELETE_SYSTEM_APP` flag). The UI must say
  this plainly rather than promising removal.
- **`clearApplicationUserData` for another app is NOT possible** from a normal
  app — the public API doesn't exist and `CLEAR_APP_USER_DATA` is privileged.
  `PackageActions.clearData` therefore opens the system app-info page and tells
  the user to use "Clear storage". **Do not try to work around this.**
- `Settings.Secure.screensaver_components` is a **Secure** setting — a normal app
  cannot write it; it can only offer a dream and link to the picker.
- Google TV enters **Ambient Mode after 10 min inactivity**, then Energy Saver
  powers the display off.
- Play TV quality rules: minSdk ≤ 31; 320×180 banner; no touchscreen requirement.

**Device class reality** (so the app works on cheap sticks too):

- Chipset/OS tiers: Android 6/7 (S905, RK3228) · Android 9/11 (S905X2/X3,
  S922X, RK3318, RK3566) · Android 12/13/14 (MTK9456, RTD286x).
- **dp viewports**: 720p@240dpi = 853×480dp · 1080p@320dpi = 960×540dp ·
  1080p@240dpi = 1280×720dp · 4K@480dpi = 1280×720dp. **Read at runtime.**
- 1GB sticks are `isLowRamDevice() == true` — the common case, not the exception.
- Samsung/Tizen is **not** Android TV, out of scope.

### OFFICIAL ANDROID SKILLS INSTALLED (use them)

Google's official `android/skills` repo is installed at `.agents/skills/`:

| Skill | Use for |
|---|---|
| `r8-analyzer` | release APK size, ProGuard keep-rule analysis. **Note: it needs R8 ≥ 9.4.34 to produce the report, and this box is offline — so apply its `references/CONFIGURATION.md` checklist by hand rather than running the script.** Already applied: `minifyEnabled`, `shrinkResources`, `proguard-android-optimize.txt`, `android.r8.optimizedResourceShrinking=true`. Verify `android.enableR8.fullMode=false` is absent (it is). |
| `android-profiler` | CPU/memory/heap profiling, allocation churn, LMK analysis. |
| `anr-audit-and-fix` | ANR hunting — relevant since this app must never block the main thread. |
| `android-permissions-security` | auditing the manifest permissions and IPC components. |
| `android-intent-security` | auditing intent handling, `exported` components, intent redirection. |

Re-install or update them with:
`android skills add --all --project=.` / `android skills update --all`.
The `android` CLI is at `/home/mitun/.local/bin/android`.

### GOTCHAS / TRAPS (learned the hard way)

- **`Ui.drawCenterCrop` uses a shared static `Paint`.** If you add an alpha
  variant you must restore the previous alpha — a bug where alpha leaked across
  draws was already fixed once here. Do not reintroduce it.
- `HeroBackgroundView` deliberately does **not** paint the focused app's banner
  over the wallpaper (tvOS shows the app's *content* there, which a launcher
  doesn't have).
- `AppRepository` renders each app's `android:banner` (TV banner) into the wide
  5:3 card when present, else generates a branded card from icon + accent
  colour. That is why the cards look like real tvOS app icons.
- `AppOrdering` is pure and unit-tested — keep ordering logic out of
  `HomeActivity`.
- `ScreenStack.refresh()` currently **rebuilds the top screen's rows list but
  does not re-render the view** — it clears `screen.rows` and never repopulates
  or rebuilds. It is half-finished; either finish it or delete it. Prefer
  rebuilding the screen properly when you need fresh values.
- `SettingRow.DANGER` is `3 + 100` = 103 — an odd constant; if you use it,
  give it a real name.
- `Prefs.columns()` now returns **0 = Auto**; `Grid.resolve(widthDp, stored)`
  handles the fallback. The settings row shows "Auto (N)".
- The ADB device (`192.168.68.108:5555`, Xiaomi MiTV-AYFR0, Android 11) was
  **disconnected by the user** for this session. It may come back later:
  `adb connect 192.168.68.108:5555`. **Do not waste time on device work** —
  build and test on the JVM; the user wants everything verified at the end.

### NEXT WORK, IN PRIORITY ORDER

The user asked to "plan exhaustively and proceed", "fix all the edge cases", and
that "the launcher is main". So:

**P1 — Launcher polish + edge cases (the main product, do this first)**
- [ ] BACK semantics: breadcrumb-only; on the top-level grid it scrolls to the
      first row / clears focus and **never exits the launcher**. Never a confirm
      dialog.
- [ ] Stop re-querying `PackageManager` on resume; cache the app list + icons
      (TVs kill background processes and slow the transition to the launcher).
- [ ] Use `isLowRamDevice()` to size the icon/banner caches.
- [ ] Implement `onTrimMemory` to release bitmaps.
- [ ] Edge cases: zero launchable apps → a real empty state, not a blank screen;
      `PackageManager` throwing → caught, partial list still renders; wallpaper
      permission denied → fall back to a bundled scene; `ActivityNotFoundException`
      → toast and survive; font scale 2.0+ → rows grow without clipping; RTL →
      margins mirror; rotate/resize → no reload (`configChanges` already set).

**P2 — Wire the built pieces into UI**
- [ ] Replace `SettingsOverlay`'s hand-rolled rows with `SettingRow` +
      `ScreenStack` so settings, app manager, debug panel and file manager all
      navigate identically.
- [ ] Add the tool host: a `ToolActivity` (or a screen stack inside
      `HomeActivity`) that every panel lives in.
- [ ] App manager UI over the already-built `AppManager` + `PackageActions`:
      list all packages, sort/filter (user/system/disabled), search, per-app
      detail with Open / App info / Hide from Home / Uninstall / Clear storage /
      Force stop. **Icons must load lazily** — loading every icon up front is
      what makes these panels slow.
- [ ] Debug panel over `SystemInfo`: memory (heap/native/system), CPU, processes,
      storage, battery, thermal, logcat tail, and a self-check row.
- [ ] **Guided live debugging guide** (explicitly requested): step-by-step,
      reads live device state, each step opens the exact Settings screen, copies
      adb commands to the clipboard, shows the TV's IP.

**P3 — Optional features (off by default, one APK)**
- [ ] First-run setup wizard: wallpaper → grid → clock → screen saver → offer to
      become the default launcher. Runs once, skippable, never blocks.
- [ ] File manager: browse internal + external storage, breadcrumbs, sort,
      search, copy/move/rename/delete, properties, open-with.
- [ ] File transfer: LAN HTTP server on a thread inside the Share activity
      (short URL + big on-screen code) + Android share sheet for sending out.
- [ ] Browser: WebView + hosts-style ad blocker with a **local** blocklist
      (no network round-trip for the list), URL bar, back/forward/reload/home,
      bookmarks. Off by default; hide the tile entirely when disabled.

**P4 — Ship**
- [ ] Update `proguard-rules.pro` for every new component (activity, dream
      service, anything else reachable from the manifest).
- [ ] Verify the release APK builds, check the size against the < 6 MB budget,
      and confirm the dream service survives minification.
- [ ] Add unit tests for any new pure logic (e.g. file sorting, blocklist
      matching, path helpers). Keep pure logic out of Activities.

### WORKING STYLE

- **Make a plan always when working** — the user asked for this explicitly.
  `PLAN.md` is the source of truth; update it as you go.
- **Work in a loop**: implement → build → test → fix → commit. Keep the build
  green at every commit.
- **Do not blow up your context window.** Prefer subagents for research
  (the `explore`/`general` subagents work well) and read files surgically.
- Commit often; the repo exists so you can.
- When something genuinely cannot be done on a retail non-rooted Android TV
  (e.g. clearing another app's data, deleting from /system), **say so in the
  UI and in your report to the user** rather than shipping a fake feature.

### DEFINITION OF DONE

Sonora is the TV's home screen: pixel-close tvOS 26 (glass pill clock
top-right, glass app bar over full-bleed aerial artwork, 5:3 cards, focus
scale + sheen + parallax), D-pad navigation like tvOS, long-press per-app
actions, fully customisable settings, and a system screen saver playing Aerial's
aerials with a location overlay and swipe-to-change. On top of that: an app
manager that really disables and uninstalls, a guided live debugging guide, a
file manager with LAN transfer, and an optional ad-blocking browser. It stays
under 6 MB, holds at most two screen-sized bitmaps, adds no background
processes, and idles at essentially zero CPU.

---
