# Spec: whole-repo audit, 2026-11-27

Status: resolved where ticketed
Phase: 3 of 3 done. Tickets are under `issues/`; 20 are resolved, 2 wait
for the owner (branch deletes, the master push), 1 is won't-fix, and the competitor gaps (25–33) wait for the owner's order.
Section F was answered on 2026-11-27 with "proceed": the spec's leanings
were taken, and each ticket records the choice.

## Goal

Every existing feature works on a real TV, including the edge cases. The repo
has one branch, `master`, and every commit on it has the owner as the only
author. Sonora closes the gaps to the other TV launchers where the project
rules allow it, and says plainly where they do not.

## How this audit was done

- Read the code: `HomeActivity` in full, `Prefs`, `AppOrdering`,
  `AppRepository`, `AppEntry`, `RecentApps`, `Grid`, `DeviceTier`,
  `PackageActions`, `AppSearch`, `SearchKeys`, `SystemInfo`, `BackPolicy`,
  the manifest and build files, `FileServer`, `ShareActivity`, the dream
  service, and the parts of `ToolActivity`, `AppPanel`, `AppSwitcherView`,
  `SearchOverlay`, `ScreensaverView`, `QuickPanel`, `GlassPillView`,
  `PhotoLoader` and `FileOps` named below. The rest was searched by pattern
  (threads, timers, key handling, permissions).
- Read every remote branch and all 9 pull requests.
- Read the six earlier edge-case plans, so nothing here repeats a fix that
  is already on `master`.
- Searched the web for the launchers people actually use (sources at the end).
- **Not done:** no build, no unit-test run, no TV. This container cannot
  reach `dl.google.com`, so the Android SDK cannot be downloaded. Phase 3
  needs that host allowed, or the builds run in CI only. Every item below
  that says "needs TV" must be checked on the TV before it is closed.

## A. Repository: one branch, owner-only history

### State found

`master` (`7bdbbbf`) is clean: 30 commits, all authored by the owner, no
`Co-authored-by` trailers. The mess is in the branches and pull requests
around it.

| Branch | What it is | PR | Proposed |
|---|---|---|---|
| `master` | The product | — | Keep. The only branch. |
| `ccr-a6002ca3-nazqx9` | This session's working branch, equal to `master` | — | Carries this audit. Fast-forward into `master`, then delete. |
| `devin/1795768622-existing-feature-perfection` | 3 fixes by Devin (A2) | #9 open | Redo the 3 fixes as owner commits (B1, B2, B3). Close #9. Delete. |
| `copilot/competitive-analysis-repo-structure` | Moves modules to `apps/` and `libraries/`, rewrites README, adds a competitor doc. Draft. Never built: AGP could not resolve. Carries a `Co-authored-by: copilot-swe-agent[bot]` trailer. | #8 open draft | Keep only the competitor doc, rewritten as an owner commit (E1). Drop the module move. Close #8. Delete. |
| `dependabot/gradle/com.android.application-9.5.2` | AGP 8.13 → 9.5.2, a major version | #6 open | Close. A major AGP bump needs a matching Gradle wrapper and a full build; do it as its own owner ticket once the SDK is reachable (A4). Delete. |
| `dependabot/gradle/com.android.application-9.5.1` | Superseded | #2 closed | Delete. |
| `dependabot/gradle/com.android.library-9.5.1` | Superseded | #3 closed | Delete. |
| `files-audit-fixes` | 1 commit by Devin: hide list headers on empty folders, fade Quick Look in | #7 closed, not merged | Owner decision: redo as an owner commit, or drop. Delete either way. |
| `devin/1794655952-untangle` | Old pre-rewrite history; author "Download Movies" | #5 closed | Delete. Its fixes for a settings reset are already on `master`. |
| `devin/1794567857-tidy-polish` | Old pre-rewrite history; author "Download Movies" | #4 closed | Delete. |
| `codex/history-20` | A separate history: "Consolidated history segment 1/19…19/19" | — | Delete. |
| `ccr-9ed4ac55-u9kxfl` | 1 README restyle commit authored by "Claude" | — | Owner decision: redo as owner commit, or drop. Delete either way. |

### A1. Delete every branch except `master`
Ten remote branches go. Deleting a branch cannot be undone from here, so it
waits for the owner's yes. The old histories (`codex/history-20`, both old
`devin/` branches) hold commits that were rewritten on purpose before
publication; deleting them is the point.
Accept: `git ls-remote --heads origin` lists `master` only.

### A2. Why Devin shows up, and how it stops
The Devin commits are authored with the owner's e-mail but committed by a
second account ("Download Movies"), and the pull requests are opened by the
`devin-ai-integration[bot]` app. Merging a bot's pull request lists the bot
as a contributor, which is what the `Sole author` CI check is written to
stop. Nothing from Devin is on `master` today.
Accept: no PR open from a bot; no Devin, Copilot or Claude trailer or author
on `master`; the `Sole author` job is green on `master`.

### A3. Agent commits from now on
This session's git identity is "Claude <noreply@anthropic.com>", and the
default commit footer adds a `Co-Authored-By` trailer. Both fail the
`Sole author` check. Phase 3 commits are written with the owner as author
and no trailer, which is the rule the CI and the publication session (PLAN
§35) already set.
Accept: `Sole author` job green on every phase-3 push.

### A4. Dependabot keeps making branches
Dependabot opens one branch per bump, which works against "one branch".
Options: keep it (and close/copy its PRs by hand, as the CI step says),
limit it to security updates, or remove `.github/dependabot.yml`.
Owner decision.

### A5. Doc drift found on the way
- `AGENTS.md` tells agents to run `codegraph sync /home/mitun/aerial`. That
  path is another machine's, and `codegraph` is not installed here.
- `docs/ARCHITECTURE.md` lists `.scratch/benchmark-scripts/`, which does not
  exist, and leaves `bench.py` and `perf-ab.md` out of `scripts/`.
- The docs-sync skill lives in `.devin/skills/`; the other skills live in
  `.agents/skills/`. Owner decision whether to move it (the owner does not
  want Devin in the repo's identity).
Accept: every path a doc names exists.

## B. Correctness bugs in features that exist

Severity: **S1** a feature is broken or lies, **S2** wrong in a common case,
**S3** wrong in an uncommon case.

### B1. S2 — Share screen can stay dead after a quick leave-and-return (from PR #9, confirmed)
`ShareActivity.stopServer()` stops the server on a helper thread. A return
before that thread has run sees `isRunning()` still true, skips the restart,
and then the helper kills the server. The screen shows an address that does
not answer. `FileServer.start()` also calls `stop()` while the old stop may
still be running on the other thread, racing on the same fields.
Fix as PR #9 does: a fresh `FileServer` per start, `stop()` closes without
joining.
Accept: unit test that start → stop → start on one screen always leaves a
listening port; on TV, HOME then straight back to Share 10 times, address
answers each time.

### B2. S2 — A new pin goes before the older ones (from PR #9, confirmed)
`HomeActivity.toggleFavourite` (`HomeActivity.java:1329`) and the Favourites
page both `add(0, …)`. `Prefs.favourites()` and `AppOrdering.order()` both
document "in the order they pinned them".
Fix: one `AppOrdering.toggleFavourite` used by both callers, new pin last.
Accept: unit test; on TV, pin A then B, A stays first.

### B3. S3 — Favourites and Debloat pages can show stale rows after quick toggles (from PR #9, confirmed)
Each toggle starts a thread that rebuilds the rows; two reads can land out of
order and the older one wins. Fix: a reload generation per page.
Accept: only the newest read replaces the rows.

### B4. S1 — Apps installed, removed or updated outside Sonora never show up
`AppRepository` keeps the app list in a static cache that only Sonora's own
actions clear (uninstall from the tile menu, App info, Debloat, App manager,
a trim at `MODERATE`). Nothing notices an app installed from the Play Store,
pushed over adb, removed in the system Settings, or updated with a new
banner. While the process lives — and a launcher's process lives for days —
the new app is missing from the grid and search, and a removed app stays as
a tile that says "Could not open".
Fix within "no receivers": on `onResume`, ask the package manager what
changed. `PackageManager.getChangedPackages(sequenceNumber)` on API 26+;
below that, `LauncherApps.Callback` registered in `onStart` and removed in
`onStop` (a callback, not a receiver, and it dies with the screen).
Rebuild only when something changed, so a resume stays free.
Accept: install an APK over adb while an app is open, press HOME, the app is
in the grid. Uninstall from system Settings, return, tile gone. Needs TV.

### B5. S1 — "Force stop" in the app manager never stops anything
`PackageActions.forceStop()` first checks `isRunning()`, which reads
`ActivityManager.getRunningAppProcesses()`. Since Android 5.1 that call
returns only the caller's own processes, so every other app reads as "not
running", the toast says "Not running", and `killBackgroundProcesses` is
never called. This is a fake feature on API 23–33 (on 34+ the row already
hands off to system settings).
Fix: drop the pre-check, call `killBackgroundProcesses`, and word the result
honestly ("Asked Android to stop it; a foreground app keeps running"), or
send every version to the system page like 34+ does. Owner picks.
Accept: no message claims a state Sonora cannot read.

### B6. S1 — Long-OK "close" in the app switcher closes nothing
A long OK on a switcher card calls `killBackgroundProcesses` and fades the
card out (`AppSwitcherView.java:257`, `HomeActivity.java:1052`). On
Android 14+ that call only works on Sonora's own package; below 14 it only
kills cached processes, never the app the user just left. The card comes
back the next time the switcher opens. The UI says it closed the app.
Fix: either remove the gesture, or keep it as "remove from this list" for
the session and say so. Owner picks.
Accept: no gesture claims to close an app it did not close.

### B7. S2 — Arranging a pinned app, or moving an app into the pinned block, snaps back
`moveTile` saves the new spot into `order`, but `AppOrdering.order()` always
puts the pins first, in pin order. A move inside the pinned block, or a move
of an unpinned app in among the pins, looks right until the next rebuild
(any setting change, a trim, a new app) and then jumps back.
Fix: a move inside the pinned block rewrites the pin order; a move across
the boundary is refused at the edge (the key is used, nothing moves) or pins
the app. Owner picks.
Accept: unit test on `AppOrdering`; on TV, arrange, open Settings, change
labels, come back, order kept.

### B8. S2 — UP, DOWN and the other keys do not wake the screen saver
The in-app saver wakes on OK (second press in "On tap"), BACK and HOME only
(`HomeActivity.java:1713`). UP, DOWN, MENU, PLAY/PAUSE do nothing, so the
screen looks frozen. The system dream (`SonoraDreamService`) is worse: OK only
toggles the clock and never wakes, so BACK and HOME are the only ways out.
tvOS and Google TV wake on any button.
Fix: LEFT/RIGHT keep changing scenes, OK keeps the "On tap" rule, every other
key wakes. Same rule in both. The dead BACK branch in the saver's own key
listener goes (the activity eats BACK first).
Accept: unit-testable key → action table shared by both; on TV, each key.

### B9. S3 — DOWN from a column with nothing under it is a dead end
`Grid.moveTarget` returns -1 for DOWN when the next row is shorter
(`GridTest` asserts this). With the Settings tile always last, a home screen
whose last row is short has dead ends above it. tvOS and Google TV move to
the last tile of the next row. Owner decision: keep the current rule or
match tvOS. If changed, `GridTest` changes with it.

### B10. S3 — BACK acts on a cancelled key-up
`HomeActivity`, `ToolActivity` and `ShareActivity` act on every BACK
`ACTION_UP` without checking `isCanceled()` or that the down was tracked.
The input system drops most orphan ups, but a cancelled up (focus moved while
held) still pops a screen or scrolls the grid.
Fix: `startTracking()` on down, act on up only when tracked and not
cancelled. One helper in `ui/` used by all three.

### B11. S3 — "App info" always throws the app list away
Opening App info from the tile menu clears the app cache and bumps the epoch
before the user has done anything there (`HomeActivity.java:1278`), so every
return is a full package scan with artwork. With B4 in place this can go:
the change check finds what really changed.

### B12. S3 — Search misses accented names
`AppSearch` lower-cases but does not fold accents, so "tele" misses
"Télé" and "cafe" misses "Café". Fix: `java.text.Normalizer` NFD and strip
combining marks, on both sides. Pure, unit-testable.
Non-Latin names cannot be typed on the on-screen keyboard at all; that is a
limit to state in the UI (a Bluetooth keyboard already works through
`getUnicodeChar`), not a bug to fix now.

## C. Edge-case matrix

Each case is checked against the code. "OK" means the code handles it;
"Bx" points at the bug above; "TV" means it can only be closed on the TV.

| # | Situation | Where | Result |
|---|---|---|---|
| C1 | App installed while Sonora is in the background | `AppRepository` | B4 |
| C2 | App uninstalled from system Settings | `AppRepository` | B4 |
| C3 | App updated with a new banner or name | `AppRepository` | B4 |
| C4 | A package with two launcher activities | `AppRepository.collect` | OK: one tile per package; the leanback entry wins |
| C5 | No apps at all (only the Settings tile) | `updateShelf` | OK: "No apps yet" |
| C6 | 150+ apps | `buildGrid`, `layoutRows` | TV: every pin or unpin rebuilds every tile view (`buildGrid` makes new `AppTileView`s); measure on LOW |
| C7 | Right-to-left language | `dpadKey`, label anchoring | OK in code; TV |
| C8 | Font scale 1.3 | tiles, shelf, settings rows | TV: shelf title is a fixed 44 sp, labels a fixed 22 dp band |
| C9 | 720p, 1080p, 4K, odd densities | `Grid.resolve` | OK (unit-tested) |
| C10 | Display mode or density change while open | `onConfigurationChanged` | OK: recreate |
| C11 | Light/dark switched in Settings | `onResume` | OK: recreate |
| C12 | Memory trim `RUNNING_CRITICAL` while on screen | `onTrimMemory` | Degrades to LOW, then the next resume sees a new tier and recreates the whole screen. Intended? Owner check |
| C13 | Trim `MODERATE` while hidden, then HOME | `onTrimMemory`, `onResume` | OK: grid reloads |
| C14 | Process killed in Settings, then BACK | `HomeActivity.onCreate` | OK: `stateNotNeeded` |
| C15 | Double HOME while recents are loading, then BACK | `handleBack` | OK: generation bump |
| C16 | HOME during the saver | `onHomeSignal` | OK |
| C17 | Saver up, user presses UP/DOWN/PLAY | saver key listener | B8 |
| C18 | System dream up, user presses OK | `SonoraDreamService` | B8 |
| C19 | Wallpaper photo deleted or permission revoked | `applyWallpaper` | OK: falls back to a bundled scene |
| C20 | Backup restored to a new TV (`allowBackup=true`) | `Prefs` | OK for the wallpaper URI (falls back); stored order and pins name packages that may not exist yet: OK, they are skipped |
| C21 | Share: leave and return quickly | `ShareActivity` | B1 |
| C22 | Share: Wi-Fi drops while a file streams | `FileServer` | OK: socket timeout, worker ends |
| C23 | Share: phone video player seeks | `FileServer` | No `Range` support, so seeking and resuming a large download fail. Gap, see D5 |
| C24 | Share: a LAN client sends headers forever | `FileServer.handle` | Header count unbounded; one worker spins until the socket times out. S3 |
| C25 | Share: the address changes every visit | `new ServerSocket(0)` | A random port each time, so a bookmark never works. See D5 |
| C26 | Files: USB stick pulled during a copy | `FileOps` | OK in code (earlier passes); TV |
| C27 | Files: symlink to a folder deleted | `FileOps.deleteTree` | OK: the link goes, not its target |
| C28 | Arrange mode across the pinned block | `moveTile` | B7 |
| C29 | Pin order | `toggleFavourite` | B2 |
| C30 | Force stop from the app manager | `PackageActions` | B5 |
| C31 | Switcher long-OK | `AppSwitcherView` | B6 |
| C32 | Google TV re-enables its own launcher after a reboot | platform | Known limit, stated in README. No fix without an accessibility service, which the rules exclude |
| C33 | Typing from the Google TV phone remote | `SearchOverlay` | No text field, so no IME connection: the phone keyboard cannot type. State it, or add a hidden `EditText` (owner decision) |
| C34 | Clock across midnight, DST, time-zone change | `GlassPillView` tick | OK: reads the time each tick, aligned to the minute |
| C35 | 24-hour setting changed in system Settings | `Prefs.clock24` | OK: the system value is followed until the user picks one in Sonora |
| C36 | Cancelled BACK up | three activities | B10 |

## D. Performance (1 GB first)

Measured numbers stay in `docs/perf/`. These are code-level costs found in
the read; each needs a before/after with `scripts/perf.sh` on the TV.

- **D1. Cold app load does three package-manager calls per app.**
  `AppRepository.collect` resolves the launcher activities, then asks
  `getLeanbackLaunchIntentForPackage` and `getLaunchIntentForPackage` for
  each package, which the query already answered. Two IPCs per app saved
  on every cold load.
- **D2. A pin rebuilds every tile.** `applyOrder` → `buildGrid` makes a new
  `AppTileView` per app while `layoutRows` already knows how to reuse them.
  Reorder the existing tiles instead.
- **D3. Recents decode 192 px icons on every tier.** `RecentApps.describe`
  ignores LOW, where the home grid already halves icons.
- **D4. `SimpleDateFormat` is built on every clock tick** in
  `GlassPillView` and `ControlCenter`; with seconds on that is one per
  second. Build it when the pattern changes.
- **D5. Share server** (also a feature gap): a stable preferred port with a
  random fallback, and single-range `Range` requests. Small, no
  dependencies, and it makes phone playback work.
- **D6. B4's change check must be cheap.** `getChangedPackages` is one IPC
  and returns null when nothing changed; the rebuild only runs on a change.

## E. Against the other launchers

### E1. Who they are

From the sources below and the Copilot comparison in PR #8 (which this
audit re-checked against the same public pages):

| Launcher | What people choose it for | Where Sonora stands |
|---|---|---|
| Google TV (stock) | Recommendations, Continue Watching, Gemini, profiles | 7× lighter (README numbers). No Watch Next: a third-party app cannot read it on Google TV |
| Projectivy | Most customisable; ad-free; wallpaper providers, folders, categories, input switching, parental time limits, backups; optional accessibility service for the HOME button; some features paid | Fewer knobs; no accessibility service; free and GPL |
| ATV Launcher / Pro | Clean, popular, rows and tiles, TV-input switching in its status bar | Similar scope; Sonora adds Control Center, switcher, Files |
| FLauncher / LTvLauncher / Arc | Open source (Flutter); categories, row/grid, TV inputs, OLED saver, HOME-button fix via accessibility | Sonora has no Flutter runtime and a fraction of the memory |
| AT4K | Fast on old hardware, Watch Next where the device allows | Comparable aim; Sonora measured |
| CleanYourTV | Per-brand debloat scripts, tiny launcher, 31 languages | Sonora is the launcher product; English only |

What the lists ask for (gist by denilsonsa, roundups): folders or tags,
sorting inside a row, phone apps as well as TV apps, more than three per row,
easy App info and Force close, a clock with the date, no ads, no intrusive
permissions, open source.

### E2. Gaps worth closing, inside the rules

| # | Gap | Rule check | Size |
|---|---|---|---|
| E2.1 | **Signed release** on GitHub Releases with a real `versionCode`; today it is adb only and `versionCode 1` | Fits | M |
| E2.2 | **TV inputs** (HDMI/AV) in Control Center on TVs that have them, via `TvInputManager` + passthrough URI; absent on sticks, and the UI says so | Fits; API 23 | M |
| E2.3 | **Folders** on the home grid | Fits | L |
| E2.4 | **Back up and restore the layout** (pins, order, hidden, settings) to a file the Files app can see | Fits; no network | S |
| E2.5 | **Translations**: strings are hard-coded in Java (1 `R.string` use) | Fits; large mechanical change | L |
| E2.6 | **TalkBack**: 16 content descriptions in the whole app | Fits | M |
| E2.7 | **App lock / PIN** for chosen apps | Fits, but a launcher cannot stop launches from elsewhere; say so | M |
| E2.8 | Fixed share port + QR code drawn by hand | Fits (QR encoder by hand, zero deps) | M |

### E3. Gaps that stay closed, and why

- **Watch Next / Continue Watching / channel rows:** reading other apps'
  programs needs a signature permission on Google TV. Not possible; README
  already says so.
- **HOME-button fix via accessibility service:** breaks "no Service except
  the DreamService" and is the thing README says Sonora does not need.
- **Weather, Unsplash wallpapers, data-usage widgets:** need the network.
- **Profiles:** Android TV user switching needs system permissions.

## F. Owner decisions needed before phase 2

1. Delete the ten branches in A and close PRs #6, #8, #9 (A1)?
2. Redo `files-audit-fixes` (#7) and the README restyle (`ccr-9ed4ac55`) as
   owner commits, or drop them?
3. Dependabot: keep, security-only, or off (A4)?
4. Move `.devin/skills/docs-sync` to `.agents/skills/` (A5)?
5. Force stop (B5): honest wording on 23–33, or system page everywhere?
6. Switcher long-OK (B6): remove, or "remove from list"?
7. Arranging across pins (B7): refuse at the edge, or pin the app?
8. DOWN on a short last row (B9): keep, or match tvOS?
9. Phone-remote typing (C33): add a hidden text field, or state the limit?
10. Which E2 items, in what order?
11. Phase 3 builds: allow `dl.google.com` for this environment, or rely on
    CI only?

## Sources

- [Android Authority: Projectivy, the cleanest Android TV launcher](https://androidauthority.com/projectivy-cleanest-android-tv-launcher-not-google-3684205)
- [Android Authority: ATV Launcher v0.1.41 (TV input switching)](https://androidauthority.com/atv-launcher-v0-1-41-update-3715420)
- [AT4K Launcher on Google Play](https://play.google.com/store/apps/details?id=com.overdevs.at4k)
- [Projectivy release notes 4.70](https://newreleases.io/project/github/spocky/miproja1/release/4.70), [4.64](https://newreleases.io/project/github/spocky/miproja1/release/4.64)
- [Projectivy on APKMirror (4.72 notes)](https://www.apkmirror.com/apk/spocky/projectivy-launcher-android-tv)
- [LtvLauncher v2026.05.01 release](https://newreleases.io/project/github/leanbitlab-org/LtvLauncher/release/v2026.05.01)
- [List of alternative launchers for Android TV (gist)](https://gist.github.com/denilsonsa/80cbb3a14b3bf63de75e6bf81c89ee05)
- [Channels community: Wolf / ATV / Basic launcher review](https://community.getchannels.com/t/review-of-wolf-launcher-atv-basic-launcher/27926)
- [Google: new Gemini features on Google TV, March 2026](https://blog.google/products-and-platforms/platforms/google-tv/new-gemini-features-march-2026/)
- [AppleInsider: tvOS 26 hands-on](https://appleinsider.com/articles/25/06/18/tvos-26-hands-on-sleek-liquid-glass-redesign-new-control-center-and-more)
- [Apple Newsroom: tvOS 26](https://www.apple.com/newsroom/2025/06/apple-tv-brings-a-beautiful-redesign-and-enhanced-home-entertainment-experience)
