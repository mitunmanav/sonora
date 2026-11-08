# Edge cases — fourth pass (2026-11-06)

Two parallel audits focused on the file manager: one over `files-core` core
logic (`FileOps`, `FileLogic`, `Marks`, `Places`, `FilesProvider` + tests), one
over the UI (`FilesActivity`, `Sheets`, `QuickLook`, `ItemCell`, `FileIcon`,
`FilesTheme`, `files/MainActivity`), plus a hand walk of the same code and the
manifests. Prior passes' fixes were verified still in place; everything below
is new this pass.

## Fixed

| # | Where | What |
|---|---|---|
| 1 | `FileOps.moveTo` + `deleteCopied` | Cross-drive move re-listed the source tree at delete time: a file created inside it mid-copy was deleted without ever being copied. `deleteCopied` removes only children whose counterparts exist in the destination (same length for files), so late arrivals keep the source tree standing — and the move honestly reports "could not remove the original". |
| 2 | `FileLogic.isLink` + `FileOps.moveTo`/`freeName`/`askRename`/`askNewFolder` | `exists()` is false for a dangling link, so `freeName` picked its name and POSIX `renameTo` silently replaced the link. `isLink` moved to `FileLogic` and now asks `Os.lstat` first (canonical paths cannot see a dead link — it needs the target to exist), falls back to `Files.isSymbolicLink` and the old canonical trick, and errs on "link" when nothing can answer (delete/copy no longer walks through an unstat-able path). `FileLogic.taken` = `exists \|\| isLink` now gates `freeName`, the rename fast path, and both input-sheet "name taken" checks. |
| 3 | `FileOps.copyTo` + `sameInode` | Copying a folder into itself through a *non-lexical* alias (bind mount, two mount spellings of one tree) slipped past the canonical `isInside` guard: `mkdirs` then `listFiles` sees its own destination and recurses until ENAMETOOLONG. Each copied child is now inode-compared to the destination (`Os.stat` dev+ino; also catches a link pointing at it) and skipped. |
| 4 | `FileOps.moveTo` | The cross-drive space check measured every source against the *batch* total per source, so two 8 GB files onto 10 GB free refused both. Now `sizeOf(source)` per source. |
| 5 | `FileOps.copyTo` | A dir whose name was taken between `freeName` and `mkdirs` failed the whole copy; it now retries the next free name like a file does (≤4). `mkdirs`/`listFiles`/`setLastModified` also gained the `SecurityException` guards the file branch already had — an unchecked throw used to skip the `deleteTree` cleanup and leave a partial copy. |
| 6 | `FilesActivity.runJob` completion | After Stop/BACK on a wedged job, its completion ran `backToItems()` unconditionally — stealing focus from a QuickLook the user opened in the window. A second QuickLook overwrote the field without removing the first: zombie full-screen pane, dead keys, leaked decoder thread. `showQuickLook` closes any existing one; completion always reloads but only refocuses/complains when `quickLook == null && sheet closed`, else toasts the failure. |
| 7 | `FilesActivity.askNewFolder` deferred runnable | The "cancel whatever sheet is up" line could cancel a *running job's* progress sheet started during the free-name disk wait — stopping real work and stranding the input sheet. Now bails when `running != null` (job keeps its sheet; the ask dies quietly). |
| 8 | `FilesActivity.runJob` | A second job could overwrite `progress`/`running` while the first still reported into them. Refused with a toast; one job at a time, as the modal UI already implied. `onDestroy` can now cancel every job there is. |
| 9 | `Sheets.input` `finish` | An IME editor-action queued before Cancel landed after it and ran the rename/create anyway. No-ops on a closed sheet. |
| 10 | `FilesActivity.onResume` | A sheet open during resume suppressed the whole places rebuild + relist, and nothing re-armed it — stale rows until the *next* pause. Refresh now runs under a sheet (it touches nothing the sheet owns); only the focus restore stays gated. |
| 11 | `FilesActivity` / `Sheets.Progress` / QuickLook close | Progress-sheet cancel was the only sheet that never refocused (focus stranded on the decor for the wedge's rest); now `backToItems()`. QuickLook's close called `itemsView.requestFocus()` even when the list was GONE — now `backToItems()`, which falls back to the toolbar. |
| 12 | `ItemAdapter.getView` | `entries.get(position)` unguarded where `getItem` bounds-checks — now uses `getItem` and returns the cell unbound for a stale position. |
| 13 | `FilesActivity` threads + visibility | `lister`/`thumbnailer` are daemon (a wedged `listFiles()` on a dead mount can't hold the process). `ItemCell.entry`, `QuickLook.generation`, `FilesActivity.lastProgressAt` are volatile — each is written on one thread and read on another. `onItemsKey` rejects a selected position past the shrunken list end. |
| 14 | `FileLogic.nameProblem` | FAT/exFAT reserved device names refused: CON/PRN/AUX/NUL/COM1–9/LPT1–9 incl. with extensions (a USB stick's Windows side can't open them otherwise). A NUL byte now reports "control characters" instead of "a slash". |
| 15 | `Places` | Dead sidebar rows: favourites only for dirs that exist; "TV Storage" only while mounted. And `inside(internal, top)` no longer hides a real drive whose mount sits under the internal tree — only its canonical twin is dropped. `sideRows.get(0)` guarded now that a sidebar can legitimately be empty. |
| 16 | `FilesProvider.query` | Ignored `projection`, returning both columns for any ask — a client projecting something else could read a missing column. Now returns exactly the asked columns (DISPLAY_NAME/SIZE mapped, others null). |
| 17 | `FilesActivity.showInfo` | `modified <= 0` showed "Modified Jan 1, 1970"; now the same "--" the rows show. |
| 18 | `FilesActivity.onTrimMemory` | `thumbs.evictAll()` fired even while visible (RUNNING_*): evicted bitmaps still referenced by cells, so it bought nothing and paid re-decodes. Now only `>= TRIM_MEMORY_UI_HIDDEN` — hidden means released, visible means cached. |

## New tests

- `aMoveLeavesWhatArrivedAfterTheCopy` — an anonymous `File` whose `renameTo`
  refuses forces the copy+delete a cross-drive move does; a file created in the
  source during the copy is preserved and the move reports the rest honestly.
- `aMoveCannotTakeADanglingLinksName` — a move onto a dangling link lands on
  "a copy" and leaves the link standing.
- `namesOldWindowsDevicesKeepAreRefused`, `aDanglingLinkStillHoldsItsName` —
  `FileLogic` coverage.

## Checked and not bugs

- `FileOps.delete`'s `cancelled ||= cancel.get()` on the tail — kept: dropping
  it would report a mid-tree cancel as success. The "Stopped" toast after a
  completed batch is honest enough.
- `renameTo` fast path doesn't bump `done[0]` — rename is instant; a bar at 0
  then done is correct, not stuck.
- `FilesActivity.generation` confined to the UI thread — no volatile needed.
- `compareNatural` returns 0 for `"a01b"`/`"a1b"` — a tie, not a contract
  break; stable sort keeps input order.
- `FilesProvider.getType` doesn't validate the URI — it only maps an
  extension to a mime string; the grant check lives in `fileFor`.
- `Sheet`'s posted `requestFocus` gap — pass 1 accepted one eaten keypress.
- `sizing`/`running` fields track the latest counter/job only — earlier ones
  are daemon threads guarded by `info.closed()`/`isDestroyed()`.
- `openElsewhere` on a since-deleted file — the *receiving* app gets the
  FileNotFound, an honest report.
- Symlinked dirs stay navigable and link children still abort a folder copy —
  a consistent, tested policy.
- `EXTRA_SHARE_*` injected via the exported `MainActivity` — `setClassName`
  resolves in-package only; null keys NPE into the `RuntimeException` toast.
- No API above 23 unguarded (`Os`/`OsConstants` are 21+; `Files.isSymbolicLink`
  is behind a `LinkageError` catch for <26; `getExternalStorageState` is
  deprecated but still the only pre-30 mounted check).

## Noted, not fixed

| What | Why |
|---|---|
| `freeName` on a dir named `photos.zip` → `photos copy.zip` | Cosmetic; Finder would keep the whole name. Changing it ripples the signature. |
| `sizeOf` counts sources the transfer will skip | The upfront space check is deliberately approximate. |
| `crumbs`/`File.equals` are lexical | Alias spellings of one folder can mis-crumb; cosmetic, needs canonical plumbing everywhere for marginal gain. |
| `deleteTree` isDirectory→listFiles TOCTOU | Needs a same-instant dir→link swap; delete() on a link is itself safe. |
| Menu `ScrollView` height unbounded | Real menus here are ≤7 rows. |
| Copy aborts the whole folder on one link/fifo/unreadable child | Deliberate all-or-nothing, covered by tests. |

## Verified

`:files-core:testDebugUnitTest` — 41 tests, 0 failures (3 new).
`:files:assembleDebug` + `:app:assembleDebug` — both green.
`codegraph sync` — clean. Not verified on the TV — the focus/job races are a
cancel-window walk that needs a slow USB stick on hardware.
