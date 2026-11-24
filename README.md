# Sonora

Sonora is an open-source Android TV launcher and file manager. Its tvOS-inspired home screen, Control Center, app switcher, local app search, screensaver, and Files app are written in Java with Android platform APIs. The project has no third-party runtime dependencies, ads, or analytics.

![Sonora on the test TV](site/img/control-center.webp)

Project site: <https://mitunmanav.github.io/sonora>

## What Sonora includes

- **A native home screen:** app grid, dock, wallpaper, clock, and local app search.
- **Control Center and app switcher:** quick access to TV controls and recent apps.
- **Settings and tools:** device information, setup guide, app manager, and a lightweight debloat screen.
- **Screensaver:** five bundled aerial scenes, with attribution in Settings.
- **Files:** a remote-friendly file manager with icon and list views, quick look, USB support, and optional LAN sharing. The same Files app is available inside Sonora and as a separate Android app.
- **Low-memory design:** `DeviceTier` selects LOW, MID, or HIGH rendering behavior based on device RAM. The design target is a 1 GB TV.

Sonora registers as an Android home app and does not need an accessibility service to receive Home when selected as the default launcher. The system's home-app settings and behavior vary by device.

## Performance

These measurements compare Sonora with Google TV on the same Mi TV Stick running Android 11 with 2 GB RAM. Both runs used the same debloated system and the same 12-key home-screen walk.

| Measurement | Sonora | Google TV |
|---|---:|---:|
| Launcher memory at idle | About 41 MB | About 181 MB |
| Launcher memory after the walk | About 37 MB | About 275 to 288 MB |
| Frames missing their deadline | About 0.9% | About 6 to 8% |
| Slowest 1% of frames | About 23 ms | About 105 to 200 ms |
| Memory left free for apps | About 900 MiB | About 810 MiB |

Google TV also hosts recommendation rows and channel logos. These figures are not a head-to-head benchmark against other third-party launchers. The 1 GB tier has been forced and tested on a 2 GB TV; no physical 1 GB device has been measured. The full method and caveats are in [the performance results](docs/perf/2026-11-05.md).

## Install on a TV

Sonora does not yet publish a signed release. Build the performance APK and install it over ADB:

```bash
./gradlew :app:assemblePerf
adb connect <tv-ip>:5555
adb install -r apps/launcher/build/outputs/apk/perf/app-perf.apk
adb shell cmd package set-home-activity com.mitun.sonora/.HomeActivity
```

Some Google TV devices may restore the stock launcher after a reboot. See the [setup guide](docs/SETUP-GUIDE.md) for device notes and the [site](https://mitunmanav.github.io/sonora) for screenshots.

## Build and test

Linux and WSL are supported. The project uses Gradle 8.13 through its wrapper and Java 17. Runtime modules use Android platform APIs only. JUnit is used for unit tests.

```bash
./gradlew :app:testDebugUnitTest :files-core:testDebugUnitTest
./gradlew :app:assembleRelease :files:assembleRelease
```

Gradle project names remain `:app`, `:files`, and `:files-core` to keep build commands stable. Their source directories are organized under `apps/` and `libraries/`.

## Repository layout

| Path | Purpose |
|---|---|
| `apps/launcher/` | Sonora launcher application, package `com.mitun.sonora`. |
| `apps/files/` | Standalone Files application, package `com.mitun.files`. |
| `libraries/files-core/` | Shared Files implementation, bundled by both applications. |
| `docs/` | Architecture, setup, plans, performance records, and research. |
| `scripts/` | Linux and WSL TV, deployment, and performance tools. |
| `site/` | Project website, published through GitHub Pages. |
| `.github/` | CI workflows and repository templates. |

## Known limitations

- No Watch Next or HDMI input rows.
- No signed public release yet.
- Google TV may re-enable its stock launcher after a reboot.
- The 1 GB design target has not been tested on physical 1 GB hardware.

See the [competitive analysis](docs/research/COMPETITIVE-ANALYSIS-2026-11-24.md) for a sourced comparison with other Android TV launchers and file managers.

## Project documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Setup guide](docs/SETUP-GUIDE.md)
- [Performance records](docs/perf/)
- [Research](docs/research/)
- [Glossary](GLOSSARY.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)

## License and credits

Sonora is licensed under GPL-3.0. Screensaver photographs are credited in Settings and are licensed under CC BY-SA 4.0. Sonora is not affiliated with Apple or Google. All interface marks are original drawings.
