# Requirements from the owner, 2026-11-04

Written down as asked ("please note all of these requirements").

## Status (updated 2026-11-05, commits through `4d903cf`)

| # | Requirement | Status |
|---|---|---|
| 1 | Settings icons | Done — per-page marks, redrawn gear, dead gear dropped from the clock. Seen on the TV. |
| 2 | Search | Done — own one-line keyboard (`SearchKeys`, `SearchKeyboard`), layout matches the UI. Seen on the TV. |
| 3 | Focus animation | Done — lean, parallax and sheen removed; every card behaves the same. This is also the biggest perf win. |
| 4 | Dark and light mode | Done — `ui/Theme`, Appearance page with Dark/Light. The rest of the look is unchanged. |
| 5 | Edge cases | Done as far as walked — the third round fixed the crashes, stale screens and silent failures; details in `docs/handoffs/HANDOFF-2026-11-04.md` and `docs/plans/PLAN.md`. |
| 6 | Performance | Done and measured — `DeviceTier` LOW/MID/HIGH, tier-gated effects, `docs/perf/2026-11-04.md`. No real 1 GB TV has been available; LOW was forced on a 2 GB TV. |
| 7 | Apple-styled file manager | Done — `files-core`, Finder-style grid/list, sheets, quick look. Rename/New Folder, USB drives and real thumbnails are built but not all seen on the TV. |
| 8 | Separate app, also bundled | Done — `com.mitun.files` ships standalone and inside Sonora's Tools. |
| 9 | Reorganize the folder | Done — `docs/`, `files/`, `files-core/`, `scripts/`; dead code removed 2026-11-05 (`SonoraProvider`, unused drawables and strings). |
| 10 | AGENTS.md / CLAUDE.md linked | Done — `CLAUDE.md` is a symlink to `AGENTS.md`. Linux/WSL only. |

## Launcher

1. **Settings icons**: optimize them. They look unfinished and do not match the rest.
2. **Search**: optimize it. The layout is cramped and does not match the UI around it.
3. **Focus animation**: the lower apps (Sony LIV, VLC, YouTube, Z5, Settings) animate on
   the icon when the focus moves; the top row does not appear to. Remove it. Every card
   behaves the same, and the same as tvOS.
4. **Dark and light mode**: add a toggle. The rest of the look stays as it is.
5. **Edge cases**: find and fix them across the app.

## Performance

6. Real optimization, not visual polish. Smooth on devices with 1 GB, 2 GB, 3 GB of RAM and
   above. **1 GB is the design target**; the others follow from it.

## File manager

7. An Apple-styled, instantly recognizable file manager.
8. A **separate Android app**, which can **also be bundled** with the launcher.

## Repository

9. Reorganize the folder locally.
10. Update `AGENTS.md` and `CLAUDE.md` and link them, so Claude reads the same file.
    `AGENTS.md` is the one the owner uses. Linux and WSL only: nothing tailored for Windows.
