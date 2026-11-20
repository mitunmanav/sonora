<div align="center">

# Sonora

**A free, open-source home screen for Android TV, built the way tvOS works.**<br>
A glass dock, Control Center, an app switcher and an aerial screensaver, in plain Java with zero third-party dependencies.

[Website](https://mitunmanav.github.io/sonora) · [Setup guide](docs/SETUP-GUIDE.md) · [Architecture](docs/ARCHITECTURE.md) · [Measured numbers](docs/perf/2026-11-05.md)

[![CI](https://github.com/mitunmanav/sonora/actions/workflows/ci.yml/badge.svg)](https://github.com/mitunmanav/sonora/actions/workflows/ci.yml)
[![License: GPL-3.0](https://img.shields.io/github/license/mitunmanav/sonora)](LICENSE)

<img src="site/img/control-center.webp" width="1100" alt="Sonora's Control Center on a TV: a large Screen Saver tile, Wi-Fi, Bluetooth, Sound and Display pills, and round Search, Apps and Settings buttons over the home wallpaper.">

<sub>Control Center on the test TV. Every tile does something the TV can actually do.</sub>

</div>

<table>
  <tr>
    <td width="25%" valign="top"><b>Light on a 1 GB TV</b><br>About 41 MB at idle, against about 181 MB for Google TV.</td>
    <td width="25%" valign="top"><b>No ads, no tracking</b><br>No analytics and no outbound connections, ever.</td>
    <td width="25%" valign="top"><b>Zero dependencies</b><br>No AndroidX, Compose or Kotlin. Platform APIs only, Android 6.0+.</td>
    <td width="25%" valign="top"><b>Open source</b><br>GPL-3.0. Anyone can read what it does.</td>
  </tr>
</table>

I wanted my TV to feel like tvOS without a launcher that eats half the RAM and fills the screen with ads. Nothing free did that, so I built this.

## What it does

- **Home screen:** hero wallpaper, glass dock, app tiles and a top shelf with clock and search. The grid opens from the dock into one smooth full-screen scroll.
- **Control Center:** Screen Saver, Wi-Fi, Bluetooth, Sound, Display, Search, Apps and Settings in one panel.
- **App switcher:** double-press Home for glass cards of recent apps. OK switches, long OK closes.
- **Search** app names with its own one-line keyboard, all on the device. The voice key shows only when an assistant answers.
- **Settings and tools:** Home Screen, Clock, Screen Saver, Apps, Debloat, Device info, App manager and a first-run setup wizard.
- **Screensaver:** five aerial photographs with an optional clock, also usable as the TV's system screensaver.
- **Files:** a Finder-style file manager with USB drives and quick look, plus a Share screen that sends files to your phone over LAN.

## How it compares

Same Mi TV Stick (Android 11, 2 GB RAM), same debloated system, same 12-key walk across the home screen. Only the launcher changed.

| | Sonora | Google TV |
|---|---|---|
| Launcher memory at idle | ~41 MB | ~181 MB |
| Launcher memory after the walk | ~37 MB | ~275–288 MB |
| Frames missing their deadline | ~0.9% | ~6–8% |
| Slowest 1% of frames | ~23 ms | ~105–200 ms |
| Memory left free for apps | ~900 MiB | ~810 MiB |
| Accessibility service to catch Home | Not needed | Not needed |
| Open source | Yes | No |

<sub>Method, caveats and raw `dumpsys` output in [docs/perf/2026-11-05.md](docs/perf/2026-11-05.md). Google TV's process also hosts its recommendation rows; Sonora does none of that work. The 1 GB tier is tuned and forced-LOW tested on a 2 GB TV; no real 1 GB hardware has been through it yet. CleanYourTV is the other big name: it centres on per-brand debloat scripts and a deliberately minimal launcher, while Sonora is the full launcher with a lighter debloat screen.</sub>

## Get started

There is no signed release yet, so you build it and install over adb.

1. Build the `perf` APK (see [Build from source](#build-from-source)).
2. Connect and install:
   ```bash
   adb connect <tv-ip>:5555
   adb install -r app/build/outputs/apk/perf/app-perf.apk
   adb shell cmd package set-home-activity com.mitun.sonora/.HomeActivity
   ```
3. Press Home.

On Google TV the stock launcher can be turned off with `adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx` (`pm enable` brings it back); it may re-enable itself after a reboot. The [setup guide](docs/SETUP-GUIDE.md) covers permissions and details.

## Privacy

- No account, ads, analytics or tracking. Nothing is sent anywhere.
- The only network use is the Files Share screen, which serves files on your LAN while it is open and stops when you leave it.
- No background services except the screensaver, no receivers, no alarms.
- Features the TV denies are stated in the UI. Nothing is faked.

## Build from source

Linux or WSL, JDK 17. Gradle 8.13 via the wrapper; the only test dependency is JUnit.

```bash
./gradlew :app:testDebugUnitTest :files-core:testDebugUnitTest
./gradlew :app:assembleRelease :files:assembleRelease
```

The `perf` build type is the release build signed with the debug key, so it installs over a debug build without a keystore. It is the build to measure: `scripts/perf.sh` for a quick read, `scripts/bench.py` with `scripts/perf-ab.md` for the full A/B protocol.

| Path | What it is |
|---|---|
| `app/` | The launcher (`com.mitun.sonora`): home, dock, search, settings, screensaver, app manager, file server. |
| `files-core/` | The Files file manager, shared by both apps. |
| `files/` | Standalone Files app (`com.mitun.files`), a 48 KB shell around `files-core`. |
| `scripts/` | Deploy, perf and TV helper scripts. |
| `docs/` | Architecture, setup, plans, research and perf numbers. Start at [docs/README.md](docs/README.md). |
| `site/` | The GitHub Pages site, served from `master`. |

## Roadmap

Not there yet: Watch Next and HDMI-input rows, a signed release, and testing on real 1 GB hardware. The running log is in [docs/plans/PLAN.md](docs/plans/PLAN.md).

## Help and contributing

Found a bug or have a device report? [Open an issue](https://github.com/mitunmanav/sonora/issues). Pull requests are not merged; a fix arrives as a commit of the maintainer's own. See [CONTRIBUTING.md](CONTRIBUTING.md), and report security problems privately as described in [SECURITY.md](SECURITY.md).

## Credits

Written by [Mitun Manav](https://github.com/mitunmanav). Screensaver photographs under CC BY-SA 4.0: Dietmar Rabich (Dülmen), Tasha~visual (Ngorongoro), Syrio (Vigo di Fassa), Medoffer (Zhuravlyne Lake), Bhullargraphic (Punjab). Not affiliated with Apple or Google; every mark is drawn for this project.

## License

[GPL-3.0](LICENSE). Use, share and change it freely; shared copies and changed versions must stay under the same license.
