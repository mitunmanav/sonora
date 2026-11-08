# Sonora: working rules

`CLAUDE.md` is a symlink to this file. Edit this one.

## Layout

- `app/`: the launcher. Pure logic at the package root, views in `ui/`, screens in `tools/`.
- `files/`: the standalone Files app. A thin shell around `files-core/`.
- `files-core/`: the file manager itself, bundled into both apps.
- `scripts/`: `deploy.sh`, `perf.sh`, `tv.sh`, `bench.py`, `perf-ab.md`. Linux and WSL only.
- `docs/`: `ARCHITECTURE.md` (what exists + the rules), `research/` (design and performance notes), `plans/` (running log
  and dated requirements), `perf/` (measured numbers), `agents/` (tracker/domain conventions).
- `reference/`: vendor APKs for study. Git-ignored.
- `site/`: the GitHub Pages site (`mitunmanav.github.io/sonora`), deployed from `master` by `.github/workflows/pages.yml`.
- `.github/`: CI (tests, release build, `Sole author` check), Dependabot config, issue templates.
- `README.md`: the human-facing overview. `GLOSSARY.md`: the domain vocabulary — use its terms.

## Explore with CodeGraph first

Before you read, grep or edit in this project, run CodeGraph. It returns the source of the
symbols you name, the call paths between them, and the blast radius.

```bash
codegraph explore "HomeActivity buildGrid moveFocus"
codegraph sync /home/mitun/aerial   # after each batch of edits, before you read again
```

The `codegraph_explore` MCP tool gives the same output. Check the blast radius before
changing a shared symbol such as `Prefs`, `Motion`, `ScreenStack` or `Theme`. Use grep for what
CodeGraph does not return: string literals, resources, files with no symbols.

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

- **Issues and specs**: markdown under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.
- **Triage labels**: the default strings, as `Status:` lines. See `docs/agents/triage-labels.md`.
- **Domain docs**: `GLOSSARY.md` at the root, ADRs in `docs/adr/`. See `docs/agents/domain.md`.
- **Documentation upkeep**: before finishing a session that changed code, docs or layout, follow
  `.devin/skills/docs-sync/SKILL.md` — append the `PLAN.md` session entry, update the requirements
  status table, and record perf numbers when measured.

## Behavioural guidelines

Caution over speed. For trivial tasks, use judgment.

### Think before coding

Don't assume. Don't hide confusion. Surface tradeoffs.

- State assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### Simplicity first

Minimum code that solves the problem. Nothing speculative.

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If 200 lines could be 50, rewrite it.

Ask: would a senior engineer call this overcomplicated? If yes, simplify.

### Surgical changes

Touch only what you must. Clean up only your own mess.

- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.
- Remove imports/variables/functions that YOUR changes made unused; leave
  pre-existing dead code alone unless asked.

Test: every changed line traces directly to the user's request.

### Goal-driven execution

Define success criteria. Loop until verified.

Transform tasks into verifiable goals: "add validation" → write tests for invalid
inputs, then make them pass; "fix the bug" → write a test that reproduces it.

For multi-step tasks, state a brief plan with a verify check per step:

```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work")
require constant clarification.
