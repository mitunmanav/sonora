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
- [ ] Launcher polish + edge cases
- [ ] Everything else

## 5. Session plan (this session)

Ordered by value, one commit each, build green at every commit.

**A. Launcher (P1) — the product**
- A1. Key dispatch: overlays must not swallow D-pad. Today `dispatchKeyEvent`
  returns `true` for every key while an overlay is up, so the focused row never
  sees the event: no focus movement, no OK, no click. Settings, the app menu
  and every picker are dead. Only BACK may be consumed.
- A2. BACK is breadcrumb-only: close overlay -> pop screen -> scroll grid to top
  and focus the first tile. Never a dialog, never a toggle. Top level only
  leaves the app when Sonora is *not* the default launcher, so the user can
  always reach the TV home screen.
- A3. Cache the app list and its artwork. `AppRepository` caches the list; the
  grid rebuilds from the cache instead of re-querying `PackageManager`.
- A4. Artwork sized to the real tile, not to a guess. Keep the banner only
  when the app has one (the icon bitmap is then unused), and halve everything
  on a low-RAM stick. Target: well under 10 MB of artwork for 25 apps.
- A5. Wallpaper decoded once and cached; not re-decoded on every resume.
- A6. `onTrimMemory` releases the wallpaper and screen-saver bitmaps.
- A7. Edge cases: no apps -> real empty state; `PackageManager` throws ->
  partial list still renders; config change -> grid rebuilt; wallpaper
  permission denied -> bundled scene.

**B. Tools (P2)**
- B1. One `ToolActivity` hosting a `ScreenStack`; every panel is a screen.
- B2. Settings rebuilt on `SettingRow` + `ScreenStack` (delete `SettingsOverlay`).
- B3. App manager: all packages, sort/filter, lazy icons, per-app actions.
- B4. Debug panel over `SystemInfo`.
- B5. Guided live debugging guide: reads live state, opens the exact Settings
  screen, copies adb commands, shows the TV's IP.

**C. Optional extras (P3)** - off by default, one APK
- C1. First-run wizard.
- C2. File manager: browse, copy, move, rename, delete, properties.
- C3. LAN share: HTTP server on a thread inside the visible Share activity.
- C4. Browser: WebView + local hosts-style ad blocker, hidden until enabled.

**D. Ship (P4)**
- D1. Keep `proguard-rules.pro` in sync; re-verify with `dexdump`.
- D2. Release size under 6 MB.
- D3. Unit tests for the new pure logic.
- D4. `PLAN.md` and `HANDOFF.md` updated.
