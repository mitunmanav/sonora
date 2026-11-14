# Sonora

A tvOS 26-style home screen for Android TV, plus the tools a TV actually
needs — all in dependency-free Java. Zero third-party libraries, no
background services, no analytics.

## Modules

| Path | What it is |
|---|---|
| `app/` | The launcher (`com.mitun.sonora`): home grid, dock, search, settings, screensaver, optional browser, app manager, file server. |
| `files-core/` | Android library: the whole Files file manager, shared by both apps. |
| `files/` | Standalone Files app (`com.mitun.files`) — a thin shell around `files-core`. |

## Build and test

Linux/WSL only. Gradle 8.13 via the wrapper; all dependencies are cached,
so build offline:

```bash
./gradlew --offline :app:testDebugUnitTest :files-core:testDebugUnitTest
./gradlew --offline :app:assembleDebug :app:assembleRelease \
    :files:assembleRelease :files-core:assembleRelease
```

A `perf` build type exists on `app`, `files` and `files-core`: release
config signed with the debug key, for on-device measurement
(`scripts/perf.sh`).

## TV

The test TV is reached over adb; `scripts/tv.sh` provides `tv`, `keys`
and `lit` for safe key injection (it only sends keys while the expected
app has focus — the owner often has the remote). See `AGENTS.md` and
`docs/plans/PLAN.md` for device details and known quirks.

## Documentation

- `AGENTS.md` — working rules for agents (also `CLAUDE.md`, a symlink).
- `docs/plans/PLAN.md` — running project log; append a session section,
  never rewrite history.
- `docs/plans/REQUIREMENTS-*.md` — dated owner requirements with status.
- `docs/perf/` — measured performance numbers.
- `docs/research/` — tvOS design and performance research notes.
- `docs/agents/` — conventions for the local issue tracker (`.scratch/`)
  and domain docs (`GLOSSARY.md`, `docs/adr/`).
- `GLOSSARY.md` — the project's domain vocabulary.
- `reference/` — vendor APKs kept for study (git-ignored).
