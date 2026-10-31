# Sonora — plan

Formerly "Aerial". Renamed to **Sonora** (user's choice).

A tvOS-style Android TV launcher that doubles as a TV utility suite: app
manager, file manager + LAN file transfer, a debug/setup guide, and an optional
ad-blocking browser.

## Hard constraints
- **Zero dependencies.** No AndroidX, no Compose, no networking libs, no QR libs.
  Platform APIs only. Target: < 5 MB APK, < 2 background processes.
- **No background processes by default.** The LAN file server runs on a thread
  inside the Share screen's Activity and dies with it - no Service, no
  notification, no background task.
- **Only one APK.** Browser, file manager and tools are bundled but OFF by
  default and enabled per-feature in Settings. `INTERNET` is declared but no
  code touches the network until the browser or the file-transfer client is
  explicitly turned on.
- `minSdk 23` (Play requires <= 31 for TV apps). `compileSdk 35`.
- `QUERY_ALL_PACKAGES` + explicit `<queries>` for the app manager.

## Universal-by-construction rules (learned from the docs)
- **Read geometry at runtime, never hardcode.** Google's TV density table maps
  720p/1080p/4K to 960x540dp, but many boxes report 240dpi = 1280x720dp.
  Every size comes from `DisplayMetrics` at runtime.
- **Google's TV grid** (developer.android.com/design/ui/tv/guides/styles/layouts):
  960dp canvas, 12 cols x 52dp, 20dp gutters, **58dp side margins**, content
  width 844dp. Icon grid derives from that, scaled to the real width.
- **`ActivityManager.isLowRamDevice()`** decides cache sizes - never infer from RAM.
- **Never re-query `PackageManager` in `onResume`.** TVs kill background
  processes and transition to the launcher is slow. Cache the app list.
- **BACK is breadcrumb-only.** Never exits the launcher from the top-level grid,
  never shows a confirm dialog, no on-screen back button.
- **Dream picker is hidden on Google TV / Philips.** Ship a user-launched
  screensaver *activity* as the primary path + deep link to `ACTION_DREAM_SETTINGS`.
- **A normal app can hide bloatware** via `setComponentEnabledSetting` +
  normal permission `CHANGE_COMPONENT_ENABLED_STATE`. Uninstall of a
  preinstalled app = per-user removal via `PackageInstaller.uninstall()`
  (`REQUEST_DELETE_PACKAGES`, user-confirmed).

## Feature list
### A. Foundation
- [x] `Grid` - pure, tested, runtime-sized grid maths
- [x] `SettingRow` - one tvOS list row, four kinds
- [x] `ScreenStack` - push/pop list screens, shared by every panel
- [ ] Wire `Grid` into `HomeActivity`; 58dp-equivalent margins scaled to width
- [ ] BACK semantics: breadcrumb only, never exit from the grid
- [ ] Rename package resources to Sonora

### B. First run
- [ ] Setup wizard: wallpaper -> grid -> clock -> screen saver -> launcher role
- [ ] Wizard runs once, skippable, resumable

### C. Debugging guide (guided, step-by-step, live)
- [ ] Live state checks: developer options on? adb authed? wireless debugging on?
- [ ] Steps that open the exact Settings screen
- [ ] Copy-to-clipboard adb commands, TV IP display

### D. App manager
- [ ] List every installed package (cached, off the main thread)
- [ ] Disable/enable components -> hide bloatware
- [ ] Uninstall (user-confirmed), clear data, force stop, app info
- [ ] Sort/filter, search, per-app detail screen

### E. File manager + transfer
- [ ] Browse internal + external storage, breadcrumbs, sort, search
- [ ] Copy/move/rename/delete, properties, open-with
- [ ] LAN HTTP server: share a file/folder, short URL, big on-screen code
- [ ] Share-sheet send-out, download-into-Downloads

### F. Browser (optional, off by default)
- [ ] WebView, ad blocker (hosts-style list, local)
- [ ] Basic UI: URL bar, back/forward/reload/home, bookmarks

### G. Debug panel
- [ ] Memory, CPU, processes, storage, battery, network, thermal
- [ ] logcat tail, `dumpsys` summary, app self-diagnostics

### H. Deep scan
- [ ] Storage breakdown, largest files/folders, cache reclaim, quick actions

### I. Ship
- [ ] Release build, ProGuard keeps dream + receivers, size budget
- [ ] `isLowRamDevice()` behaviour, `onTrimMemory` release
- [ ] Unit tests for pure logic

## Status
- [x] Research (leanback/TV specs, grid metrics, TV device classes)
- [x] Plan
- [ ] A. Foundation (in progress)
