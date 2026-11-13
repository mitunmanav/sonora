# Files app pass

Status: ready-for-agent — code side only; the on-device check needs the TV and a USB stick.

A dedicated pass over `files/` + `files-core/` — the standalone Files app and
the copy bundled inside Sonora's Tools — for tvOS-26 look, edge cases and
D-pad behaviour. Separate from the launcher work in PLAN sessions 20–21.

## Scope

- Every screen: grid, list, sidebar/toolbar, sheets, quick look, rename/new
  folder, USB drives, share sheet, QR/pairing if present.
- Focus paths: no stranded focus, BACK always one step, focus restored after
  pickers and returning from the system.
- Type scale (`ui/Type` or the files-core equivalent), RowDrawable focus
  treatment, dark/light theme parity.
- Edge cases from `docs/research/TVOS-DESIGN-RESEARCH.md`: empty states,
  permission loss, long names, font scale, RTL.
- On-device check with `scripts/tv.sh`; USB and thumbnails were built but not
  all seen on the TV (REQUIREMENTS row 7).

## Out of scope

- New file-manager features. This is a polish/edge-case pass only.

## Answered

- Does files-core share `ui/` widgets or duplicate them? Duplicates them:
  `files-core` imports nothing from `com.mitun.sonora`, so the standalone
  app builds alone. Its colours are `FilesTheme`, not `ui/Theme`.
- Edge-case passes 4–6 already walked `files-core` for logic and focus
  races; this pass is the look and D-pad path, not another logic audit.
