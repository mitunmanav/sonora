# Sonora: working rules

`CLAUDE.md` only loads this file. Edit this one.

## Layout

- `app/`: the launcher. Pure logic at the package root, views in `ui/`, screens in `tools/`.
- `files/`: the standalone Files app. A thin shell around `files-core/`.
- `files-core/`: the file manager itself, bundled into both apps.
- `scripts/`: deploy, TV control and performance tools. `bench.py` runs the
  paired benchmark in `perf-ab.md`. Linux and WSL only.
- `docs/`: `ARCHITECTURE.md` (what exists + the rules), `research/` (design and performance notes), `plans/` (running log
  and dated requirements), `perf/` (measured numbers), `agents/` (tracker/domain conventions).
- `reference/`: vendor APKs for study. Git-ignored.
- `site/`: the GitHub Pages site (`mitunmanav.github.io/sonora`), deployed from `master` by `.github/workflows/pages.yml`.
- `.github/`: CI (tests, release build, `Sole author` check), Dependabot config, issue templates.
- `README.md`: the human-facing overview. `GLOSSARY.md`: the domain vocabulary — use its terms.

## Before changing shared code

Check every caller of a shared symbol such as `Prefs`, `Motion`, `ScreenStack` or `Theme`
before changing it (CodeGraph where available, otherwise grep). Grep for string literals and resources.

## Build and test

Build and test in WSL only (Windows has no Java). The commands are in `.github/workflows/ci.yml`.

## Rules the code keeps

- **Zero dependencies.** Platform APIs only, which is why `ui/Spring.java` solves the spring by hand.
- **No background work.** No Service except the DreamService, no receivers, no alarms. Timers
  stop when their window is hidden. The LAN server dies with its screen.
- **No network** unless the transfer client is switched on. No analytics, ever.
- **`minSdk 23`.** Check the API level of anything new.
- **Pure logic lives outside the Activity** and is unit-tested.
- **Say what a device cannot do** in the UI. Never ship a fake feature.
- **No Apple assets.** Measure and imitate behaviour; draw every mark ourselves.

## Performance: 1 GB first

The design target is a 1 GB TV. `DeviceTier` picks LOW, MID or HIGH from the RAM, and each
costly effect asks it. A new effect has a LOW answer before it ships.

- Animate RenderNode properties (scale, alpha, translation): they skip the redraw.
- A focus change redraws at most the two cards involved.
- `clipPath`, a `saveLayer` from alpha on overlapping content, and elevation at rest are the
  costs that have hurt here. Round with a `BitmapShader` on `drawRoundRect`.
- Decode and bake on a background thread. Nothing screen-sized on the main thread.
- Hidden means released: see `HomeActivity.onTrimMemory`.

Measure the `perf` build with `scripts/perf.sh`. A debug build runs uncompiled and misleads.
Record results in `docs/perf/`.

## Working with the TV

The owner is often using the TV, and OK on the wrong tile opens their app. `scripts/tv.sh` provides
`tv`, `keys` and `lit`. `keys` sends only while the app named by `APP` has the focus. Reach screens
through Control Center, where a miscount cannot open an app, and check `lit` before any OK: the grid
order changes whenever an app is installed. Leave the TV on Sonora's home screen when you finish.

## Theme

Colours come from `ui/Theme`, in both dark and light. A focused surface is white with dark ink
in both. The home screen wallpaper stays as it is in both.

## Agent skills

- **Subagents**: always set `model` to `gpt-6-luna` and `reasoning_effort` to `high`.
  If that combination is unavailable, work in the current agent.
- **Issues and specs**: markdown under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.
- **Triage labels**: the default strings, as `Status:` lines. See `docs/agents/triage-labels.md`.
- **Domain docs**: `GLOSSARY.md` at the root, ADRs in `docs/adr/`. See `docs/agents/domain.md`.
- **Documentation upkeep**: before finishing a session that changed code, docs or layout, follow
  `.agents/skills/docs-sync/SKILL.md` — append the `PLAN.md` session entry, update the requirements
  status table, and record perf numbers when measured.
