# Sonora architecture

This document describes the current repository layout, application boundaries, and implementation rules. Use `GLOSSARY.md` for project terminology and `docs/plans/PLAN.md` for the append-only work log.

## Repository layout

| Path | Responsibility |
|---|---|
| `apps/launcher/` | Android application `com.mitun.sonora`. Launcher screens, UI, tools, and screensaver. |
| `apps/files/` | Standalone Android application `com.mitun.files`. A thin host for Files. |
| `libraries/files-core/` | Shared Files implementation used by both applications. |
| `scripts/` | TV access, deployment, and performance measurement tools. Linux and WSL only. |
| `docs/` | Architecture, setup, research, performance records, plans, and agent conventions. |
| `site/` | GitHub Pages website. |
| `.github/` | CI, Dependabot, and repository templates. |
| `.scratch/` | Tracked feature specifications and issue notes. |
| `reference/` | Vendor APKs for study. Git-ignored. |

Gradle project names are kept as `:app`, `:files`, and `:files-core` for stable task names. `settings.gradle` maps those names to the directories under `apps/` and `libraries/`.

Place each independently installable Android application under `apps/`. Put Android libraries shared by multiple applications under `libraries/`. Keep build and TV tooling under `scripts/`, and keep research, setup, plans, and measured results under `docs/`.

## Applications and shared library

- **Launcher (`apps/launcher/`)**: home screen, app grid, Control Center, app switcher, local search, settings, tools, and screensaver.
- **Files (`apps/files/`)**: standalone entry point for the shared Files application.
- **Files core (`libraries/files-core/`)**: file browsing and management UI, storage access, and components shared by the standalone app and launcher.

The launcher depends on `:files-core`; the standalone Files app also depends on `:files-core`. Keep reusable file-management behavior in the library rather than duplicating it in the application modules.

## Launcher surfaces

- **Home screen** (`HomeActivity`): hero wallpaper, app tiles, dock, clock, and local app search.
- **Control Center** (`ui/ControlCenter`): quick settings and routes to core launcher functions.
- **App switcher** (`ui/AppSwitcherView`): recent apps, opened from the Home button interaction.
- **Search** (`ui/SearchOverlay` and `SearchKeyboard`): local app-name search.
- **Settings and tools** (`tools/`): device information, setup guidance, Files, app management, and system settings.
- **Screensaver** (`ui/ScreensaverView` and `dream/SonoraDreamService`): bundled scenes and optional clock.

Pure logic belongs outside Activities and is unit-tested. UI uses Android platform views and APIs.

## Rules the code keeps

- **No third-party runtime dependencies.** Platform APIs only. `ui/Spring` implements the spring behavior directly.
- **No background work.** The DreamService is the only service. The LAN file server runs only while its Share screen is visible.
- **No network by default.** LAN sharing starts only when the user opens it. No analytics.
- **`minSdk 23`.** Check platform availability before using a new API.
- **Low-memory behavior is required.** `DeviceTier` selects LOW, MID, or HIGH. Every costly effect needs a LOW answer.
- **No Apple assets.** Use observed behavior as reference and draw original interface marks.
- **Measure performance on the `perf` build.** Record device, method, and limits in `docs/perf/`.

## Documentation map

- `README.md`: project overview, installation, build, and repository layout.
- `GLOSSARY.md`: domain vocabulary.
- `docs/plans/PLAN.md`: append-only session history.
- `docs/plans/REQUIREMENTS-*.md`: dated owner requirements and status.
- `docs/perf/`: measured results and methods.
- `docs/research/`: design, platform, and competitive research.
- `docs/agents/`: repository conventions for agent skills.
