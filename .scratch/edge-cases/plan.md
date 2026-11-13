# Edge-case pass — exhaustive plan

Status: done — fixes landed (PLAN session 25); the "noted, not fixed" items stay as the record. TV walk still owed.

Five-area audit of the whole codebase against the standing rules (zero deps,
minSdk 23, 1 GB target, D-pad, honest UI, no background work). Findings below
are verbatim from the audit; the fix plan follows.

Fix policy: **every P0 and P1 gets fixed.** P2s are fixed when the fix is a
one-liner (guard, clamp, volatile, ellipsize); purely latent or cosmetic P2s
are listed as "noted, not fixed" at the end with the reason.

---

## A. browser/ — 33 findings

### Fix

- [P0] `BrowserActivity.java:179-202` — no `shouldOverrideUrlLoading`: `intent:`/
  `tel:`/custom-scheme links can throw SecurityException out of the WebView or
  hijack the user into another app. → override; load http/https/about in place,
  toast honest message for the rest.
- [P1] `BrowserActivity.java:181-187` — blocked main-frame request renders a
  silent blank page (the adlist contains whole domains: twitter.com,
  facebook.com, spotify.com). → if `isForMainFrame() && isBlocked`, load an
  inline "Blocked by the ad blocker" page.
- [P1] `res/xml/network_security_config.xml:3-7` + `BrowserActivity.java:271-279` —
  `normalise()` emits `http://` for IPs/host:port but cleartext is only allowed
  to localhost → every typed `http://` LAN URL dies ERR_CLEARTEXT (incl.
  Sonora's own ShareActivity URLs). → add `10.0.0.0/8, 172.16.0.0/12,
  192.168.0.0/16`-style private-range cleartext domain configs (that's the use
  case), keep everything else https-default.
- [P1] `ToolsPanel.java:45-57` + `SettingsPanel.java:458-459` — no way to turn
  the browser OFF once enabled; row shows "On"/"Off" but only launches. →
  enabled row hosts a choose("Browser", {Open, Turn off}) →
  `setBrowserEnabled(false)`.
- [P1] `BrowserActivity.java:363-376` — BACK while IME is up navigates page
  history / exits instead of dismissing the keyboard. → if address field
  focused, `hideKeyboard()` + consume first.
- [P1] `BrowserActivity.java:190-201` — error state is a blank page + status
  line that `onPageFinished` clears. → keep the error text when the last
  main-frame load failed (one boolean); name the reason from `getErrorCode()`.
- [P1] `BrowserActivity.java:179` — no `WebChromeClient`: window.open, file
  input, JS dialogs, video fullscreen all silently dead. → tiny client that
  toasts an honest "can't on this TV" for dead ends; `onProgressChanged` →
  status.
- [P1] `AndroidManifest.xml:100-105` — `standard` launchMode → every Tools→
  Browser launch stacks a new WebView; fastest path to OOM on 1 GB. →
  `singleTask` + `onNewIntent`.
- [P1] `BrowserActivity.java:86` — `loadUrl("about:blank")` adds a dead history
  entry before the start page → BACK lands on blank. → delete the line.
- [P2-fix] `BrowserActivity.java:85-90,112-121` — `loadUrl`/`restoreState`
  outside the try; `web.destroy()` in catch can itself throw. → extend try,
  wrap destroy in its own.
- [P2-fix] `BrowserActivity.java:89` — `restoreState` return ignored → blank
  page on failed restore. → `if (restoreState(...) == null) load START_PAGE`.
- [P2-fix] `BrowserActivity.java:190-193` — `onPageFinished` rewrites the
  address bar while the user is typing / shows "about:blank". → skip when
  field focused or url is about:/data:.
- [P2-fix] `BrowserActivity.java:386-400` — `onPause` without `pauseTimers()`
  → audio/JS can keep running under the launcher. → pause/resume timers with
  the activity (single WebView in process → safe).
- [P2-fix] `BrowserActivity.java:331-333` — "Clear history" leaves
  cookies/storage; toast overpromises. → also clear CookieManager+WebStorage,
  or reword.
- [P2-fix] `AdBlock.java:28,54-60,67-68` — `enabled` read on WebView IO
  threads, written on main → `volatile`.
- [P2-fix] `AdBlock.java:34-52` — missing/corrupt adlist → enabled stays true
  blocking nothing → set `enabled=false` when the list ends empty.
- [P2-fix] `AdBlock.java:44-48` — hosts-style lines stored whole (never match);
  bare `|` → empty fragment blocks EVERYTHING. → last whitespace token for
  host lines; skip empty fragments.
- [P2-fix] `AdBlock.java:95-120` — `hostOf`: IPv6 `[` , trailing-dot FQDN never
  matches. → strip `[]` and trailing `.`.
- [P2-fix] `res/raw/adlist.txt:125` — `/banner_ad` missing `|` → dead host
  entry; ~8 duplicated hosts. → add `|`, dedupe.
- [P2-fix] `BrowserActivity.java:65-67` — browserEnabled checked only at call
  site → check in `onCreate` too (defense for a future off-path).
- [P2-fix] `BrowserActivity.java:9` — dead `import android.net.Uri`. → delete.
- [P2-fix] `BrowserActivity.java:158-164` — no loading state → `status` =
  "Loading…" in `onPageStarted`.
- [P2-fix] `AdBlock` — pure logic untested; make `hostOf`/`isBlocked`
  package-visible and unit-test subdomain walk, userinfo, IPv6, fragments,
  case.
- [P2-fix] `BrowserActivity.java:271-273` — typed `tel:`/`mailto:`/`data:` →
  `http://tel:…` garbage → honest "no app for that" toast.

### Noted, not fixed
- Renderer-death callback (API 26+ only; below that nothing exists to call) —
  document LOW answer; skip `onRenderProcessGone` wiring unless seen on device.
- About-menu item reachable via `default:` in switch — reorder trap, noted.
- Bookmark polish (no titles/delete/cap), Back/Forward silent no-ops —
  no fake-feature violation; list stays.
- `normalise` `x://` pass-through incl. `javascript:` — self-XSS only; bookmark
  guard already requires http. Accept.
- Immersive not reapplied on window focus — STICKY covers most cases.
- No `DeviceTier`/`onTrimMemory` answer for the WebView — document only.
- Adlist blocks whole products vs "ad hosts" wording — reword toast only if
  touched anyway.
- `about:blank`/`data:` in history, `configChanges` missing fontScale/locale,
  D-pad focus ring on WebView — platform limits, documented.
- Verified already-safe: missing WebView catch screen, no SSL-proceed handler,
  `setAllowFileAccess(false)`, destroy order, web!=null guards, manifest
  exported=false, gated entry, subdomain-walk exactness, ≤23 APIs.

## B. files-core/ + files/ — 46 findings

### Fix

- [P1] `FileOps.java:160-161` — paste TOCTOU: `freeName` picks a free name then
  `FileOutputStream` truncates whatever appeared in between → data loss. →
  `Files.newOutputStream(path, CREATE_NEW)`, re-pick on failure.
- [P1] `FileOps.java:160` — FIFO/socket in a copied tree blocks `FileInputStream`
  forever; cancel can't interrupt the open. → `if (!source.isFile())` skip with
  honest message (mirrors symlink treatment).
- [P1] `FileOps.java:153-156` — `all &= copyTo(...)` evaluates every sibling
  after a failure → full-disk still writes the rest. → break on first failure.
- [P1] `FilesProvider.java:42` — blacklist guard only covers *own* data dir;
  symlink on shared storage → resolves into other apps' private dirs. →
  whitelist: canonical path must be under a known place/external storage.
- [P1] `FilesActivity.java:925-953,681` — `exists()`/`mkdir()`/`renameTo()` on
  the UI thread; dead USB mount → ANR. → run inside the existing job/lister
  path.
- [P1] `Sheets.java:269,298-301` + `FilesActivity.java:919,938` — input sheet
  has no `onCancel` → BACK/Cancel strands focus on sidebar row 0. → add
  `onCancel` param; callers pass refocus.
- [P1] `FilesActivity.java:883-894,126-146` — `toggleView`/`toggleAppearance`
  `recreate()` drops folder, clipboard, sheets, `lastSelected`; `onCreate`
  ignores `savedInstanceState`. → rebuild only the toolbar glyph instead of
  recreate (whole activity rebuilt to swap one icon).
- [P1] `FilesActivity.java:1061` — after delete/move, `load(null)` + stale
  `lastSelected` → focus jumps to item 0. → pass the next sibling's name as
  `select` through the job completion.
- [P1] `FilesActivity.java:900-911` — Get Info `sizeOf` on the shared lister
  thread, uncancellable → 1 TB tree freezes the app. → own thread + cancel
  wired to sheet dismiss.
- [P1] `FilesActivity.java:1044-1049` — `runJob` catches only
  `RuntimeException`; an Error kills the worker before the UI callback →
  progress sheet stuck forever. → `finally` posts completion.
- [P2-fix] `QuickLook.java:162-184` — swallows every key incl. volume. → return
  false for unhandled.
- [P2-fix] `FilesActivity.java:430-431` — `INVALID_POSITION`(-1)+1 == 0 →
  `(0)%columns==0` eats DPAD_RIGHT. → `if (position < 0) return false`.
- [P2-fix] `FilesActivity.java:828` — `entries.indexOf(entry)` identity → stale
  entry after background reload → silent no-op Open. → compare by `file.getPath()`.
- [P2-fix] `FilesActivity.java:605-610,1147` — `getItemAtPosition` unchecked
  upper bound → potential IOOBE on stale position. → bounds-check in `getItem`.
- [P2-fix] `FilesProvider.java:65-68` — `getType` mimes the full path, not the
  filename. → `mimeOf(new File(path).getName())`.
- [P2-fix] `FilesProvider.java:61` — `openFile` can return an fd for a
  directory. → reject `!file.isFile()`.
- [P2-fix] `FilesProvider.java:71-83` — `query` reports a name+0 bytes for a
  nonexistent file → return null.
- [P2-fix] `FilesActivity.java:142-145,1093-1108` — `onCreate`+`onResume` both
  open `places.get(0)` → double listing at boot. → skip resume reload when the
  folder is already open and unchanged.
- [P2-fix] `FilesActivity.java:596,450-453` — dimmed Back button still
  focusable/clickable at root. → `setEnabled(false)` there.
- [P2-fix] `FilesActivity.java:321-327` — `columnLabel` wraps at font scale
  2.0. → `singleLine` + ellipsize.
- [P2-fix] `ItemCell.java:51-53` — cell text sized in dp not sp → ignores font
  scale. → sp.
- [P2-fix] `ItemCell.java:114-115,186-190` — unclipped row columns; name column
  end 0.50 vs header 0.52. → ellipsize each column to its width; fix the
  constant.
- [P2-fix] `FileLogic.java:111-132` — nonstandard MIMEs (3gp, mpg, wmv, flv,
  wma, opus). → standard ones (3gpp, mpeg, x-ms-wmv, x-flv, x-ms-wma, ogg).
- [P2-fix] `FileLogic.java:249-263` — `nameProblem` allows control chars/
  newlines → tofu labels. → reject `c < 0x20 || c == 0x7F`.
- [P2-fix] `FilesActivity.java:944-949` — case-only rename blocked on
  case-insensitive FS. → allow when `getCanonicalPath` equals source.
- [P2-fix] `Places.java:55-74` — internal-storage dedup by string compare can
  double-list on `/sdcard` vs `/storage/emulated/0`. → canonical-path compare.
- [P2-fix] `FilesActivity.java:985-988` — `ellipsized` substring can split a
  surrogate pair. → BreakIterator or clamp to ≤ BMP boundary.
- [P2-fix] `Sheets.java:214-216` — menuRow label no ellipsize. → END.
- [P2-fix] `QuickLook.java:82-107` — previous photo stays under the new
  caption while next decodes. → clear `image` on step.
- [P2-fix] `FilesActivity.java:796-798` — `kindName().toLowerCase()` default
  locale → Turkish-i bug. → `Locale.US` or ROOT.
- [P2-fix] `Sheets.java:389-391` — progress bar min-blob shows sliver at 0%.
  → draw nothing below a threshold.

### Noted, not fixed
- `onSaveInstanceState` absent (process death → first place) — folded into the
  recreate() fix where free; full state restore is a bigger change, noted.
- `lastSelected` unbounded growth — strings only, noted.
- `lister.execute` unguarded `RejectedExecutionException` — unreachable post-
  shutdown, noted.
- Sheet post-focus race (one eaten keypress), share-extra NPE on contract
  misuse, `place.label` null → "null" text — contract-only paths, noted.
- USB pull while foreground → stale list until resume — no receivers rule;
  every action degrades honestly. Accepted design.
- QuickLook arrows only walk images; runJob thread pins activity bounded;
  <640dp grid floor at 3 cols — noted.
- `dir404` EACCES→"no longer available" wording, stale `place` identity after
  `readPlaces`, share() contract, `sizeOf` skip-perm behavior — noted.
- Verified already-safe: listFiles-null handling everywhere, SecurityException
  paths, canonical `isInside` recursion guard, renameTo→copy→delete fallback,
  partial-batch reporting, cancel hygiene, symlink skip, provider grants+
  readonly, thumbnail bounds/OOM/LRU/trim, API≥24/30 guards, generation-ticket
  drops, honest error taxonomy.

## C. ui/ toolkit — 48 findings

### Fix

- [P1] `AppTileView.java:133-135` + `Motion.java:190-198` — re-focus during the
  label fade-out leaves a stale `setVisibility(INVISIBLE)` callback on the
  ALPHA track → focused label pops invisible. → conditional callback
  `if (!isFocused())`.
- [P1] `ScreenStack.java:431-432,283-292` — same mechanism one level up: push B
  over A, BACK mid-fade → A's INVISIBLE callback fires at settle → top screen
  invisible but still eats keys. → condition the callback on still-covered, or
  `Motion.set` the under view before retarget.
- [P1] `FocusGlassView.java:84-92` — two writers on the slab: springs write
  `posX/posY` (custom tracks) while scroll snap uses `Motion.set(TRANSLATION_X/Y)`
  — different track indices → in-flight spring keeps stamping the old position
  during scroll. → spring the real TRANSLATION_X/Y tracks; drop posX/posY.
- [P1] `HomeActivity.java:1267-1296,904-938,1563-1567,1341-1348` — overlays
  don't dismiss each other: app menu + search key → SearchOverlay over picker;
  close top → focus lands on grid under a still-attached modal → OK launches
  an invisible app. → dismiss other overlays before adding, mirroring
  `handleBack` ordering. (fixed in HomeActivity batch)
- [P1] `BackdropView.java:36-57` — constructor does `Ui.decodeUri` (two stream
  round trips) on the main thread on cache miss. → construct empty; hand the
  bitmap in via `setSoftened` from the existing decode thread.
- [P1] `AppIconView.java:152` — `TextUtils.ellipsize(entry.label…)` NPE on
  null label (every other consumer tolerates null). → `label == null ?
  packageName : label`.
- [P1] `HomeActivity.java:818-829,941-950` — `closeControlCenter`/
  `closeAppSwitcher` restore focus while the overlay is still fading → ~0.3 s
  of keys moving the grid under a visible panel. → restore inside the close
  callback like `closeSearch`/`dismissChoice` already do. (HomeActivity batch)
- [P1] `AppSwitcherView.java:55-68,141-155` — empty state has nothing
  focusable → D-pad moves the grid under the "No recent apps" dim; UP/DOWN
  can't reach the dismiss path. → focusable empty container so a press lands
  on `focusSearch`→dismiss.
- [P2-fix] `ScreensaverView.java:261-263` — OK toggles the clock overlay even
  in "Never" mode. → only toggle when pref == "on input".
- [P2-fix] `Motion.java:101-118,264-271` — `onSettled` that starts a new spring
  on the same view leaves it dead (`running` cleared mid-frame). → re-scan
  tracks after the callback loop before clearing `running`.
- [P2-fix] `Motion.java:101-102` + `Spring.java:96-101` — NaN target →
  `atRest` true → writes NaN into the view → invisible until rewritten. →
  reject non-finite targets in `spring()`.
- [P2-fix] `Motion.java:208-225` — `springCustom` slot outside 0–7 → AIOOBE.
  → clamp.
- [P2-fix] `Spring.java:58-64` — `dampingRatio <= 0` → oscillation to NaN
  flicker. → `Math.max(0.05f, …)`.
- [P2-fix] `Ui.java:210-212,236-238,252-254` — `reqWidth`/`maxDimension` ≤ 0 →
  `sample*2` overflow → `outWidth/0`. → `Math.max(1, …)`.
- [P2-fix] `Ui.java:164` — dream-component `contains(pkg)` substring match.
  → compare only the package token before `/`.
- [P2-fix] `Type.java:47-62` — unchecked `SIZES[style]` → AIOOBE on bad id.
  → clamp to BODY.
- [P2-fix] `SettingRow.java:109,137-143` — null title/value → NPE. → `""`
  defaults in factories.
- [P2-fix] `ScreenStack.java:305-325,359-375` — screen with zero focusable
  rows swallows all D-pad keys → dead end till BACK. → skip key intercept when
  `firstFocusable == null`.
- [P2-fix] `ScreensaverView.java:248-254` — corrupt huge pref → negative
  duration → decode attempt every frame. → clamp to a ceiling.
- [P2-fix] `ScreensaverView.java:444-448,317` — re-attach after detach →
  `RejectedExecutionException` on shut-down executor. → guard `isShutdown()`
  (AerialsView already does).
- [P2-fix] `ScreensaverView.java:361-363` — dead `wrap()` divides by count →
  AIOOBE at 0. → delete (no callers).
- [P2-fix] `ScreensaverView.java:177` — `onDraw` → `Scenes.all` →
  `PhotoLoader.photoPaths` → recursive fs scan on the main thread when the
  cache expires. → hold the resolved list; refresh on scene change.
- [P2-fix] `ScreensaverView.java:433-438` — wall-clock jump → negative elapsed
  → backwards drift. → `Math.max(0, elapsed)` or elapsedRealtime.
- [P2-fix] `GlassLens.java:53` — `width()/scale` with scale 0 → AIOOBE. →
  `Math.max(1, scale)`.
- [P2-fix] `GlassPillView.java:117-134` — `focusValue()` allocates a
  FloatProperty per focus change. → one field instance.
- [P2-fix] `ChoiceOverlay.java:86-93` — null element in options → blank
  focusable row. → skip null entries.
- [P2-fix] `ScreenStack.java:269-302` — `pop()` completion can fire
  `onEmpty()→finish()` after the stack repopulates. → generation counter like
  `glassGeneration`.

### Noted, not fixed
- `spring`/`cancel` off-main-thread (all callers on UI thread), `Spring.jumpTo`
  dead API, `ScreenStack.refresh` index-vs-identity focus restore,
  `SearchOverlay.Listener.onClosed` never invoked (dead API — remove when next
  touching the file), weak-ref pin of custom-track owners (bounded), screen
  margins on fold, RTL note column, `GlassDrawable.setColorFilter` no-op,
  `keyPaint` strokeWidth leak, `Ui.start` mutating caller intent,
  `SearchKeyboard` strokeWidth persistence, `HeroBackgroundView` per-draw
  Paint+RectF (rare path), backdrop single-entry cache, ControlCenter UP-only
  asymmetry, `Handler` in field off-main-thread, VALUE row maxwidth,
  `AppSwitcherView`/`ChoiceOverlay` null-arg NPEs (contract), queryView spaces
  (unreachable), `requestFocus` on detached view — all latent or noted.
- Verified already-safe: focus math clamps, spring stall/NaN handling + tests,
  reduced-motion path, weak view refs for built-ins, decode guards + OOM catch,
  empty-state coverage in Search/Choice/Switcher, detach→Motion cancel
  everywhere, ≤23 APIs, zero-size draw guards, hidden→stop tickers.

---

## D. tools/ + dream/ — findings

### Fix

- [P0] `Cleanup.java:100-104` — `COMPONENT_ENABLED_STATE_DEFAULT` means
  installed+enabled, not "not installed" → enabled bloatware skipped, reported
  "already off"; feature silently no-ops. → check `getApplicationInfo().enabled`.
- [P0] `Cleanup.java:127` — `state()` maps IllegalArgumentException (not
  installed) to DEFAULT, conflating missing with enabled. → distinguish.
- [P0] `FileServer.java:391-405` — `copyFolder` recurses into symlinked dirs;
  self-referential loop copies forever filling the disk. → skip non-canonical
  children.
- [P0] `PhotoLoader.java:105-120` — `scanDir` recurses with no cycle guard;
  symlink loop → StackOverflowError. → track visited canonical paths. (root
  package → batch D owns the file)
- [P1] `FileServer.java:64-67` — `stop()` doesn't close in-flight client
  sockets; shutdownNow can't interrupt a socket write → huge transfer keeps
  streaming after the screen dies. → track client sockets, close on stop.
- [P1] `ScreensaverView.java:437-438` — auto-advance only when
  `current != null`; first-decode failure → black screen forever, no retry.
  → schedule retry in tick. (file owned by batch C)
- [P1] `ScreensaverView.java:317,444-447` — detach during `step()` → execute
  on shut-down pool → RejectedExecutionException on main. → catch it / guard
  isShutdown. (batch C; same as ui finding)
- [P1] `AppPanel.java:344-364` — one `new Thread` per icon bind; fast-scrolling
  300 packages spawns hundreds of decoders on 1 GB. → single executor +
  per-item in-flight flag.
- [P1] `DebloatPanel.java:94` — only NameNotFoundException caught; a
  RuntimeException kills the bg thread → "Reading…" forever. → catch
  RuntimeException, show error row.
- [P1] `Cleanup.java:50` — disables `com.google.android.tvlauncher`
  unconditionally; if Sonora isn't default, HOME dead-ends. → only when Sonora
  is the default home.
- [P1] `ScreenStack.java:316-324` — page of only note/value rows (Battery,
  Processes, log) has no focusable child; UP/DOWN swallowed, can't even
  scroll. → focusable fallback / skip intercept. (batch C, same as ui finding)
- [P1] `PhotoLoader.java:33-53` — `photoPaths` full-storage recursive scan can
  run on the main thread via ScreensaverView ctor + OverlayView.onDraw cache
  miss → multi-second dream-start stall. → preload off-thread. (batch C for
  ScreensaverView call sites; D for PhotoLoader)
- [P2-fix] `FileServer.java:99` — accept-loop `continue` on IOException → busy
  spin at EMFILE. → break on persistent failure.
- [P2-fix] `FileServer.java:45-48` — pool workers non-daemon → linger after
  stop. → daemon thread factory.
- [P2-fix] `FileServer.java:64-66` — sockets queued-but-not-started at
  shutdownNow never closed → clients hang. → close pending.
- [P2-fix] `FileServer.java:177` — `URLDecoder` maps `+`→space; a filename
  with `+` 404s. → decode %XX only, keep `+` literal.
- [P2-fix] `FileServer.java:144` — HEAD/POST handled as GET-ish 404. → 405 or
  proper HEAD (headers only).
- [P2-fix] `FileServer.java:248` — `Content-Length` statted before send;
  mid-serve resize → truncated body. → stream chunked or re-stat at send.
- [P2-fix] `FileServer.java:55-68` — `stop()` doesn't join accept thread or
  clear `port` → `address()` can show a dead port. → join + reset.
- [P2-fix] `ShareActivity.java:51` — sharing a file serves its whole parent
  dir while the subtitle names the file → over-shares. → say "folder" or
  serve just the file (prefer honest wording change).
- [P2-fix] `ShareActivity.java:92` — `address()` "" → blank line. → "No
  network address".
- [P2-fix] `ShareActivity.java:104` — bind-failure hint blames the network
  even when the port failed. → "could not start".
- [P2-fix] `ShareActivity.java:49` — `EXTRA_PATH` nonexistent → silently
  shares parent dir. → fall back with a note.
- [P2-fix] `ToolActivity.java:272-276` — `takePersistableUriPermission`
  SecurityException swallowed → dead wallpaper after reboot. → toast on
  failure.
- [P2-fix] `ToolActivity.java:136-139` — `reopenAt` posts `recreate()`
  unguarded on finishing activity. → check isFinishing.
- [P2-fix] `ToolActivity.java:355-357` — onResume rebuilds a settings page
  under an open ChoiceOverlay. → skip refresh while choiceOverlay != null.
- [P2-fix] `DebugPanel.java:271` — `process.waitFor()` unbounded → hung logcat
  leaks the thread. → waitFor(timeout) + destroy check.
- [P2-fix] `DebugPanel.java:154` — null processName → row titled "null".
  → "Unnamed".
- [P2-fix] `AppPanel.java:264-269` — `load()` posts notifyDataSetChanged
  without isDestroyed check. → guard.
- [P2-fix] `AppPanel.java:82` — null versionName → "null (1)". → "Unknown".
- [P2-fix] `AppPanel.java:144-149` — uninstalled app stays listed →
  Open/Uninstall on a ghost. → reload on resume.
- [P2-fix] `Pickers.java:248-255` — stored value not on the list → picker
  ticks "Never", OK silently sets 0. → tick nothing (-1).
- [P2-fix] `Scenes.java:112-119` — hidden flags keyed on Scene.name; two
  photos with the same filename hide together. → key by path. (root package →
  batch D)
- [P2-fix] `SetupGuide.java:160-179` — first non-loopback IPv4 may be VPN/
  tether → wrong IP shown. → prefer wlan/eth interfaces.
- [P2-fix] `DebloatPanel.java:40-43` — disabling stock launcher while Sonora
  isn't default strands HOME; warning only. → require isDefaultLauncher first.
- [P2-fix] `QuickPanel.java:204-207` — two consecutive toasts; second
  overwrites result. → one combined message.
- [P2-fix] `AerialsView.java:163-196` — empty category → empty grid, RIGHT
  dead-ends silently. → "No pictures" note.
- [P2-fix] `SetupWizard.java:62-65` — BACK off step 1 exits without
  welcomeSeen → wizard reappears. → mark seen (or accept + note).
- [P2-fix] `SettingsPanel.java:392-398` — "Reload apps" bumps epoch but the
  open Apps page isn't rebuilt. → refresh page too.
- [P2-fix] `FileServerTest.java` — only request-line parsing covered → add
  canonical-path traversal unit test (copyFolder symlink skip if testable).

### Noted
- Path traversal already canonical-checked (FileServer:233-239); malformed
  request lines rejected; server lifetime bound to ShareActivity; all heavy
  lists off main thread with dead-activity guards.

## E. launcher core — findings

### Fix (batch D owns HomeActivity + com.mitun.sonora root files)

- [P1] `Prefs.java:49,240` — getInt/getStringSet on a wrong stored type →
  ClassCastException crash loop via `gridKey()` in onCreate. → wrap getters
  in try/catch(ClassCastException), return default.
- [P1] `HomeActivity.java:910` — `RecentApps.recent()` queryEvents + ~10 icon
  decodes on the main thread on double-HOME. → build off-thread.
- [P1] `HomeActivity.java:1563` — search key while screensaver shown opens an
  invisible overlay stealing focus. → guard `openSearch` on screensaver up.
- [P1] `HomeActivity.java:1563` — search during edit mode leaves editing state
  silently under the overlay. → `endEditing()` first.
- [P1] `Grid.java:90` — `moveTarget` LEFT→index-1 is visually RIGHT under RTL
  (supportsRtl=true). → invert at the call site when
  `layoutDirection == RTL` (verify LinearLayout actually mirrors tiles first).
- [P1] `RecentApps.java:113` — `stats.queryEvents` unguarded RuntimeException
  → crash on double-HOME. → try/catch.
- [P1] `AppManager.java:89` — `app.loadLabel(pm)` unguarded → one bad package
  kills the whole load. → per-package try/catch.
- [P1] `PhotoLoader.java:115` — symlink recursion → StackOverflow. → visited
  canonical set. (from tools audit)
- [P1] `HomeActivity.java:1845` — `onPause→hideScreensaver→applyWallpaper`
  spawns a decode thread while pausing (background-work rule). → skip when
  pausing.
- [P1] `HomeActivity.java:824` — close CC/dismiss sets suppressIdle=false
  while a stacked overlay still open → screensaver fires over it. → count
  overlays.
- [P1] `HomeActivity.java:1789` — `onConfigurationChanged` reloads grid but
  dockMargin/shelf/chevron metrics go stale. → recompute or recreate.
- [P1] `HomeActivity.java:416` — `loadApps` callback checks isFinishing only;
  after `recreate()` builds grid on the dead instance. → add isDestroyed.
- [P1] `HomeActivity.java:1097` — "Uninstall" offered for FLAG_SYSTEM apps;
  ACTION_DELETE does nothing real → fake capability. → offer Disable/Hide.
- [P1] `HomeActivity.java` (ui-audit) — overlays don't dismiss each other:
  menu + search key → SearchOverlay over picker → OK launches invisible app.
  → dismiss before adding, mirror handleBack ordering.
- [P1] `HomeActivity.java:818-829,941-950` — focus restored while CC/switcher
  still fading → keys move the grid under a visible panel ~0.3 s. → restore in
  the close callback.
- [P2-fix] `Ui.java:252` — <28 failed bounds decode → sample=1 → full-res
  attempt. → bail when outWidth<=0. (batch C owns Ui.java)
- [P2-fix] `HomeActivity.java:473` — zero-apps subtitle instantly overwritten
  by Settings tile's focus hint. → keep message until a real app focuses.
- [P2-fix] `HomeActivity.java:385` — searchPill's glass rect can bake at 0.
  → add the listener.
- [P2-fix] `AppSearch.java:45` — `Locale.getDefault()` lowercase → Turkish-ı
  misses "I…" labels. → Locale.ROOT.
- [P2-fix] `SearchKeys.java:51` — DELETE removes one UTF-16 unit → lone
  surrogate on emoji. → delete by codePointCount.
- [P2-fix] `AndroidManifest.xml:23,31` — READ_EXTERNAL_STORAGE declared twice.
  → drop one. (I own the manifest)
- [P2-fix] `AppManager.java:109` — `versionCode` deprecated ≥28;
  versionName can be null. → getLongVersionCode + fallback.
- [P2-fix] `RecentApps` icon null → blank card. → accent fallback. (the card
  is AppSwitcherView → batch C)
- [P2-fix] `HomeActivity.java:564` — `restoreFocus` post lacks isDestroyed
  guard → requestFocus on dead window. → add check.
- [P2-fix] `HomeActivity.java:1028` — `bakeGlass` spawns unbounded threads on
  rapid layout churn. → single executor.
- [P2-fix] `SystemInfo.java:64` — `getExternalStorageState()` unguarded
  RuntimeException. → try/catch.
- [P2-fix] `HomeActivity.java:1592` — `editMove=false` when focus is on
  searchPill/Settings → raw framework nav while editing. → consume always.
- [P2-fix] `PhotoLoader.java:129` — `toLowerCase()` default locale for ext
  match. → Locale.ROOT.
- [P2-fix] `HomeActivity.java:1098` — RTL/bidi app label inside "Open %s"
  renders oddly. → bidi isolate.
- [P2-fix] `ChoiceOverlay.java:92` — "nothing to choose" row focusable but OK
  dead. → non-focusable. (batch C)

### Noted, not fixed
- `hide()` dead code in menu (hiding lives in AppPanel) — mention, don't
  rewire. `ss_photo_folder` dead pref — noted. `goHome()` sole-home-not-
  default relaunch — edge, accepted. Stale grid on external installs (no
  receivers by rule — accepted tradeoff). `AppRepository` shared mutable cache
  — safe today. `DeviceTier.refresh` un-degrades — intended. `suppressIdle`
  post double-restore — harmless. `AppSwitcherView.postDelayed` on detached —
  folded into C. Fixed-44sp shelf title clip — accepted.
- Verified already-safe: ActivityNotFound/SecurityException on launches,
  decode/OOM→bundled-scene fallback, wallpaper permission denial, zero-app
  grid, BACK breadcrumb via BackPolicy, overlay focus confinement,
  onTrimMemory releases, ticker/executor stop on detach, generation guards.

---

## Fix batches (disjoint file sets → parallel fix agents)

1. **browser/** — BrowserActivity, AdBlock, ToolsPanel row, SettingsPanel row,
   network_security_config.xml, adlist.txt, new AdBlockTest. Verify:
   `:app:testDebugUnitTest`, `:app:assembleDebug`.
2. **files-core/ + files/** — FileOps, FilesProvider, FilesActivity, Sheets,
   QuickLook, ItemCell, FileLogic, Places. Verify:
   `:files-core:testDebugUnitTest`, `:files:assembleDebug`.
3. **ui/** — Motion, Spring, ScreenStack, AppTileView, AppIconView,
   FocusGlassView, BackdropView, AppSwitcherView, ScreensaverView, Ui, Type,
   SettingRow, GlassLens, GlassPillView, ChoiceOverlay. Verify: unit tests.
   NOTE: BackdropView constructor change must be coordinated with the
   HomeActivity caller — fix agent owns both sides of that seam only.
4. **HomeActivity + root package** — overlay mutual-dismissal, focus-restore-
   inside-callback, BackdropView caller side, plus retry-audit findings.
5. **tools/ + dream/** — FileServer + panels, per retry audit.

Sequencing: batches 1–3 and 5 are disjoint. Batch 4 touches HomeActivity which
ui/ findings 4/5/7 also reference — run batch 4 after 3 lands, or have 4 take
all HomeActivity edits (chosen: batch 4 owns ALL HomeActivity.java lines).
