# Sonora — system architecture

What exists, how it fits together, and the rules it is built under.
`GLOSSARY.md` has the vocabulary; `docs/plans/PLAN.md` is the running log of
what was done session by session.

## Modules

| Path | What it is |
|---|---|
| `app/` | The launcher (`com.mitun.sonora`). Pure logic at the package root, views in `ui/`, screens in `tools/`, screensaver in `dream/`. |
| `files-core/` | Android library: the whole Files file manager, shared by both apps. |
| `files/` | Standalone Files app (`com.mitun.files`) — a thin shell around `files-core`. |
| `scripts/` | `deploy.sh`, `perf.sh`, `tv.sh`. Linux and WSL only. |
| `docs/` | `plans/` (log + requirements), `perf/` (measured numbers), `research/` (design/perf notes), `agents/` (tracker/domain conventions), this file. |
| `.scratch/` | Tracked efforts: one directory per feature, `spec.md` + `issues/`. |
| `reference/` | Vendor APKs kept for study. Git-ignored. |

## The launcher, screen by screen

- **Home screen** (`HomeActivity`): hero wallpaper, glass dock, app tiles,
  top shelf (title, search pill, clock pill), chevron. The grid opens from a
  bottom dock into a full-screen scroll, one scroll value driving dock,
  rows, wallpaper and glass together.
- **Control Center** (`ui/ControlCenter`): tvOS 26 panel — large Screen
  Saver tile, Wi-Fi/Bluetooth/Sound/Display pills, round Search/Apps/Quick
  settings/Settings buttons. Every tile does something the TV can really do.
- **App switcher** (`ui/AppSwitcherView`): glass cards of recent apps,
  double-press Home; OK switches, long OK closes.
- **Search** (`ui/SearchOverlay` + `SearchKeyboard`): local app-name search,
  own one-line keyboard, no network. Voice key only exists when an
  assistant answers `ACTION_ASSIST`.
- **Settings** (`tools/SettingsPanel` + `ui/ScreenStack` +
  `ui/SettingsHomeView`): index of pages in the tvOS split layout — centred
  title, list on the right, per-page glyph on the left. Pages: Quick
  settings, Home Screen, Clock, Screen Saver, Apps, Tools, System, Mode.
- **Tools** (`tools/`): Device info (`DebugPanel`), Setup guide, Files,
  Debloat, Setup wizard (first run), App manager (`AppPanel`).
  All navigate through one `ScreenStack`; BACK is always one step.
- **Choice picker** (`ui/ChoiceOverlay`): the one picker for every
  either/or value; restores focus to the row that opened it.
- **Screensaver** (`ui/ScreensaverView` + `dream/SonoraDreamService`):
  bundled aerial scenes with credit, optional clock overlay, also
  selectable as the TV's system dream.

## Rules the code keeps

- **Zero dependencies.** Platform APIs only — `ui/Spring` solves springs by
  hand, no AndroidX, no Compose.
- **No background work.** No Service except the DreamService, no receivers,
  no alarms. Timers stop when the window is hidden; the LAN server dies
  with its screen.
- **No network** unless the transfer client is switched on.
  No analytics, ever.
- **`minSdk 23`.** Check the API level of anything new.
- **Pure logic lives outside the Activity** and is unit-tested
  (`app/src/test/`).
- **Say what a device cannot do** in the UI. Never ship a fake feature.
- **No Apple assets.** Measure and imitate behaviour; draw every mark
  ourselves (`ui/Glyph`, `files-core/Marks`).
- **1 GB first.** `DeviceTier` picks LOW/MID/HIGH from RAM; every costly
  effect has a LOW answer. Animate RenderNode properties; round with
  `BitmapShader`; decode and bake off the main thread; hidden means released
  (`HomeActivity.onTrimMemory`). Measure with `scripts/perf.sh`, record in
  `docs/perf/`.

## Docs map

- `README.md` — human overview, build/test commands.
- `AGENTS.md` / `CLAUDE.md` — working rules for agents (symlinked).
- `GLOSSARY.md` — domain vocabulary.
- `docs/plans/PLAN.md` — running session log, append-only.
- `docs/plans/REQUIREMENTS-*.md` — dated owner requirements with status.
- `docs/perf/` — measured numbers with method and device.
- `docs/research/` — tvOS design research, platform performance research.
- `docs/agents/` — issue-tracker and domain-doc conventions.
- `.devin/skills/docs-sync/` — the doc-upkeep checklist every session ends with.
- `.scratch/` — `benchmark-scripts/`, `files-pass/`, `browser-pass/` specs.
