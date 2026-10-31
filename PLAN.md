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
- [ ] Design pass: springs in the launcher, materials, reduced motion
- [ ] First-run setup wizard
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

**P1 - Finish the design pass.**
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
