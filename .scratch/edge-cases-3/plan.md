# Edge cases — third pass (2026-11-06)

Two parallel audits (launcher core + manifest; tools/browser/dream) plus a
hand walk of `ui/`, deduplicated against `.scratch/edge-cases/` and
`.scratch/edge-cases-2/`. Every pass-2 fix was verified in place; the one
unfinished guard (`choiceOverlay != overlay` self-reference in `ToolActivity`)
was completed inside `ChoiceOverlay` instead — rows go dead on `dismiss()`,
covering both call sites.

## Fixed

| # | Where | What |
|---|---|---|
| 1 | `PackageActions.setHidden` | Unhide could never restore: no launch intent while the component was disabled sent it down the whole-app branch, whose read-back checked the wrong state. Hide now disables *all* launcher components (dual LEANBACK+LAUNCHER entries included); restore re-enables every DISABLED component via `getPackageInfo(GET_ACTIVITIES)` and verifies with a real intent. DISABLED_USER (Debloat's) is left alone. |
| 2 | `AppPanel` + `Pickers.hiddenApps` | Two hidden stores (`prefs.hidden()` vs component-disable) diverged: lying toasts, "No hidden apps" while the count said otherwise, unlistable hidden apps. The toggle reads/writes both stores; the picker lists the pref against *installed* packages and calls `setHidden(false)` so the component comes back too. |
| 3 | `SettingsPanel.choosePhotoSource` | "Device photos"/"Both" set the pref with no storage access; the saver silently played bundled scenes while the row claimed photos. Now it toasts honestly and opens Sonora's app-info page instead. |
| 4 | `HomeActivity` | Arrange mode started during the picker's dismiss fade — arrows moved a tile nobody could see. `dismissChoice(Runnable)` runs `startEditing` after the fade. |
| 5 | `HomeActivity` + `ToolActivity` | `System.currentTimeMillis()` → `SystemClock.elapsedRealtime()` for double-HOME, the idle watcher and the uninstall-report pause: clock changes can't fake presses or stall the saver. |
| 6 | `HomeActivity` ×3, `ShareActivity` | Unguarded `Thread.start()` calls now catch `RuntimeException \| OutOfMemoryError` with per-site honest degradation (empty grid / re-armed saver / cleared in-flight flag / inline stop). |
| 7 | `PackageActions.forceStop` | Checked `isRunning` only after killing: reported "Stopped" for an app that never ran. Checks first, answers "Not running". |
| 8 | Manifest + `ToolActivity`/`ShareActivity` | Both were `standard`: HOME → relaunch stacked duplicate instances with stale state. Now `singleTask` with `onNewIntent` → recreate (new intent decides the screen/share). |
| 9 | `ShareActivity` | The hint was a local and went stale across a server restart while the address refreshed; now a field recomputed in `onStart` via `hintText(port)`. `server.stop()` (≤500 ms join) moved off the UI thread onto a daemon, guarded by `stopStarted`. |
| 10 | `FileServer` | Request headers were never drained: >8 KB of headers still in flight could RST the response away on `Connection: close`. Drained to the blank line before answering (bounded by the socket timeout). The trailing-slash redirect checked the *encoded* path: `GET /dir%2F` redirected to `/dir%2F/` (double-slash hop). `askedSlash` checks the decoded path; new socket test `aFolderWithAnEncodedSlashIsServed`. |
| 11 | `SetupGuide.ipAddress` | IPv6 scope ids (`fe80::1%wlan0`) leaked into the share URL — invalid in an address bar. Stripped at `%`. |
| 12 | `BrowserActivity` | `shouldOverrideUrlLoading` scheme check was case-sensitive (`HTTPS://` fell to "needs an app"). `regionMatches` now. `WebChromeClient` gained `onGeolocationPermissionsShowPrompt` and `onPermissionRequest`: denied out loud instead of left pending forever. |
| 13 | `ScreensaverView` | TOCTOU between `isShutdown()` and `execute()`: `RejectedExecutionException` would crash after detach. Caught; `loadingNext` reset so stepping isn't stuck. |
| 14 | `SettingsPanel` Favourites | Not tagged as a settings page → never rebuilt on resume (stale ticks). And a toggle rebuilt check rows with a synchronous `favouriteRows` — a cold `AppRepository` put the package scan on the main thread. Extracted `reloadFavourites` (bg thread) used by open/toggle/`resumedFavourites`. |
| 15 | `PhotoLoader.photoPaths` | The comparator ran `new File(x).lastModified()` twice per comparison — O(n log n) filesystem stats. Precomputed into a map, one stat per file. |
| 16 | `AppManager.Item.icon` | Written on the loader thread, read on the UI thread: now `volatile`. |
| 17 | `Scenes` | Dead `hiddenFlags(Prefs, Context)` overload removed. |

## Checked and not bugs

- "About" screen can't scroll — false alarm. `ScreenStack.buildScreen` makes
  ScrollView customs focusable and `focusTop` reaches them; DPAD scrolls.
- `AppPanel`'s non-focusable `LinearLayout` wrapper — `ViewGroup.requestFocus`
  delegates to descendants; the `ListView` takes it.

## Noted, not fixed

| What | Why |
|---|---|
| `Cleanup.PACKAGES`: `com.xiaomo.tv.milegal`, `android.autinstalls.config.xioami.mibox3` look misspelled | Blindly "correcting" a disable-list package could match and disable a real system app. Needs verification against a Xiaomi device. |
| `screenRect` ignores translations | Cosmetic: the glass can lag one frame on a fast-scrolling row. Rare, harmless. |
| `glassKey` uses `identityHashCode` | A reused view id could serve a stale bake for one frame. Corrects itself on the next bake. |
| Settings tile not searchable | Intended: it is a pinned tile, not an app row. |

## Verified

`:app:testDebugUnitTest` — 118 tests, 0 failures (incl. the new
encoded-slash test). `:app:assembleDebug` green. `codegraph sync` clean.
TV walk (D-pad through overlays, arrange mode, Favourites, About, share
relaunch) still owed — no on-device verification happened this pass.
