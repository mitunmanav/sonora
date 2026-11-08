# Edge cases — fifth pass (2026-11-06)

Two parallel audits over `files-core/` — one on copy/move/delete/name logic,
one on the Activity/sheets/cells — with the same code walked by hand. The
prime suspects this time were the fixes landed in pass 4 itself, which had
never been audited. Findings verified against the final code; several were
found and fixed mid-flight by both sides independently.

## Fixed

| # | Where | What |
|---|---|---|
| 1 | `FileOps.copyTo` guard | The pass-4 self-copy guard compared each child to the *current level's* `into`, so a destination nested deeper inside the source through a second path spelling (bind mount) still recursed forever. The copy's **root** is now threaded through as `guard` and every child is inode-compared to it, at any depth. `sameInode` moved to `lstat` on purpose — a link keeps its own inode, so it falls through to the usual "links are not copied" abort rather than slipping silently past it — and gained an NIO `fileKey` fallback so it also answers where `Os` cannot run (unit tests, <26). |
| 2 | `FileOps.copyTo` → `moveTo` | When the dir-name retry landed the copy under a different name, `deleteCopied` was still handed the stale `destination` — comparing source children against a *foreign* dir and, in the worst case, deleting files the copy never wrote. `copyTo` now returns the `File` it actually landed as and `moveTo` deletes against that. |
| 3 | `FileLogic.freeName` | An unbounded `for` on `taken()`: a folder that answers "taken" to every name (a drive it cannot stat) loops forever on the job thread or starves `lister` — and the cancel flag can never reach it. Capped at 999 candidates; every creation site re-checks `taken` for real, so a taken answer fails honestly instead of looping. |
| 4 | `FileLogic.isLink` | `Files.isSymbolicLink` answers `false` on **any** error — an uninspectable path read as "not a link", exactly the direction the helper exists to prevent. Replaced with `readAttributes(NOFOLLOW_LINKS)`, which throws on error and falls through to the fail-safe tail. |
| 5 | `FileLogic.nameProblem` | DOS strips the stem's trailing dots/spaces before the device-name check, so `con .txt`, `aux .log`, `com1 .x`, `nul..x` still name devices on the Windows side of a FAT stick. Stems are now stripped the same way before comparing; `CONIN$`/`CONOUT$` added. |
| 6 | `FileOps.deleteCopied` | A file child could match a directory counterpart and recurse into it (unreachable in practice — a file can't coexist with a same-named dir — but the guard now says what it means: kind must match, then length for files). |
| 7 | `FileOps.delete` + `FilesActivity` | A delete job's progress sheet sat frozen at 0 forever — `delete` took no `Progress`. It now reports item counts (`i of N`, current name), honest for a job whose bytes are unknown. |
| 8 | `FilesActivity` long-press | `entries.get(position)` unguarded on the long-click — a press landing between a list shrink and its layout crashed. Now goes through the bounds-checked `adapter.getItem`. |
| 9 | `ItemAdapter.getView` / `ItemCell.bind` | A recycled cell for a row beyond the new list end kept drawing the file it showed last — the wrong name where OK opens things. `bind` now accepts null and clears the cell. |
| 10 | `FilesActivity.askPaste` | The menu pick cleared `clipboard` and hid the button **before** `runJob` could refuse it — paste during a running job's Stop-window silently forgot the held file. The clipboard is now only released when the pick was "Forget It" or the job will actually run. |
| 11 | `FilesActivity.onDestroy` | `quickLook.close()` was never called on recreate — a Quick Look open through a config change leaked its decoder thread. Now closed with the other cancellations. |
| 12 | `FilesActivity.complain` | A deferred failure alert (rename/mkdir answers on `lister`) could stack over a sheet opened meanwhile, leaving a hidden modal the `free` gate can't see. `complain` now cancels a still-open sheet first — except a running job's progress sheet, which is never worth a user's work. |
| 13 | `Places.locations` | `/Android/` was cut at `indexOf` — a mount whose own name contains `Android` lost its real root and exposed its parent. `lastIndexOf` now. And a `MEDIA_MOUNTED_READ_ONLY` internal drive is listed again (browsable, just not writable) instead of vanishing. |
| 14 | `FilesActivity` smalls | `reportProgress` moved to `elapsedRealtime` (a clock change can no longer freeze or flood the bar). `showAccessNeeded` now moves focus to the one button left on screen. Empty sidebar sections no longer print bare headings. |

## New tests

- `sameInodeKnowsAFileFromALink` — a file is itself, another file is not, and a
  link is never the file it points at (the lstat semantics the guard relies on).
- `aSizeAlreadyStoppedCountsNothing`, `aCopyOntoAFileFailsHonestly`.
- `namesOldWindowsDevicesKeepAreRefused` extended for `CONIN$`, `conout$.bat`,
  and the trailing space/dot stems (`con .txt`, `nul..x`).

## Checked and not bugs

- `copyTo`'s dir-retry cannot merge into a foreign dir or loop: `mkdirs` runs
  at most four times and the post-loop `isDirectory` check fails honestly.
- `moveTo` per-source `sizeOf` under cancel returns partial → `need` shrinks →
  the space check is skipped but `copyTo`'s own cancel check stops it cold.
- A bind-mounted *ancestor* of the destination inside the source copies its
  whole contents minus the destination — bounded, honest, and the space check
  usually refuses it first. Noted, not fixed.
- `FileProvider.fileFor` decodes URIs correctly (`getPath` is decoded);
  `getType` maps a name to a mime and discloses nothing; strict `"r"` is the
  only legal read mode anyway.
- `FilesActivity.generation`, `entries`, `selection`, `running`, `sizing`,
  `Sheet.closed` — all confined to the UI thread or `AtomicBoolean`; the
  agent's re-verification matched pass-4's.
- `showQuickLook` close-before-open: `onClose` reads the field while it still
  points at the closing instance — removeView can't grab the wrong view.
- `runJob` completion `free` gate is airtight for the cases it was built for;
  the only desync was the stacked-sheet path, closed via `complain`'s guard.
- `deleteCopied` partial-delete on cancel leaves a coherent source tree —
  only children whose counterparts verifiably landed ever get deleted.

## Noted, not fixed

| What | Why |
|---|---|
| `isFile()`→`FileInputStream` TOCTOU — a same-instant swap to a FIFO wedges the open | Needs a local adversary racing `mkfifo` mid-read; same class as the pass-4 deleteTree TOCTOU, and `O_NONBLOCK` semantics would corrupt a real FIFO read anyway. |
| A late `reportProgress` post can paint one frame into the next job's sheet | ~100 ms cosmetic overlap in a window that needs Stop+re-paste in the same second. |
| `FilesTheme.inkTertiary`/`separator` are dead fields | Harmless; flagged for the next time the file is touched, per the dead-code rule. |
| `pastedName` can predict a name the copy then moves past | Cosmetic — the selection lands one row off after a mid-op name grab. |
| `Places`/`crumbs`/`File.equals` remain lexical | Alias spellings can mis-crumb; cosmetic, needs canonical plumbing for marginal gain. |
| `Uri.getPath()` decode behavior on API 23–26 | Documented and confirmed; only worth a device check if a unit harness lands for `FilesProvider`. |

## Verified

`:files-core:testDebugUnitTest` — 44 tests, 0 failures (3 new since pass 4).
`:files:assembleDebug` + `:app:assembleDebug` — green.
`codegraph sync` — clean. Not verified on the TV — the job/cancel/paste
windows and a mount-alias self-copy still need a slow USB stick on hardware.
