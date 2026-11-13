# Edge cases — pass 6, the file manager

Status: done — fixes landed (PLAN session 29); the "noted, not fixed" items stay as the record. TV walk still owed.

Two-agent sweep over `files-core/` (core agent + UI agent + independent
parent walk), with the pass-5 fixes as the prime suspects. Prior pass
reports in `.scratch/edge-cases-4/plan.md` and `.scratch/edge-cases-5/plan.md`
were read first; nothing already fixed or deliberately deferred is
re-reported here.

## Fixed

### Core (FileOps / FilesProvider)

- **Dir-copy retry could merge into — then delete — a foreign folder.**
  The retry loop produced a fifth candidate (`into` after `tries == 4`) that
  was never `mkdirs`'d and never `taken`-checked, and the post-loop
  `isDirectory()` check *passes on a pre-existing dir*. Children then copied
  into somebody else's folder; an abort's `deleteTree(into)` would have
  removed its contents. Only a folder this call created may now be filled:
  `made` tracks `mkdirs()` per candidate and exhaustion fails honestly.
  Triggered by `freeName`'s cap answer landing on an existing dir, or a
  racer creating each returned name. Regression test:
  `aCopyRefusesAFolderItDidNotCreate` (999 pre-existing dirs).
- **A `new String[0]` projection got a 1-row, 0-column cursor** from
  `FilesProvider.query` — legal but strange. No columns now means no row.

### UI (FilesActivity / FileOps progress)

- **`onResume` → `open()` → `showAccessNeeded()` stole focus under a
  sheet.** A resume while a dialog is up is deliberate (pass 4), but the
  access screen's new `focusContent()` moved keys to a hidden window — the
  hidden-modal class. Gated on no open sheet, same as the other focus moves.
- **Boot and every recreate listed the folder twice.** `listed` is set in
  `show()`, so the first `onResume` (before the lister's answer lands)
  always queued a second `listFiles` behind the first — the pass-1 fix
  never covered the window. A `requested` marker is now set at submit and
  cleared when that request resolves (or dies to a generation bump), and
  the resume gate honours it. Failure clears it, so a transient failure is
  still retried on resume — pass-2 behaviour kept.
- **`FilesActivity` and standalone `MainActivity` launched `standard` —
  stacked instances, each with a 6 MB thumb cache and executors.** Same
  class as the pass-1 browser fix; both are `singleTask` now. Extras are
  read lazily (`readPlaces`, the share-menu paths), so `onNewIntent` only
  keeps the newest intent.
- **`complain` captured its refocus target *before* cancelling the sheet
  that owned it** — a dead view, so dismissing the alert dropped focus to
  the first focusable. Captured after the cancel.
- **RTL locales inverted the D-pad edges.** The layout mirrors (sidebar on
  the right) but the key handlers moved by logical direction: LEFT went to
  the visual-right sidebar. The grid edges, toolbar edge, `SideRow` and the
  access button now follow `getLayoutDirection()` — the same swap the
  launcher's `Grid` got in pass 1.
- **DPAD_DOWN on an incomplete last grid row could eject focus** to the
  toolbar: the guard required the press be *in* the last row. Now it is
  simply "no cell below" — the press stays put.
- **Item-menu "Open" on a listed-away entry was a silent no-op** —
  `positionOf` → -1 → return. It toasts "That item is no longer there."
- **A delete's in-tree progress reset the bar to 0 between top-level
  items.** `deleteTree` now echoes the caller's `done/total` with each
  child name, so the name streams while the bar keeps the item fraction.
- **`load(null)` NPE on the lister** when Sort ran with zero places — the
  only caller that can see `folder == null`. Guarded.

## Noted, not fixed

- **`FilesProvider.openFile` `isFile()`→`ParcelFileDescriptor.open`
  TOCTOU**: a same-instant swap to a FIFO blocks the open until a writer
  connects, pinning a Binder thread. Same adversary-only class as the
  `copyTo` FIFO residual noted in pass 5 — one fix would cover both;
  left documented together.
- **`copyTo`/`deleteTree` StackOverflow on a deeper-than-stack tree**
  (a mount point cycling inside its own tree included): `runJob`'s
  `finally` reports honestly but the abort cleanup unwinds through the
  same SOE, leaving partial-copy litter. No data loss; bounded.
- **`deleteCopied` counterpart checks follow links** — a swapped-in link
  counterpart can masquerade as the copy (adversary, same-instant swap);
  no deletion *through* a link is possible (links delete themselves).
- **`sameInode` can false-positive on filesystems that synthesize/reuse
  inodes** (some FUSE/network mounts) → a child silently skipped. The
  lexical `isInside` fallback stays for null keys. No cheap fix.
- **`freeName` is bounded in count, not time**: each `taken` is up to 3
  stats that can hang on a wedged mount — the accepted mount-hang class.
- **`transfer` into a since-deleted target** resurrects the path for dir
  sources (`mkdirs` makes the chain) but fails for files — honest both
  ways, asymmetric. Cosmetic.
- **`pastedName` vs `landed` divergence** — a capped/renamed landing
  selects a foreign row afterward. Cosmetic, pass-5 class.
- **`complain` over a running job buries the progress sheet** (Stop
  unreachable until the alert is answered; the `sheet` field then holds a
  closed alert while progress lives — self-heals through view dispatch;
  verified). Deliberate: a message never cancels work.
- **Manifest absorbs `density`/`screenSize` changes** with no metrics
  rebuild — stale `dp()` sizes until a recreate. Cosmetic.
- **`FilesTheme` dead fields** from pass 5 — still noted only.

## Checked, not bugs (new this pass)

- `askPaste` precedence `(index == 0 && running == null) || index != 0`
  is exactly the intent; clipboard/pasteButton only mutate together.
- `onDestroy`→`quickLook.close()` is safe on finish/recreate/kill paths.
- `addSection` empty-guard: nothing assumes headings exist.
- `ItemCell.bind(null)` fully resets drawn state; pending `setThumb`
  callbacks are entry-gated.
- QuickLook's `entries` snapshot is coherent — the field is only ever
  reassigned, never mutated in place.
- Rotation/state: folder, place, clipboard(+move), `lastSelected` saved;
  a running job cancels honestly with partial-copy cleanup.
- IME hides via both tokens and dies with the window.
- All five executors daemon; ticket/`isDestroyed` gates on every post.
- `moveTo`'s `landed`→`deleteCopied` wiring verified for file and dir
  sources; `copyTo` null-returns all carry `result.fail`.
- `sameInode` NIO `fileKey` semantics match `lstat` dev+ino on Android;
  null key → false → lexical fallback (correct direction).
- `Places.lastIndexOf("/Android/")` strictly better than `indexOf`; the
  files-dir tail can't produce a later occurrence.
- `MEDIA_MOUNTED_READ_ONLY` places fail honestly end-to-end (mkdir /
  rename / createNewFile / delete).
- `FilesProvider.fileFor` canonical containment + percent round-trip
  (incl. literal `%2F` names) verified.

## Verified

- `:files-core:testDebugUnitTest` — 45 tests, 0 failures
  (`aCopyRefusesAFolderItDidNotCreate` new).
- `:files:assembleDebug` + `:app:assembleDebug` — green.
- `codegraph sync` — clean.

Not verified on the TV: the boot double-list fix, singleTask relaunch,
RTL D-pad edges, and the under-sheet access-focus gate all want a real
remote; mount-hang and FIFO-swap classes need a local adversary anyway.
