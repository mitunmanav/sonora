# Sonora — exhaustive plan

Formerly "Aerial", now **Sonora**. A tvOS-style Android TV launcher that also
carries a small TV utility suite. Everything is bundled in **one APK**; the
launcher is the product, the tools are optional extras you switch on.

---

## 0. Non-negotiables

| Rule | Why |
|---|---|
| Zero dependencies. No AndroidX, no Compose, no networking libs, no QR libs. | 1.5 MB of AndroidX buys nothing here and costs memory. |
| No background services, no WorkManager, no JobScheduler, no alarms, no boot receiver. | "Little to no system resource usage" is the headline feature. A launcher that idles at 0% CPU is the product. |
| The only Service is the DreamService, and it exists solely while the system shows the dream. | Screensaver is a user-visible feature, not background work. |
| The LAN file server runs on a thread inside the visible Share activity and dies with it. | No foreground service, no notification, no listening socket when the app is closed. |
| `INTERNET` permission is declared but **no code touches the network** unless the browser or the transfer client is explicitly enabled. | A launcher should not have network access by default. |
| Pure logic lives outside the Activity and is unit-tested. | Keeps `AppOrdering`/`Grid` meaningful. |
| Every screen survives: zero apps, no wallpaper permission, `ActivityNotFound`, rotation, RTL, huge font scale, low memory. | Edge cases are the deliverable, not an afterthought. |
| `minSdk 23`, `compileSdk 35`, `targetSdk 35`. | Play requires TV apps to keep minSdk <= 31. |
| Runtime-sized layout. Never hardcode 960dp. | 720p@240dpi boxes report 1280dp wide; 1080p@320dpi reports 960dp. |

## 1. Sizing the app grid (done)

`Grid` is pure maths, unit-tested, and derives everything from the runtime
screen width:

- Icon = 14.7% of screen width (measured off Apple's tvOS 26 press renders).
- Margin = 2.8% per side, gutter = 1.2%.
- Aspect 5:3 landscape.
- Because the proportions are scale-invariant, every 16:9 screen lands on the
  same column count - which is exactly what tvOS does.
- Below 640dp wide, columns are dropped rather than the icons shrunk.

## 2. Package layout

```
com.mitun.sonora
├── HomeActivity          home screen: grid, focus, app menu, idle saver
├── Grid                  pure: icon geometry          [tested]
├── AppOrdering           pure: order + hidden set     [tested]
├── AppEntry              model: label, pkg, icon, banner, accent
├── AppRepository         launchable apps, off the main thread
├── Prefs                 every user choice, one file
├── Scenes                bundled aerial stills + credits
├── SystemInfo            device facts, read once and cached
├── PackageActions        enable/disable/uninstall/clear-data, guarded
├── dream/
│   └── SonoraDreamService system screen saver
├── tools/
│   ├── ToolActivity      host for every tool panel
│   ├── AppManager        all installed packages, cached
│   ├── DebugPanel        memory/CPU/storage/battery/logcat
│   ├── FileManager       browse, copy, move, rename, delete
│   ├── FileServer        LAN HTTP server, dies with its activity
│   ├── SetupGuide        guided, live, step-by-step debugging guide
│   └── DeepScan          storage breakdown + reclaim
├── browser/
│   ├── BrowserActivity   WebView, off by default
│   └── AdBlock           hosts-style blocker, local list
└── ui/                   platform views only
    ├── Ui, Grid-free helpers, glass drawable
    ├── HeroBackgroundView, AppIconView, AppTileView
    ├── GlassPillView, GlassDrawable
    ├── SettingRow        one list row, four kinds
    ├── ScreenStack       push/pop list screens
    ├── ChoiceOverlay, SettingsOverlay
    └── ScreensaverView
```

## 3. Feature plan

### 3.1 Launcher (the product)
- [x] tvOS grid, 5:3 cards, glass bar, focus scale + sheen + parallax
- [x] Runtime-sized grid
- [ ] BACK is breadcrumb-only: never exits from the top-level grid
- [ ] Cache the app list; never re-query PackageManager in `onResume`
- [ ] `isLowRamDevice()` decides icon/banner cache sizes
- [ ] `onTrimMemory` releases bitmaps
- [ ] Edge cases: no apps, launcher disabled, focus lost on resume

### 3.2 First run
- [ ] Setup wizard: wallpaper -> grid -> clock -> screen saver -> launcher role
- [ ] Runs once, skippable, never blocks the launcher

### 3.3 Guided debugging (live, step-by-step)
- [ ] Reads live state: developer options on? adb authorised? wireless debugging on?
- [ ] Each step can open the exact Settings screen it needs
- [ ] Copy-to-clipboard adb commands, TV IP, current build
- [ ] Never pretends a step is done when it is not

### 3.4 App manager
- [ ] All installed packages, cached, loaded off the main thread
- [ ] Disable/enable a component -> hides bloatware (normal permission)
- [ ] Uninstall via `PackageInstaller.uninstall()` (user-confirmed)
- [ ] Clear data, force stop, app info, per-app detail screen
- [ ] Sort, filter (user/system/disabled), search
- [ ] Uninstall of a preinstalled app = per-user removal; say so plainly

### 3.5 File manager + transfer
- [ ] Browse internal + external storage, breadcrumbs, sort, search
- [ ] Copy / move / rename / delete, properties, open-with
- [ ] LAN HTTP server: share a file or folder, short URL, big on-screen code
- [ ] Share sheet send-out; download into Downloads
- [ ] No socket listening unless the share screen is open

### 3.6 Browser (optional, off by default)
- [ ] WebView, ad blocker with a local hosts-style list
- [ ] URL bar, back/forward/reload/home, bookmarks
- [ ] Nothing starts until the user switches it on

### 3.7 Debug panel
- [ ] Memory, CPU, processes, storage, battery, network, thermal
- [ ] logcat tail, `dumpsys` summary, self-diagnostics

### 3.8 Deep scan
- [ ] Storage breakdown, largest files and folders, cache reclaim

## 4. Edge-case checklist (the real deliverable)

- Zero launchable apps -> empty state, not a crash or a blank screen
- `PackageManager` throws -> caught, partial list still renders
- Wallpaper permission denied -> bundled scene instead
- `ActivityNotFoundException` on launch -> toast, grid survives
- Uninstall/clear-data fails -> reason shown, no crash
- Rotate / resize -> `configChanges` already handles it; no reload
- RTL -> margins mirror, text stays LTR for labels
- Font scale 2.0+ -> rows grow, nothing clips
- Low memory -> caches shrink, bitmaps freed
- Screen off/on -> dream takes over, home redraws on resume
- Back on the grid -> scroll to top, never exit
- WebView missing on the device -> browser screen explains, does not crash
- No external storage -> file manager shows only what exists
- Two screens open at once -> stack stays consistent
- Package deleted while listed -> row disappears on refresh
- `dumpsys` / `logcat` unavailable -> panel degrades, does not crash

## 5. Ship
- [ ] Release build: `minifyEnabled`, `shrinkResources`, `optimizedResourceShrinking`
- [ ] ProGuard keeps the activity, the dream service and any receiver
- [ ] Size budget: < 6 MB APK
- [ ] No `INTERNET` traffic unless the browser or transfer client is on

## Status
- [x] Research (TV grid metrics, device classes, back-button rules, R8 config)
- [x] Rename to Sonora
- [x] Grid: pure, tested, runtime-sized
- [x] Columns preference: Auto by default
- [x] Launcher polish + edge cases (verified on the TV)
- [x] Key dispatch, focus and BACK semantics (verified on the TV)
- [x] Tools host: one `ToolActivity` + one `ScreenStack` for every panel
- [x] Settings rebuilt on `SettingRow` + `ScreenStack`
- [x] App manager, debug panel, guided setup guide
- [x] File manager, LAN share, optional browser with a local ad blocker
- [x] Design foundations: `Motion` (hand-written spring), `Type` (type scale)
- [x] Design pass: springs in the launcher, materials, reduced motion
- [x] First-run setup wizard, in two modes
- [x] Settings rebuilt as an index of short screens
- [x] Tooling: the lists, the picker, the LAN server, the app manager
- [ ] Verify the tools end to end on the TV

## 5. What this session did

### A. Launcher (P1) - the product
- A1. **Key dispatch.** `dispatchKeyEvent` returned `true` for every key while
  an overlay was up, so the focused row never saw the event: no focus movement,
  no OK, no click. Settings, the app menu and every picker were dead. Only
  BACK may be consumed.
- A2. **BACK is breadcrumb-only.** Overlay -> screensaver -> scroll the grid
  to the top and focus the first tile. Never a dialog, never a toggle. It
  leaves the app only when Sonora is *not* the default launcher, so the user
  can always reach the TV home screen. The rule is `BackPolicy`, unit-tested.
- A3. **Cache the app list and its artwork.** `AppRepository` caches the list;
  the grid rebuilds from the cache instead of re-querying `PackageManager`.
- A4. **Artwork sized to the real tile.** The banner is stored at the size the
  tile draws at, the icon bitmap is dropped when the app has a banner the card
  never uses, and everything is halved on a low-RAM stick.
- A5. **Wallpaper decoded once and cached**, not re-decoded on every resume.
- A6. **`onTrimMemory` releases the wallpaper.**
- A7. **Edge cases.** No apps -> a real empty state; `PackageManager` throws ->
  the partial list still renders; a config change rebuilds the grid; a refused
  wallpaper permission falls back to a bundled scene.

### B. Controls, found on the physical TV
- B1. `bringToFront()` on the focused tile **reordered the row**, so the D-pad
  links pointed at the wrong neighbours. The tile now raises itself with `setZ()`.
- B2. The framework's spatial focus search overrode the grid's links. The D-pad
  is now driven by the activity: along the row, then between the rows, with
  the clock above the first row.
- B3. The first tile did not get the focus at startup (the grid is not laid
  out when it is built).
- B4. `Ui.moveListFocus` asked `findFocus()` whether the list had focus, which
  it never does, so DOWN always jumped back to the first row. It uses
  `getFocusedChild()` now.
- B5. `ScreenStack.focusTop()` focused a heading, which cannot take focus.
- B6. A label in "focused only" mode stayed visible until the tile had been
  focused once.
- B7. Screens in the stack were transparent, so the screen being replaced
  showed through the one sliding in.
- B8. The app menu was drawn over the app title, with the grid showing through.

### C. Tools (P2)
- C1. One `ToolActivity` hosting a `ScreenStack`; every panel is a screen.
- C2. Settings rebuilt on `SettingRow` + `ScreenStack` (`SettingsOverlay` deleted).
- C3. App manager: all packages, filters, lazy icons, per-app actions.
- C4. Debug panel over `SystemInfo`, plus processes, battery, network, the
  logcat tail and a self check.
- C5. Guided setup guide: reads live state, opens the exact Settings screen,
  copies adb commands, shows the TV's IP.

### D. Optional extras (P3) - off by default, one APK
- D1. File manager: browse, sort, search, copy, move, rename, delete, properties.
- D2. LAN share: an HTTP server on a thread inside the visible Share activity.
- D3. Browser: WebView + a local hosts-style ad blocker, hidden until enabled.
- D4. `SonoraProvider`, so a file can be handed to another app as a content URI.

### E. Design foundations (apple-design skill)
- E1. `ui/Spring.java` solves the spring itself, because AndroidX's
  `SpringAnimation` is not in the platform SDK. It keeps the value and the
  velocity when re-targeted, so an animation can be interrupted.
- E2. `ui/Motion.java` is the spring API, and it reads the system's
  `ANIMATOR_DURATION_SCALE` so reduced motion is honoured.
- E3. `ui/Type.java` is the type scale: tracking and leading follow the size.
- E4. The screen stack slides in from the right and leaves to the right.

## 6. Next session

**P1 - Finish the design pass.** *(done - see section 7)*
- Springs for the launcher's focus scale and parallax sway.
- The type scale across the remaining screens.
- Materials: a bright top edge, weight encoding hierarchy.
- A settings row for "reduce motion"; check every animation degrades gently.
- Confirm the transitions are mirrored and interruptible.

**P2 - Verify on the TV.** The app manager's detail screen, the debug panel,
the file manager, the LAN share and the browser have not been checked on the
device yet.

**P3 - Ship.** ProGuard keep rules in sync, release size under 6 MB (now
3.27 MB), unit tests for new pure logic.

**P4 - The first-run wizard.** "Once a person opens this APK on any Android
TV, it's going to set it up": wallpaper -> grid -> clock -> screen saver ->
offer to become the default launcher. Runs once, skippable, never blocks.
`Prefs.welcomeSeen()` is already there for it.

*(done - see section 8)*

## 7. Session: launcher only (no TV connected)

Scope: the launcher and the shared `ui` package. The tools, the wizard and
the APK/REA study are deliberately untouched this session.

### F1. The spring foundation was broken
`Spring` started every animation at `value = 0` and wrote that value to the
view on the first frame. Every spring therefore snapped to its target
instantly instead of animating: the panel slide-in never slid, and the
focus scale never sprang. The spring also never read the view's current
value, so a re-targeted animation could not continue from what was on
screen.

Fix: `Spring` becomes pure physics (no Android types) and `Motion` drives it
from a `Choreographer`, reading the presentation value off the view before
it starts and keeping value + velocity when it is re-targeted. Unit-tested.

### F2. The lean and the parallax never ran
`HomeActivity` asked the *previous* tile to sway, but `AppTileView.sway()`
returns early when the tile is not focused - and the previous tile has
already lost focus. So the 3D lean and the artwork parallax were dead code.
The artwork offset was also never returned to rest.

Fix: the newly focused tile leans, the lean springs back to zero, and the
artwork inside the card shifts with the lean and springs back to rest with
it.

### F3. Tween -> spring in the launcher
Focus scale, label fade and the lean were fixed-duration tweens with an
overshoot interpolator. All three are springs now, so an interrupted focus
change continues instead of restarting.

### F4. The type scale on the launcher screens
The shelf, the clock pill, the tile labels, the app menu and the screen
saver overlay still used ad-hoc text sizes. They use `Type` now, including
a paint variant for the canvas-drawn screen saver text.

### F5. Materials
`GlassDrawable` gets a bright top edge (light catching the material) and the
weight hierarchy is corrected: the big app bar is the heavier material, the
small clock pill the lighter one. It was the other way round.

### F6. Reduced motion
`Prefs.reduceMotion()` had no settings row and nothing consumed it beyond
the first read. There is a row now, and it is honoured by the focus scale,
the lean, the sheen, the screen saver drift and the panel transitions -
each degrading to a gentler equivalent, never to nothing.

### F7. Launcher edge cases
- The wallpaper was decoded on the main thread on every resume: now off it.
- The idle watcher woke the CPU every second for nothing: now it sleeps
  until the screen saver is actually due. It also stopped for good after the
  first screen saver - it starts again when the saver is dismissed and it
  stops entirely while the launcher is in the background.
- Changing "apps per row", "app names", the order or the hidden apps in
  Settings did nothing until the app was restarted: the grid now rebuilds
  on resume.
- A rebuilt grid lost the focus: the focused app is now kept.
- "Reload apps" promised a reload that never happened: it does now.
- BACK left to whatever app was open before, not to the TV home screen.
- A configuration change reused artwork rendered for the old screen size.
- The app artwork was cached at the width of the tile it was first drawn at,
  so changing the column count left every banner the wrong size. The cache
  is now keyed by the tile width.
- `Motion` keeps its runners in a `WeakHashMap` keyed by the view, so a
  runner that held its own view would have pinned its own key and never been
  collected. The view properties hold the view weakly.
- Dead code removed: the unused banner blur, the per-frame scrim
  allocation, the unused fields, the dead helpers in `Ui`.

### F8. The D-pad never reached the overlays
The activity intercepts the D-pad to drive the grid, but it did so before
`super.dispatchKeyEvent`, so the app menu and the screen saver never saw the
arrow keys: the menu could not be walked and the saver could not change
scene. The grid now only takes the D-pad when the grid has the focus;
anything else gets the key.

### F9. The spring physics is unit-tested
`Spring` is pure maths, so it is tested without a device: a critical spring
never overshoots, a springy one does, a re-aimed spring keeps its speed and
still arrives, and a long stall cannot make it explode.


## 8. Session: the wizard, the settings, the tooling

### G1. The first-run setup
`tools/SetupWizard.java` walks the user through the setup once: the mode,
the wallpaper, the clock, the screen saver, the home screen role. It runs
once, BACK walks back through it, and it never blocks the launcher. Each
screen says where it is ("Step 2 of 5"), because a setup that does not say
where you are is a setup the user has to guess their way through.

### G2. Two modes
`Prefs.mode()` is `Default` or `Technical`. Default shows the settings that
matter to anybody, in plain words. Technical shows all of them: apps per row,
seconds, reduce motion, the app order, the setup guide. The mode is picked in
the setup and can be changed in Settings.

### G3. The settings are an index now
One long list of twenty rows was too much to read, so Settings is an index:
Home Screen, Clock, Screen Saver, Apps, Tools, System. Every screen holds a
handful of rows. The technical rows only exist in technical mode.

### G4. The focus works
A screen that was built while the activity was starting could not take the
focus, so the D-pad went nowhere and OK did nothing. The stack asks again
when the window gets the focus, and a rebuilt screen keeps the row the user
was on and where it was scrolled to.

### G5. The lists work
The rows of the app manager and the file manager were focusable, which stops
a `ListView` from ever reporting a click: OK did nothing in both panels. The
list holds the focus now, the selection is drawn for it, and the filters sit
above the list instead of inside it.

### G6. The picker opens on the value it shows
`ChoiceOverlay` never took the selected index, so every picker opened on the
first option: OK silently changed the setting.

### G7. The LAN server
It kept listening after the user left the share screen, which broke the "no
background processes" rule. It stops in `onStop` now. The path check was a
prefix test with no separator, so a folder that merely started with the same
name could be served; the separator is there now. Names are URL-decoded and
the listing is URL-encoded, and a picture is served as a picture.

### G8. The app manager
The row said "Enabled" but showed the hidden state, and never updated. It
says "Hidden from home screen" now and the screen is rebuilt after the tap.
`CHANGE_COMPONENT_ENABLED_STATE` was never declared, so hiding an app was
refused by the system: it is declared now, and so is
`KILL_BACKGROUND_PROCESSES` for force stop.

### G9. The device panel
`getRunningAppProcesses()` can return null and took the panel down with it.
The process list is read off the UI thread now, because it is a binder call
per app. "Refresh" refreshes the screen instead of throwing the stack away,
and the numbers are read again instead of coming from the cache.
