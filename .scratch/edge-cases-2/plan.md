# Edge-case pass 2 — exhaustive plan

Status: findings complete, fixes in-progress

Second-pass audit. Ten read-only agents, disjoint scopes, each told to skip
everything the first pass (`.scratch/edge-cases/plan.md`) already fixed or
noted, and to verify those fixes. ~85 new unique findings below; every
claimed fix was also regression-checked (a few were incomplete — flagged).

Fix policy (same as pass 1): **every P0 and P1 gets fixed.** P2s are fixed
when the fix is a one-liner/guard/clamp/constant; structural or purely latent
P2s are listed under "Noted, not fixed" with the reason.

---

## A. P0 — one finding, three agents converged

- [P0] `RecentApps.java:56-64` — `AppOpsManager.unsafeCheckOpNoThrow` was added
  in **API 29**; on API 23–28 it throws `NoSuchMethodError` — an `Error`, which
  `catch (RuntimeException)` cannot stop. Two triggers:
  (a) Settings → "Home Screen" calls `usageGranted` on the main thread → crash;
  (b) double-HOME → `recent()` dies on the `sonora-recents` thread → the
  switcher never opens **and** `suppressIdle` stays true → screensaver
  permanently disabled for the session.
  Fix: `ops.checkOpNoThrow(OPSTR_GET_USAGE_STATS, Process.myUid(), pkg)`
  (exists since API 23); also wrap the recents thread body so a throw still
  resets `suppressIdle` via the UI callback. Add an assert-style unit test on
  the op-string path if testable. Lint should have caught this (NewApi) — no
  lint baseline exists; note for later.

## B. P1 findings (~24)

### HomeActivity / launcher

- [P1] `HomeActivity.java:179-198` — `idleWatcher` reposts
  `Math.max(1000, due-now)`; while the saver runs or an overlay suppresses
  idle, `due` is permanently past → wakes every 1 s for hours — the exact
  "waking every second" the file's own comment forbids. Fix: don't repost
  when `screensaver != null || suppressIdle`; re-arm inside
  `updateSuppressIdle()` on the →false transition.
- [P1] `HomeActivity.java:1656-1684` — `editing` consumes every DPAD key and
  OK before overlays/saver see them. Two triggers: arrange-mode + double-HOME
  → switcher opens but is unnavigable (keys eaten, OK ends editing
  invisibly); arrange-mode + idle → saver shows but can't be dismissed by OK
  (first OK ends editing invisibly). Fix: gate both editing shortcuts on
  `!overlayOpen() && screensaver == null`; end editing in
  `openAppSwitcher()`/`showScreensaver()` (mirror the search-key fix).
- [P1] `HomeActivity.java:785-797` — `focusFirstTile` forces tile-0 focus
  whenever the focused view isn't an `AppTileView`, including a view inside
  an open overlay (boot + voice-assist round-trip leaves `initialFocusDone`
  false) → keys move the grid under a visible modal. Fix: latch
  `initialFocusDone` and return early when `overlayOpen() ||
  screensaver != null || focused != null`.
- [P1] `HomeActivity.java:384-386` — `gridLabel` uses `Gravity.START` with an
  absolute positive `translationX` → in RTL locales the focused-app name
  renders off-screen (labels are the only name for rows > 0). Fix:
  `Gravity.LEFT` (math is already physical-left).
- [P1] `HomeActivity.java:1922-1929` — `TRIM_MEMORY_MODERATE` clears tiles,
  grid and repo cache but keeps `appEntries` — the icon/banner bitmaps (the
  actual payload) stay pinned; "hidden means released" violated. Fix:
  `appEntries = new AppEntry[0]`.
- [P1] `SearchOverlay.java:337-340` — results-grid nav passes raw keycodes to
  `Grid.moveTarget`; row `LinearLayout`s mirror under RTL → LEFT/RIGHT
  inverted (the HomeActivity `dpadKey` fix wasn't carried here). Fix: swap
  LEFT↔RIGHT when layoutDirection==RTL.
- [P1] `PackageActions.java:51-54,261-263` + `AppManager.java:129-132` —
  launch-intent precedence is `getLaunchIntentForPackage` first while
  `AppEntry.launch`/`AppRepository` prefer the leanback entry → dual-entry
  packages open the phone UI; `setHidden` disables the phone component so
  `isOff` reports "Hidden" while the app stays on home via its leanback
  entry. Fix: leanback first in all three sites.
- [P1] `app/src/main/AndroidManifest.xml` — bundled Files can't install APKs:
  `REQUEST_INSTALL_PACKAGES` missing (the standalone `files` app has it) →
  Open on an `.apk` from launcher-Files always fails → dead feature on one
  entry point. Fix: add the permission.

### files-core

- [P1] `FileOps.java:187-209` — copy drops `lastModified`; a cross-filesystem
  move (copy+delete) too → the Date Modified sort column shows today for
  everything copied/moved. Fix: `into.setLastModified(source.lastModified())`
  per file, and for dirs after children.
- [P1] `FilesActivity.java:649-666+` — `open()` doesn't clear `entries` while
  the new listing queues on `lister`; toolbar/path show the new folder while
  the grid still lists and *operates on* the old one (delete/rename on
  wrong-folder entries). Fix: clear entries + notify at the top of `open()`.
- [P1] `FilesActivity.java:1014-1019` — `Sheets.input` opens from a deferred
  `lister→runOnUiThread` callback with no modal check → stacks over a sheet
  or QuickLook that appeared during the wait; `sheet` field desyncs, focus
  lands under the modal. Fix: bail if `quickLook != null`; `sheet.cancel()`
  an open sheet first.
- [P1] `Sheets.input` deferred-modal guard — same item, folded above.

### network / browser

- [P1] `FileServer.java:256-291` — directory requested without trailing slash
  gets a listing of relative hrefs that resolve one level up → every link
  404s (only for hand-typed URLs). Fix: `301` + `Location: path/`.
- [P1] `BrowserActivity.java` — no `DownloadListener` → clicking a
  downloadable file is a silent dead-end (same class the first pass fixed
  for window.open/file-input/dialogs). Fix: honest "cannot download" toast.

### dream / photos / prefs

- [P1] `Scenes.java:54-66` — photo source "Device photos" (1) still returns
  bundled+photos = identical to "Both" → fake choice. Fix: include bundled
  only when `source != 1`; fall back to bundled if the result is empty
  (avoids permanent black saver).
- [P1] `SettingsPanel.java:364-374` + `PhotoLoader` — "Photo source" silently
  no-ops without storage permission; nothing requests it or says so → row
  says "Device photos", saver shows bundled. Honest-UI violation. Fix:
  replicate `hasStorageAccess` (checkSelfPermission /
  `isExternalStorageManager` ≥30); honest note row or route to the Files
  access flow when denied.
- [P1] `ScreensaverView.java:256-264` — full-storage `photoPaths` scan is
  queued on the single-thread `decodeExecutor` ahead of the first scene
  decode → black panel for seconds-to-minutes at dream start. Fix: own
  daemon thread for the scan.
- [P1] `ScreensaverView.java:354-360,472-476` — failed decode burns a full
  `sceneDurationMs` of black (e.g. USB pulled mid-dream → minutes of black).
  Fix: on `bitmap == null`, advance again after a short delay (consecutive-
  failure pacing ~2–3 s).
- [P1] `PhotoLoader.java:37,109-138` — scan can't be interrupted and holds
  the class monitor → keeps scanning after the saver hides (hidden-means-
  released) and can ANR the main thread via `invalidateCache`. Fix:
  `isInterrupted()` checks in `scanDir`; synchronize only field access.
- [P1] `PhotoLoader.java:72` — sampling uses `||` (fit-inside) not `&&`
  (cover) → every device photo decodes at ≤ half intended size, visibly
  blurrier than bundled scenes. Fix: `&&`.

### settings / tools screens

- [P1] `SetupWizard.java:80-96` + `ToolActivity.java:414-418` — wizard CLOCK
  step title "Clock" collides with the settings page name → `onResume`
  replaces the wizard step with the Clock settings page → wizard dead-ends.
  Fix: skip settings refresh while `wizard != null` and refresh the wizard
  instead.
- [P1] `DebugPanel.java:143-157` — `getTotalPrivateDirty()` returns kB,
  displayed as bytes → every memory figure on the Processes screen is off
  by 1024×. Fix: `× 1024`.
- [P1] `AerialsView.java:85-107` — "Choose Aerials" lists bundled scenes
  only; with photo source ≠ Bundled every toggle is a no-op for what plays
  (device photos can't be hidden there) → fake controls. Fix: when
  `screensaverPhotoSource() != 0`, show an honest note / adjust the screen.
- [P1] `ScreenStack.java:366-390` — `dispatchKeyEvent` confines UP/DOWN only;
  LEFT/RIGHT fall to framework focus search, which can land on the covered
  but still-VISIBLE under-screen → OK acts on a screen the user can't see.
  Fix: consume LEFT/RIGHT in the same `screen.list != null` branch.
- [P1] `ScreenStack.java:329-331` — a top screen with zero focusables (About
  page: plain TextView) leaves focus on the covered screen → every key
  drives an invisible page, OK fires blind actions. Fix: consume DPAD/OK
  when the top screen doesn't own focus; make the about/log scroller
  focusable so it can scroll.
- [P1] `AppSwitcherView.java:74-83,116-140` — card row is wider than the
  screen; end cards sit fully off-screen yet stay focusable (card 0 — the
  one focused at open — is invisible at 10 recents on 1080p, worse on 720p)
  → OK launches / kills an app the user can't see. Fix: cap the list to
  what fits: `(screenW - CARD_W) / (CARD_W + GAP) + 1`.
- [P1] `AppPanel.java:139-183` — Sonora itself is listed with "Hidden from
  home screen" + "Uninstall"; if Sonora is default, either strands HOME —
  the guard that exists for the stock launcher isn't applied to self. Fix:
  suppress destructive rows when `packageName.equals(host.getPackageName())`.

## C. P2 findings — fixed when small

(Each is a guard/clamp/constant/flag; fix unless the batch agent finds it
structural — then move to "Noted".)

Home/UI:
- switcher build callback lacks `resumed` check (HomeActivity:961-998);
  async completion can stack switcher over an overlay opened mid-decode
  (add `overlayOpen()` bail).
- `applyGridScroll` re-VISIBLEs `hero`/`gridBackdrop` under the saver while a
  spring is in flight (early-return when `screensaver != null`).
- `releaseWallpaper` doesn't clear `searchPill` backdrop (+1 line).
- HOME during saver is a no-op (dismiss saver in `onHomeSignal`).
- HOME-close of switcher doesn't refresh `lastHomePressAt` → 4th rapid
  press reopens.
- `entry.label.contentEquals` NPE on null label (TextUtils.equals).
- `moveTile` doesn't refresh `gridKey` → pointless full reload next resume.
- `AppOrdering.order` runs outside the load try → thread death strands
  empty grid (wrap in same try).
- duplicate wallpaper decode threads for the same key under memory pressure
  (skip when `key.equals(wallpaperRequest)`).
- `ACTION_ALL_APPS` scroll+focus with no overlay guard.
- dead onResume `screensaver != null` branch (note only).

ui/ toolkit:
- `Motion`: settled-callback throw corrupts runner; `cancel()`-inside-settle
  → double frame callbacks; `set()/cancel()` drop a pending `onSettled`
  without running it — collect callbacks, run after bookkeeping, repost
  only if `runner.running`, run pending callback before nulling track.
- `Spring`: NaN `dt` → NaN accumulator → runner ticks forever (contract;
  guard).
- `ControlCenter` clock row is a stale snapshot after a minute
  (setClock / re-evaluate at open — or accept, decide).
- `SearchOverlay`: no dismissed flag → keys mutate query during the 0.3 s
  fade; `cards.get().entry()` contract-NPE guard.
- `ScreenStack`: `refresh` can silently change screen kind leaving `under`
  stuck INVISIBLE (assert/comment or recompute); `smoothScrollBy` under
  reduced motion → `scrollBy`; `slideIn` width<=0 mixed pair (latent, note).
- `AppSwitcherView`/`GlyphPillView`/`ChoiceOverlay`/`SettingRow`:
  `new Motion.FloatProperty` per focus event — hoist to field (the
  GlassPillView fix wasn't carried to 4 sites).
- `AppIconView` ellipsize can split a surrogate pair.
- `ChoiceOverlay` option label no singleLine/ellipsize.
- `Ui.decodeUri` ≥28 branch: bounds-stream failure → full-res decode attempt
  (the <28 bail wasn't carried over).
- `SearchKeyboard` no per-key accessibility announcement (note — structural).
- `ToolActivity.onResume` doesn't `Motion.refresh` → dev-options animator
  change invisible there.

dream/photos/prefs:
- `ScreensaverView`: reduced-motion still pans (translationX ungated);
  `stop()` leaves `incoming` mid-fade alpha (latent); mid-fade LEFT targets
  outgoing scene's index (cosmetic); dead `Listener`/`setListener` +
  unreachable BACK branch — delete; constructed-never-attached leaks daemon
  (latent, note).
- `PhotoLoader`: EXIF orientation ignored (portrait photos sideways);
  RGB_565 always vs tier ARGB_8888; dead `outWidth==0` check (→ `<=0`);
  API≥30-only full scan means 23–29 photos outside Pictures/ never found
  (gate on permission not version — or document); no depth bound on
  `scanDir` (cap ~32); wall-clock cache (→ elapsedRealtime/clamp);
  `.heic`/`.heif` missing (API 28+ decode — add guarded).
- `Scenes`: `hiddenFlags` calls `all()` a second time → the two lists can
  diverge across the 5-min cache (add overload taking the list);
  `count(Context)`/`safeIndex(int,Context)`/`regions(Context)` dead —
  delete.
- `Prefs.reset()` two `apply()` calls — process death between loses
  `welcome/mode` (single editor).
- `HomeActivity` vs dream OK-semantics divergence (preview vs real — note).
- `AerialsView` subtitle doesn't mention device photos rotate (wording).

files-core:
- `FileOps`: partial dir tree left on cancel/ENOSPC (deleteTree dest on
  abort); moves skip free-space check though cross-fs move *is* a copy;
  `moveTo` renames symlinks/FIFOs before copy's guards; `sameFolder`
  non-canonical compare → wrongly renames to "a copy"; `delete()` can end
  mid-`deleteTree` reporting neither failed nor cancelled; rename
  exists→renameTo TOCTOU (rename(2) overwrites — *noted: java.nio.Files is
  API 26+; keep re-check, accept residual on 23–25*); FIFO/socket named
  .jpg blocks `thumbnailer`/`QuickLook` open forever (`!isFile` skip);
  recursion in `sizeOf`/`copyTo`/`deleteTree` → SOE kills `showInfo` thread
  silently (catch Error → end wait); paste-TOCTOU fix used
  `createNewFile`+`FileOutputStream` — residual swap window (*noted: API
  26+ needed for atomic; accept*).
- `FileLogic.nameProblem`: misses FAT-illegal `\?*:"<>|` and trailing
  dot/space → generic error later.
- `FilesProvider.query` returns a file row for a directory (`!isFile → null`).
- `Places`: volume whose app dir lacks `/Android/` is silently skipped →
  drive absent from sidebar (walk-up fallback).
- `FilesActivity`: raw `getSharedPreferences` getters unguarded
  `ClassCastException` (same fix as `Prefs`); `listed = dir` set at submit
  not success → transient failure never retried; paste select misses
  "x copy" collision name; `getParent()` null → "null" in Get Info
  (contract); lister callbacks can stack a second sheet (ordering);
  `backButton.setEnabled` without `setFocusable` → still focusable, eats OK;
  `readPlaces` canonical/storage calls on main at every onResume (small
  ANR surface — fix if clean, else note); "files-info"/"files-job"/
  "files-quicklook" threads non-daemon (setDaemon true);
  `invalidateViews`+per-draw `ellipsize` heavier than two-card rule
  (cache clipped strings in bind); paste select via `freeName` result.
- `ItemCell`: `modified==0` → "Jan 1, 1970" (guard `--`); sp text overflows
  fixed dp cell at fontScale ≥1.6 (clip or note); row columns hard LTR
  under supportsRtl (cosmetic, note).
- `Sheets.alert` heading no ellipsize → 255-byte name pushes buttons
  off-screen.
- `QuickLook`: `new Thread` per `show()` — key-repeat storm (~10 concurrent
  decodes on 1 GB); single executor or skip-while-decoding.
- `files/MainActivity` fine.

tools/:
- `ToolActivity`: double-OK during `ChoiceOverlay` fade re-fires
  `onPicked` (guard `choiceOverlay != overlay`); `reportUninstall` fires on
  first resume while the installer dialog is still pending; `stackFocus`
  can capture a mid-pop row; refresh-on-resume misses Debloat/SetupGuide/
  app-detail screens (stale toggles/ghost rows); standard launchMode stacks
  instances (ToolActivity/ShareActivity → `singleTask`/`singleTop`).
- `SettingsPanel`: title-collision is generic — any pushed screen titled
  "Apps"/"System"/etc. gets replaced on resume (tag screens by identity,
  not title); `confirmReset` misses `Theme.load` + `DeviceTier.refresh`;
  Favourites/Hidden loaders lack the RuntimeException catch → "Reading…"
  forever.
- `Pickers`: hidden/favourites counts include uninstalled pkgs → ghost rows
  that can never be cleared (count only installed / prune on read);
  stored-value drift ticks wrong option (clamp at getters).
- `PackageActions`: `setPackageEnabled` doesn't verify the state (dishonest
  toast on silent-fail firmware); `isRunning` misses `pkg:remote` process
  names (use `pkgList`); javadoc misclaims `CHANGE_COMPONENT_ENABLED_STATE`
  is a normal permission (signature|privileged — correct doc or pre-check).
- `Cleanup`: `problems`/`alreadyOff` collected but never surfaced (name
  them or drop).
- `DebugPanel`: `readLine` itself can block forever on wedged logd
  (deadline/destroy watchdog); `wifiName` shows `<unknown ssid>` verbatim;
  `plugged == -1` → "Plugged in" not "Unknown".
- `QuickPanel`: "Wi-Fi: Connected" when active network is Ethernet (check
  TYPE_WIFI); keyboard/language fall back to generic Settings instead of
  `ACTION_INPUT_METHOD_SETTINGS`/`ACTION_LOCALE_SETTINGS`; `tidyUpNow` no
  in-flight guard (double-tap → N threads).
- `SetupGuide`: "Copied" toast duplicates API-33 clipboard preview (skip ≥33);
  `ipAddress` empty on IPv6-only / link-local (skip link-local, bracketed
  v6 fallback).
- `AppPanel`: resume `load()` can overlap (in-flight boolean); `getView`
  returns stale recycled row when `getItem` null (clear fields); detail
  screen not rebuilt on resume (folded into ToolActivity resume item).
- `SetupWizard` ROLE/SAVER rows stale after returning from system settings
  (wizard refresh on resume — same fix as the P1).
- `DebloatPanel`/`SetupGuide` stale on resume (ToolActivity item above).

network/browser:
- `FileServer`: malformed request lines ("GET", "GET  /x", "GET\t/x") serve
  the root listing instead of 400 (null-path → 400; keep the "GET→/" test
  in sync); overlong request line silent-closes instead of 414 (distinguish
  sentinel); no write timeout — one stalled client pins a worker forever
  (note — no small fix on blocking IO); `index.html` checked on the
  *encoded* name (decode `relative` first); stale address states: accept-
  loop death leaves bound dead socket + "Open this address", `hint` not
  refreshed on onStart, IP change never re-read (field the hint, recompute
  address each onStart); `server.stop()` on UI thread joins accept → ~0.5 s
  stall (daemon thread); `copyFile(f,f)` self-truncation hole — **dead code**
  (no production callers; either add canonical self-guard or delete —
  decide); `mimeOf` missing mkv/avi/mov/srt — the common TV files → download
  instead of play; `dir.getName()` "" for "/" / newline names (path
  fallback).
- `ShareActivity`: see FileServer items above (hint/address/stop thread).
- `BrowserActivity`: `isLocalHost` prefix checks — `10.evil.com`,
  `192.168.attacker.com`, `127.foo.com`, `172.16.bad.com` pass → cleartext
  to non-LAN host (gate behind IPv4-shape regex); bookmark clicks bypass
  `localOrHttps` (route through it); `web.saveState` unguarded (try/catch);
  typed `about:blank`/bare `::1`/`foo:` → `http://` garbage (pass about:,
  reject hostless `x:`).
- `adlist.txt`: `amazonaws.com.trakc` dead entry + 4 redundant subdomains
  (dedupe incomplete last pass).
- `network_security_config.xml`: regression — cleartext permitted globally
  not just private ranges (domains can't express IP ranges; documented
  deviation — keep, note).

## D. Noted, not fixed

- FileServer write timeout — no small fix on blocking sockets; bounded by
  pool size, documented.
- SearchKeyboard TalkBack announcements — structural, flagged for a later
  accessibility pass.
- renameTo/paste TOCTOU residual on API 23–25 — atomic APIs are API 26+;
  re-check kept, residual accepted (single-user TV).
- `ScreenStack.refresh` kind-change + `slideIn` width<=0 mixed pair —
  latent, no current caller triggers.
- `ItemCell` RTL columns / fontScale ≥1.6 overflow — cosmetic.
- HomeActivity dead onResume branch, `ScreensaverView` never-attached leak,
  saver-vs-dream OK divergence — contract/intent, noted.
- `Ui.start` mutating caller intent, contract NPEs — same class as pass 1.
- `network_security_config` global cleartext — deliberate (see above).
- `PhotoLoader` API 23–29 scan scope — verify intent; likely gate by
  permission.
- `AppSwitcherView` HorizontalScrollView alternative — cap chosen (simpler).

## E. Fix batches (disjoint file sets → parallel fix agents)

1. **app root + manifest** — `RecentApps.java` (P0 + thread hardening),
   `HomeActivity.java` (all its items), `PackageActions.java`,
   `AppManager.java`, `app/src/main/AndroidManifest.xml`
   (+ `REQUEST_INSTALL_PACKAGES`). Verify: `:app:testDebugUnitTest`.
2. **files-core + files** — `FileOps`, `FileLogic`, `FilesActivity`,
   `Sheets`, `ItemCell`, `QuickLook`, `FilesProvider`, `Places`.
   Verify: `:files-core:testDebugUnitTest`.
3. **tools/** — `ToolActivity`, `SettingsPanel`, `Pickers`, `Cleanup`,
   `DebloatPanel`, `AppPanel`, `AerialsView`, `SetupWizard`, `SetupGuide`,
   `DebugPanel`, `QuickPanel`, `ShareActivity`. Verify:
   `:app:testDebugUnitTest`.
4. **ui/ + dream + photos** — `ScreenStack`, `AppSwitcherView`, `Motion`,
   `Spring`, `ControlCenter`, `SearchOverlay`, `ScreensaverView`,
   `PhotoLoader`, `Scenes`, `SceneChoice`, `Prefs`, `SonoraDreamService`,
   `Ui`, `GlyphPillView`, `ChoiceOverlay`, `SettingRow`, `AppIconView`,
   `SearchKeyboard` (note only). Verify: `:app:testDebugUnitTest`.
5. **network + browser** — `FileServer`, `BrowserActivity`, `AdBlock` (if
   touched), `adlist.txt`, `network_security_config.xml` (note only),
   `FileServerTest`/`EdgeCasesTest`/`browser` tests. Verify:
   `:app:testDebugUnitTest`.

Sequencing: all five batches touch disjoint files; run in parallel.
Cross-batch seams: batch 3 may *call* `Scenes.all(context)` (batch 4 keeps
the signature, only changes internals); batch 3's `ShareActivity` items are
UI-side only (server internals stay in batch 5). `FilesActivity` stays
entirely in batch 2.
