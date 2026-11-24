# Setup guide

Sonora targets Android TV devices running Android 6.0 or later (API 23+). The project does not yet publish a signed release APK, so installation currently uses a locally built APK and ADB.

## Build and install

From the repository root, build the release-like performance APK:

```bash
./gradlew :app:assemblePerf
```

Connect ADB to the TV, install the APK, and select Sonora as the Home app:

```bash
adb connect <tv-ip>:5555
adb install -r apps/launcher/build/outputs/apk/perf/app-perf.apk
adb shell cmd package set-home-activity com.mitun.sonora/.HomeActivity
```

You can also select Sonora in the device's Home app settings. Menu names vary by manufacturer. Some Google TV devices may restore their stock launcher after a reboot.

## First use

Open Sonora's Control Center to reach Settings and Tools. The setup wizard provides device-specific guidance. Search is local and searches installed app names.

The Files app is available from Tools. It can also be installed separately from a release build of `:files`:

```bash
./gradlew :files:assembleRelease
```

The standalone APK is written to `apps/files/build/outputs/apk/release/files-release.apk`.

## Notes

- Sonora's app switcher may show a more complete recent-app list when Usage Access is granted. The permission is optional.
- LAN file sharing is available only while the Share screen is open.
- Sonora does not include Watch Next or HDMI input rows.
- The 1 GB memory target has not been tested on physical 1 GB hardware.

For project details and test commands, see the [README](../README.md). For measured performance, see [`docs/perf/`](perf/).
