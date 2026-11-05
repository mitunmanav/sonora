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

Walked through the code on 2026-10-31; what was broken is fixed and written
up in section 10.

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

---

## 9. The search screen, rebuilt the tvOS way

### 9.1 What the reference actually says

Read from Apple's own material, not from memory:

**WWDC26, "Design intuitive search experiences"**
- The **search field** keeps its core elements intact. The magnifying glass is
  universally recognised as Search; a custom icon must closely resemble the
  symbol it replaces.
- **Placement decides the animation.** Inline, the field stays at the top when
  it is active. In a bottom toolbar it animates up over the keyboard. A
  launcher has no bottom toolbar, so the field is **inline at the top**.
- A **button appearance** "conveys that people will tap will immediately
  engage search, and bring up the keyboard". So the search button is not a
  shortcut to a list — it opens search *with the keyboard already up*.
- Results update **live** while typing, and the number of suggestions is
  limited so "search results feel front, and center".

**WWDC20, "Discover search suggestions for Apple TV"**
- tvOS 14 **increased the size of the text in the search field** "so it's easy
  to see what you've typed", and moved many keyboards to one line "to leave
  more space for the search results".
- For IR remotes the keyboard is a **grid**, so the field and the results are
  reachable with the D-pad alone.
- While typing, the results occupy part of the screen width and "slide in to
  fill the screen when focused".

**The tvOS focus engine**
- A row is calm and evenly spaced; a row that continues off-screen shows
  10–20% of the next item so it is obvious there is more.

**tvOS 26 Liquid Glass**
- Glass belongs to the field and the toolbar. Content in the scroll region
  uses ordinary content styling.

**The apple-design skill, applied**
- Springs, not tweens. Damping 1.0 (no overshoot) unless the gesture carried
  momentum. Response 0.3–0.4 s.
- Enter and exit along the same path. Materialize, do not just fade: animate
  scale and opacity together.
- Reduced motion keeps the feedback and drops the travel.
- Type is size-specific: large text is tracked tighter.

### 9.2 What Google TV does, for the parts tvOS does not cover

From the teardown (`docs/research/GOOGLE-TV-TEARDOWN.md` §9):
- The launcher **never handles the search key**. The system fires
  `ACTION_ASSIST` at the installed assistant.
- The launcher's own entry point is a **search widget in the system bar**, and
  it is **two orbs in one slot**: a microphone ("Click to speak") and a
  keyboard ("Click to type"), morphing into each other on focus.
- Both orbs fire the **same intent**: `ACTION_ASSIST`, flags
  `NEW_TASK | RESET_TASK_IF_NEEDED`, extra **`search_type`** = 1 for voice,
  0 for keyboard.
- No assistant? Google toasts "Application unavailable" and gives up. It
  never falls back to the keyboard. We do.

### 9.3 What this means for Sonora, concretely

| # | Change | Why | Size |
|---|--------|-----|------|
| 1 | A **round glass search button** in the top row, with a magnifier drawn by us | tvOS 26's top-right search button; Google's system-bar widget; the MiTV remote has no `KEYCODE_SEARCH` and its ASSIST key is swallowed, so search is unreachable today | S |
| 2 | The button opens search **with the keyboard already up** | WWDC26: the button "immediately engages search, and brings up the keyboard" | S |
| 3 | The field's text at **30 sp** (was 19 sp), the hint at caption size | WWDC20: tvOS increased the field text size so you can see what you typed | S |
| 4 | Our own **magnifier glyph** instead of `android.R.drawable.ic_menu_search` | One look across every TV; WWDC26: keep the symbol, make it ours | S |
| 5 | A **mic button** beside the field that fires `ACTION_ASSIST` with `search_type=1`, and **falls back to the keyboard** when nothing resolves | Google's orb; our fallback is the part Google got wrong | M |
| 6 | **The result cards are built once and re-bound**, not rebuilt per keystroke | Today every character typed allocates 12 × (card + icon + label). This is the one real resource cost in the search | M |
| 7 | The results are the **same 5:3 cards as the home screen** | Spatial consistency: the thing you find in search is the thing you saw at home | done |
| 8 | An **empty state** with a sentence: "No apps match …" | Google's error catalogue: never an empty box | S |
| 9 | **"This TV has no keyboard"** when no IME is enabled, and the D-pad browses the results instead | AGENTS.md: say what a device cannot do. Never a focusable field that swallows the remote | S |
| 10 | The screen **materializes**: scale and opacity spring in, and leave the same way | apple-design: materialize, don't just fade; enter and exit on the same path | S |
| 11 | The glass **catches the light on focus** (a glow inside the material, not a new shader) | Google's `ArgbEvaluator` tint; tvOS glass brightens under focus | S |
| 12 | The top-row buttons are **circles**, not pills | Google's `Outline.setOval`; tvOS 26's circular glass buttons | S |

### 9.4 Deliberately not built

| Not building | Why |
|---|---|
| Our own on-screen keyboard | An IME is a large feature. The TV has one (its settings app is the AOSP TV one); the runtime check in #9 covers the case where it does not. |
| Speech recognition | A new dependency and a network round trip. Both forbidden. Voice is a **delegation** to the TV's own assistant, exactly as Google does it. |
| Search history, suggestions, network results | Forbidden. The search is the names of the apps on this TV, and nothing else. |
| A categories tab (Apple TV's Search Tab) | There are no content categories on this TV. It would be an empty tab — a fake feature. |
| The "slide in to fill the screen" morph | It buys little on one row of cards, and it fights the fixed grid. |
| A predictive suggestion strip | It needs a corpus we do not have and may not ship. |

### 9.5 The rules this screen keeps

- **Local only.** `AppSearch` reads app names off the device. No network, no
  history, nothing sent anywhere.
- **No background work.** No thread, no timer, no service, no alarm. The
  screen is a view hierarchy that exists while the user is looking at it.
- **Few allocations.** The twelve result cards are made once. The glyphs are
  two small bitmaps. Nothing is decoded, blurred or filtered per frame.
- **Springs only.** `Motion` with damping 1.0 and response 0.3–0.4.
- **Honest about the device.** No keyboard, no assistant: both are said out
  loud, and the screen still works with the D-pad.

## 10. Session: the edge cases

Every item of the §4 checklist was walked through the code, one by one, with
CodeGraph for the map and the source for the truth. What was broken is fixed
here; what was already right was left alone.

### H1. The LAN file server could be killed from the network
- A request line of nothing but spaces splits into **no parts at all**, and
  `parts[0]` took the whole app down. Anything on the network can send one.
- A broken `%` escape made `URLDecoder` throw, and the throw was not caught.
- A request line had no length cap, and the socket timeout only bounds a read
  that *blocks*: a client that kept sending grew the buffer until the memory
  ran out.
- Every link to a folder was dead: the index linked `sub/`, and `sub/` was
  turned into `sub/index.html`, which does not exist. Now a folder with an
  index of its own is served as that page and every other folder is listed.
- The client threads were not daemons, so a client that hung on kept the
  process alive.

The request parsing is now three small pure methods, and `FileServerTest`
covers them: the path, the query, the empty line, the broken escape and the
endless line.

### H2. The screen saver could spin on a broken scene
- A scene that would not decode left the clock at zero, so **every frame**
  decoded a full-screen picture again, 30 times a second, for ever. A
  failed decode now waits for the next turn of the wheel.
- `OutOfMemoryError` was not caught on that path, so a TV with little memory
  lost the app. The wallpaper path already caught it; the saver does now.
- **"Previous" went forwards.** `previousScene()` called the same code as
  `nextScene()`; the pan went the right way but the picture went the wrong
  way. Both the home saver and the dream had it.
- The overlay named a scene that was never loaded.

### H3. A TV with no web engine took the app down
`new WebView(this)` was unguarded, and `android.webkit.WebView` is
framework code, so there is no way to ask for it in advance: the only probe
is to make one. The browser now says *"This TV has no web browser"* and
names what is and is not affected.

### H4. The app manager could crash on a package with no label
`loadLabel()` returns null for a package with no label resource, and
`applicationInfo` can be null as well. Both were dereferenced on the thread
that loads the list, where an exception kills the whole process.

### H5. One package the system would not answer for cost the user every app
`getLeanbackLaunchIntentForPackage()` and `getLaunchIntentForPackage()` were
outside every try, so one bad package emptied the whole grid. The same was
true of `getLaunchIntentForPackage()` in `setHidden()` and of
`getComponentEnabledSetting()` in the app manager.

### H6. Hiding or uninstalling an app left it on the home screen
The home grid kept its cached list and its epoch never moved, so the app was
still there until the process was restarted. The app manager now drops the
cache and moves the epoch, and the grid is rebuilt on the way back.

### H7. An uninstall was never reported
`REQUEST_DELETE_PACKAGES` was not declared, so the system refused the
request while Sonora said "Confirm to uninstall X". The permission is
declared, and the result is read back when the app comes back: the user is
told whether the app was uninstalled or not.

### H8. With no apps at all, there was no way into Settings
The empty state replaced the whole grid, including the Settings tile. The
reason is shown *and* the tile is there.

### H9. The focus was lost on the way back
Popping a screen threw the focused row away with it, so the screen
underneath came back with nothing focused: DOWN worked, UP did not.

### H10. The setup guide threw the user out of the activity
It was the only panel that replaced the whole stack, so BACK from it left
the tools activity instead of going back.

### H11. Smaller ones
- The picker opened with nothing ticked and nothing focused when the value
  was not on the list (a wallpaper that is a photo is stored as -1).
- The wallpaper picker opened on the first scene even when a photo was set,
  and OK replaced the photo with it.
- The process list was rebuilt on the screen *underneath* it when the user
  pressed BACK while it was being read.
- A file was opened as `*/*`, so the receiving app had no idea what it was.
- The app's own folder was `/data/sonora`, which the app cannot write to,
  and the file manager told the user it could.
- The long-press menu opened apps without saying so when the launch failed.
- The ad blocker cut the port before the userinfo, so `user@ads.example`
  was never blocked, and it never cut the query string.
- The address bar's keyboard had no "Go", so it did nothing.
- A page could read the TV's files on Android 6 to 9.
- A spring with a target that is not a number never rests, and would have
  kept the frame clock awake for good.
- A re-target threw away a callback that was waiting, which would have left
  an overlay on the screen for good.
- The margins were physical, so they did not mirror in a right-to-left
  language, and the chevron pointed the wrong way.
- The dream showed the place from the first frame even when the setting says
  "on input".
- The app list was read on the main thread by the favourites screen and by
  the hidden-apps picker, which is long enough to be an ANR on a slow stick.
- The artwork of every app is now dropped when the system asks for memory,
  and the old pixels are gone before the new ones are made.


## 11. Session: the 1:1 tvOS pass

Reference re-checked against Apple's tvOS 26 Home Screen press image (1960x1102)
and the HIG pages for Designing for tvOS, Layout, Focus and selection, Motion,
Materials, Typography, Color, Accessibility, Top Shelf, Remotes, Settings and
Writing. The device was reachable again at `192.168.68.108:5555`.

### I1. The dock was drawing over its own icons
`dockBar` had `elevation = 12dp`, so Android painted the glass *after* the app
cards. Every icon sat under a white sheet when the old focus light turned the
bar on. The bar now has no elevation: it is the surface the cards sit on, not a
layer above them. The white "focus" state of the whole dock was removed with
it; tvOS keeps the dock dark and lights the focused card, not the bar.

### I2. The top-right corner matches the reference at rest
tvOS 26 has one capsule there: time plus profile. Sonora had three controls
(search, quick settings, clock) visible all the time. The two extra controls
now fade in when the clock takes the focus and fade out when the grid does.
They stay one UP press away, and the screen at rest is the reference.

### I3. The default subtitle was an instruction, not content
"Hold OK for options" sat under the app name on every screen. It is gone.
Contextual hints remain for arranging, pinned apps, the Settings tile and the
empty state, and the subtitle is `GONE` when there is nothing to say.

### I4. Rows below the fold measured to zero and could not take focus
The dock strip is deliberately shorter than the list so the next row peeks
out. A `WRAP_CONTENT` grid inside that fixed-height strip was measured against
the strip, so the second row and everything below it got height 0. The D-pad
could never enter row two: DOWN did nothing. The grid now gets its full height
explicitly (`barPad + rows * rowStride`) and the strip clips it. Verified on
the TV: DOWN moves the focus into the second row.

### I5. The middle of a row was a D-pad dead end
LEFT at column 0 and RIGHT at the last column returned no target. `Grid.moveTarget`
is now pure and tested: LEFT wraps to the previous row's last tile, RIGHT wraps
to the next row's first tile. The two real ends (first tile LEFT, last tile
RIGHT) are still ends. `HomeActivity` uses the pure helper instead of its own
copy of the maths.

### I6. The focused surface is white, not a grey gradient
HIG's focused control is an opaque white surface with dark ink. The shared lit
material now resolves to near-white (0.99 -> 0.93) instead of 0.95 -> 0.66, so
settings rows, pickers and the pills read like tvOS controls.

### I7. Smaller HIG fixes
- `HomeActivity` has `stateNotNeeded="true"`, like Google's launcher.
- The clock's content description now reads the time (and date when shown)
  before "Settings", so TalkBack says something useful.
- The row focus scale, sheen, lean and dock slide remain springs, and reduced
  motion still degrades them to a static state.

### What is still deliberately unlike tvOS
- The top-left title is the focused app's name, because a launcher has no
  editorial Top Shelf content to show instead.
- The long-press menu and the settings sub-pages are Sonora's own patterns;
  tvOS does not define a launcher context menu or a pixel spec for its Settings
  app. They use the same type, glass and focus language.
- The grid keeps the measured 2.8% side margin from the tvOS 26 render rather
  than the general 80pt HIG safe area, because the measured home screen is the
  thing being replicated and 80dp would drop the six-column layout on a 960dp
  Android TV canvas.

### I8. The hint footer was noise, and it clipped the settings list
Every stack screen carried a bottom line ("OK to select · BACK to go back").
tvOS shows no such footer, and the HIG writing guidance says not to explain
standard remote behaviour. It also took the height the eight settings rows
needed: the last row sat half under it. The footer is gone and the index now
fits without clipping. Verified on the TV at 960x540dp.

### I9. Cancelling a picker lost the focus
Removing the focused picker makes Android focus-search the screen behind it.
That search found a row on the screen *under* the top one, so the code's
"something already has focus, leave it" check dropped the user on the wrong
screen with no visible focus. The activity now records the stack depth when a
picker opens: a cancel restores the row that opened it, while a pick that
pushes a screen keeps that screen's own focus. Verified on the TV: BACK from
the clock picker returns to the Clock row, picking a value returns to the
rebuilt row, and BACK from the Clock page returns to the Clock category.

## 12. Session: commit the third round, then clean the repo

The third requirements round had been sitting uncommitted since 2026-11-04,
plus a stale `files-rename-thumbs` worktree holding two fixes that were never
merged back (thumbnail centring in `ItemCell`, the rename selection fix in
`Sheets`). The worktree's three differing files were copied into the main
tree, the worktree and its branch were removed (backup tarball was kept in
/tmp at the time), and `.claude/` was added to `.gitignore`.

The work was then committed in three pieces so each builds on its own:

- `decbc87` — docs under `docs/`, `AGENTS.md`/`CLAUDE.md`, `scripts/`.
- `ff48bfc` — `files-core` + `files` modules, root plugin versions, the
  `perf` build type, storage permissions. Verified to build with the old
  launcher code in a scratch worktree.
- `4d903cf` — the whole third round: `Theme` dark/light, `DeviceTier`,
  `SearchKeys`/`SearchKeyboard`, `ControlCenter`, `GlassLens`, `BackdropView`,
  `PressRow`, `RowDrawable`, `Glyph`, `AerialsView`, `SceneChoice`, pickers
  on the Settings row language, and Files reached through `files-core`.

A cleanup pass followed on 2026-11-05:

- `SonoraProvider` deleted: nothing called `uri()` any more — sharing goes
  through `ShareActivity`/`FileServer` and `FilesProvider`. Its manifest
  provider entry and its two `EdgeCasesTest` cases went with it.
- `banner.png` and `ic_launcher.png` deleted: the manifest uses
  `sonora_banner`/`sonora_mark`; they were leftovers.
- `strings.xml` cut to the three strings anything references
  (`app_name`, `dream_name`, `about_text`); the rest were dead resources.
- Root `build/` (a stale Gradle report) removed.
- `README.md` and `GLOSSARY.md` added; `docs/plans/REQUIREMENTS-2026-11-04.md`
  gained a status table; this file remains the session log.

Unit tests and debug/release builds for all three modules pass after the
cleanup.

## 13. Session: launcher benchmark, Sonora vs stock Google TV

A controlled A/B on the owner's Mi TV Stick, recorded in
`docs/perf/2026-11-05.md`. New `performance-optimization` worktree at
`../aerial-performance`; the `app-perf` build from `2c6e17a` was installed over
the older APK for the Sonora run.

Protocol, identical for both launchers: reboot, 60 s settle, system
`/proc/meminfo` + launcher `dumpsys meminfo`, then force-stop, cold start,
`gfxinfo reset`, the same 12-key DPAD walk. Two clean Google TV runs, one clean
Sonora run (raw dumps kept under `docs/perf/2026-11-05/`).

Results: Sonora ~40 MB PSS, ~897 MiB MemAvailable, 2.7% deadline misses; the
stock launcher ~181 MB idle growing to ~275–288 MB after navigation, ~803–823
MiB available, 6–8% misses. Against the Oct 31 pre-debloat record (~688 MiB
available, ~298 MB launcher PSS, ~250 MB swap), the debloat alone is worth
~115–135 MiB and Sonora roughly doubles that headroom again.

Two things worth remembering:

- `pm disable-user com.google.android.tvlauncher` no longer survives a reboot
  once `set-home-activity` had pointed HOME at it — the package comes back
  `enabled=1` during boot and steals focus despite the HOME role and preferred
  activity both naming Sonora. Not yet isolated (suspects: `tvrecommendations`,
  `katniss`, or leftover preferred-activity state). The disable holds until the
  next reboot; the TV is left on Sonora's home screen.
- The stick's LAN IP moved to `192.168.68.104` (mDNS `_adb._tcp`); `.108` still
  pings but refuses adb. `scripts/tv.sh` honours `SONORA_TV`.

## 14. Session: focus glass and the screensaver rewrite

Continued on the `performance-optimization` worktree (`aerial-performance`).
The focus-glass work from `/home/mitun/aerial` was committed there as `22776c7`
and cherry-picked here as `fc7e555`: `ui/FocusGlassView` carries the rim and
shadow as one hardware-layered slab that springs between cards, `AppIconView`
dropped its per-card shadow/lift drawing, and `HomeActivity` positions the slab
by accumulating translations up to the root.

Then the biggest remaining invalidation came out: `ScreensaverView` is now a
`FrameLayout` of two `SceneView` children plus a static `OverlayView`
(`c4bfde4`). The Ken Burns drift is `setScale`/`setTranslationX` on the scene
views — RenderNode properties, no redraw — the crossfade is view alpha, and
the scrim/captions redraw only on scene or overlay changes. Same 33/50 ms
cadence, same 1.04→1.10 zoom, same 2200 ms fade, same decode-on-background-
thread flow; the public API is unchanged. One deliberate visual fix: the old
canvas paints never set a text colour (default black on a dark scrim); the
overlay now draws white title and 72% white credit.

Verified: `:app:testDebugUnitTest`, `:files-core:testDebugUnitTest`,
`:app:assembleDebug`, `:app:assembleRelease`, `:files:assembleRelease` all
pass. Not verified: anything on the TV — focus-glass alignment, the fade and
drift on-device, and the LOW-tier frame cost are all still owed a look when TV
access is allowed again.

Still open, in order: FrameMetrics self-tuning (`DeviceTier.degrade` on a
rolling jank window), porting cleanyourtv's `bench.py`/`perf-ab.md` into
`scripts/`, then the Files/browser suite pass.

## 15. Session: post-merge benchmark on master

The `performance-optimization` worktree was merged into `master` (`2af7e57`) and the
worktree/branch removed; repo is single-branch again at `aerial`. The pending
docs-sync output (benchmark write-up, raw dumps, GLOSSARY term) was committed as
`f6242b0`.

Re-ran the 2026-11-05 protocol against the merged code (`2af7e57`, `app-perf.apk`
reinstalled, `compile -m speed`): two clean Sonora runs, recorded in
`docs/perf/2026-11-05.md` under "Post-rewrite Sonora runs". Deadline misses
2.7% → 0.9%, p99 48 → 18–28 ms, zero slow UI-thread events; MemAvailable ~944 MiB
at idle, swap ~11 MB. Idle PSS ~43.9 MB, ~2.5 MB above the pre-rewrite run —
plausibly the focus-glass layer.

Verified: `:app:testDebugUnitTest`, `:app:assemblePerf`, two on-device runs.
Not verified: the screensaver itself — the walk never shows the dream, so the
`c4bfde4` rewrite is untested on-device (needs an idle-dream measurement or a look).
TV left on Sonora's home screen.

Still open, in order: cheap wins (text layout first), a dream-time frame check for
the screensaver, `tvlauncher` re-enablement quirk, then the Files/browser pass.

## 16. Session: frame-phase breakdown and dream verification

Two measurements on `master` @ `7ce7058`-era build, same TV:

- `framestats` after the DPAD walk: CPU-side frame phases are ~0.1–3 ms; GPU
  completion trails swap ~15 ms median. Remaining cost is fill/composition, so
  the planned "cheap wins" (text layout etc.) were re-ordered — overdraw audit is
  the only lever left on the frame path.
- The TV dozed to the system dream on its own timer. Found
  `screensaver_components` still naming the removed `com.mitun.aerial` package —
  the dream had been unreachable since the rename. Set it to
  `com.mitun.sonora/.dream.SonoraDreamService`; dream ran, 902 frames/30 s,
  zero jank, p99 5 ms. `ScreensaverView` rewrite verified on-device. TV left on
  Sonora's home screen.

Still open: overdraw audit, `tvlauncher` re-enablement quirk, Files/browser pass,
FrameMetrics self-tuning, port `bench.py`/`perf-ab.md` into `scripts/`.

## 17. Session: debloat tool in settings

Added a Debloat screen under Settings → Apps: a curated list of common Android TV
bloatware packages with On/Off toggles. Turning an app off uses
`PackageActions.setPackageEnabled` (disable-user), then invalidates
`AppRepository`/`AppManager` and bumps the apps epoch so the home grid drops it.
Package state is read on a background thread and the screen fills in via
`refreshSettings`, same pattern as Favourites. Warnings are shown before the two
risky rows (Google TV Launcher, Google Play Services).

The first cut was a custom `DebloatView` pushed full-bleed; it did not compile
(`SettingRow` is a row model, not a `View`) and its `Debloat` page was never
linked into the settings index. Replaced with `DebloatPanel`, a row-provider in
the `AppPanel`/`DebugPanel` style, reached from the Apps page.

Verified: `:app:assembleDebug`, `:app:testDebugUnitTest`. Not verified on-device.
TV untouched.

## 18. Session: Google TV parity batch, part 1

Working from `docs/research/GOOGLE-TV-TEARDOWN.md` §6 (Tier 1 + Tier 2 approved).

Done and building:

- Quick settings gained Keyboard, Date & time and Language rows. The three
  `com.android.tv.settings` activities were verified present on the TV
  (`inputmethod.KeyboardActivity`, `system.DateTimeActivity`,
  `system.LanguageActivity`); each row falls back to generic Settings.
- The screen saver overlay now draws a large clock above the place name,
  respecting the 12/24-hour setting. It invalidates once a minute, only while
  the overlay is showing — the drift clock is untouched.
- A round glass search pill (`GlyphPillView`, new) sits beside the clock on the
  top shelf. This remote has no search key, so the pill is the visible way in.
  UP from the first row now lands on the pill; UP again opens Control Center;
  DOWN returns to the grid. The pill is fed by the same glass bake as the dock
  and clock (frost and bent-lens paths both updated).
- `android.intent.action.ALL_APPS` is handled: the grid is the all-apps screen,
  so the intent scrolls it to the top.

Already done before this session (verified in code): the long-press menu order
(Open · Move · Favourites · Info · Uninstall) and uninstall via
`PackageActions.requestUninstall`.

Skipped: "Previews on/off" — Sonora has no content previews to toggle; the hero
is a still and Reduce motion already covers animation. A fake toggle would
violate the no-fake-features rule.

Still open: ADD_APP_LINK approval dialog (needs the extras contract from the
launcher dex), Apps/Games order split, icon polish pass, setup-guide clarity.

Verified: `:app:assembleDebug`, `:app:testDebugUnitTest`. Not verified on-device.

## 19. Session: icon polish pass

Redrew every hand-drawn mark toward the tvOS 26 look: `ui/Glyph` (17 marks) and
`files-core/Marks` (14 marks), all still authored as `Path` geometry on a 24-unit
grid — no Apple assets, per the rules.

Notable fixes, not just nudges:

- STAR was a ten-point burst; it is now a five-point star.
- HOME was a monitor-with-windows shape; it is now a house outline with a door.
- DELETE was a skewed bin with an X; it is now a proper trash can (lid, handle,
  body, two ribs).
- WIFI arcs were elliptical and off-centre; they are now three concentric circular
  arcs sharing the dot's centre.
- SOUND waves were flat ellipses; they are now concentric `)` arcs.
- GEAR teeth were 12 sharp notches; now 8 rounded teeth from a smooth sampled
  radius profile.
- APPS/GRID squares unified (same geometry in both apps); LIST/MORE/DRIVE dots are
  now solid discs (radius 0.9 with the 1.8 stroke fills them).
- MOVIES is now a clapperboard (striped slab + body) instead of a divided frame.
- DISPLAY gained a proper stand; MIC gained its base line; HIDDEN's pupil and eye
  proportions corrected.

`FileIcon` (the file-type artwork: folder gradient, page) was left alone — it is
not an Apple-style mark.

Verified: `:app` + `:files` compile, `:app:testDebugUnitTest` +
`:files-core:testDebugUnitTest` pass, `codegraph sync` run. Geometry bounds-checked
against the 24-grid in a matplotlib harness. Not seen on the TV — icon look is
unverified on-device; check Control Center, Settings rows, Files sidebar/toolbar.

## 20. Session: stale docs cleanup

Removed the Google TV reverse-engineering trail now that its findings are in
the code: `docs/handoffs/` (both dated handoffs — the live log is `PLAN.md`),
`docs/research/GOOGLE-TV-TEARDOWN.md` and `docs/FEATURE-COMPARISON.md`.
`docs/research/` keeps the tvOS design and performance notes, which are still
referenced. `README.md`, `AGENTS.md` and the `docs-sync` skill no longer point
at handoffs; `PLAN.md`/`REQUIREMENTS` keep their historical mentions.

Verified: no live references remain outside the dated plan/requirements files.

## 21. Session: settings screens unified on the tvOS split layout

Audit of every screen against tvOS 26. The home grid, Control Center, app
switcher, search and the settings index were already measured; the stragglers
were the last old-style screens.

- SetupWizard, the App detail screen, DebugPanel (Device, Processes, Battery,
  Network, Self check) and SetupGuide now use `pushSettings`/`refreshSettings`
  — the split layout with the centred title, the list on the right and the
  mark on the left. Subtitles became NOTE rows, which the split layout draws
  under the mark.
- Dead code removed: the unreachable "tools" screen (`ToolsPanel.show` and its
  `ToolActivity` branch — Settings > Tools is the only tools page now) and the
  old-style `ScreenStack.show`/`push`/`showOrPush`/`refresh` row-list API,
  which nothing calls any more. Custom-content screens (app list, log,
  aerials, About) keep the standard column chrome.
- Edge case: `ToolActivity.onResume` now rebuilds the settings index and any
  settings page, so values that a system screen can change (Wi-Fi state,
  screen-saver selection, home-screen role, developer options) are fresh on
  the way back instead of stale. Custom screens are skipped on purpose.
- AppPanel: the filter tabs got the same RowDrawable focus treatment as
  settings rows (they only changed text colour before), and row/tab text now
  uses the `Type` scale instead of hardcoded sizes.
- AppSwitcherView: closing the focused card with a long OK moves focus to a
  neighbour first, so it is not left nowhere.
- QuickPanel: removed a duplicated javadoc block.

Verified: `:app:assembleDebug`, `:app:testDebugUnitTest`, `codegraph sync`.
Not verified on-device — the wizard, app detail, Device/Battery/Network/Self
check, Setup guide and the app-manager tabs want a look on the TV.
