# Requirements from the owner, 2026-11-04

Written down as asked ("please note all of these requirements"). Status lives at the end
of `docs/handoffs/HANDOFF-2026-11-04.md`.

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
