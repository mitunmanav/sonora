# Android TV launcher and file manager comparison

**Reviewed:** 2026-11-24

## Executive summary

Sonora sits at the intersection of two product categories: Android TV home apps and TV-friendly file managers. Its most useful distinction is the combination of a native home app, launcher controls, utility screens, and a shared Files app that is also shipped on its own. Sonora is open source, uses Android platform APIs without third-party runtime dependencies, and has measured performance results against Google TV on one device.

It is not the most customizable launcher, the smallest launcher, or the broadest file manager in this group. CleanYourTV is stronger in guided ADB debloating and device profiles. LTvLauncher provides a broader set of integrations and visual options. Projectivy focuses on customization. X-plore and AnExplorer offer wider file-operation and storage-protocol coverage.

## Scope and method

This review uses public GitHub repositories, project READMEs, and the Google Play listings linked below. The project facts were checked on 2026-11-24. No competing application was installed or measured on a TV for this report. Sonora's performance figures are from its existing Mi TV Stick comparison with Google TV, not from a comparison with the products below. GitHub popularity counts are not used as a quality or performance measure.

The projects are grouped by their primary job. A launcher and a file manager are not interchangeable, even when one application includes both.

## Launcher comparison

| Product | Primary focus and documented strengths | Comparison with Sonora |
|---|---|---|
| [CleanYourTV](https://github.com/Gathin23/cleanyourtv) | Guided Android TV and Google TV cleanup, backups, reversible package disabling, device-specific lists, and a small native Java launcher. The project describes its launcher as about 572 KB and targets Android 8.0 or later. | The closest overlap in cleanup and launcher replacement. Sonora also includes a debloat screen, while CleanYourTV centers its workflow on guided device cleanup and model-specific setup. |
| [Glass TV](https://github.com/aqmangge/glass-tv-home) | A recently active Android TV project combining a frosted-glass launcher with debloating, a phone-based installer, weather and air-quality screens, and localized religious content. Its README documents network-backed information and companion setup flows. | Similar visual direction and a combined launcher/tool concept. Sonora's documented design emphasizes local operation, restrained background behavior, and an integrated Files app. Glass TV documents a wider set of online and regional features. |
| [FLauncher](https://gitlab.com/flauncher/flauncher) and [LTvLauncher](https://github.com/leanbitlab-org/LtvLauncher) | FLauncher is an open-source Android TV launcher built with Flutter. LTvLauncher is a GPL-3.0 fork that documents categories, themes, Watch Next, weather, notification overlays, TV inputs, and an OLED screensaver. It also documents an accessibility-based Home button fix for some devices. | Both are direct launcher alternatives. LTvLauncher has more documented integrations and customization. Sonora differentiates through its platform-only Java implementation, Control Center and tools, plus Files shared with a standalone app. |
| [Projectivy Launcher](https://play.google.com/store/apps/details?id=com.spocky.projengmenu) | A Google Play launcher described as ad-free, customizable, and configurable with layouts and dynamic wallpapers. Its listing describes an optional accessibility service for remote-control shortcuts. | A polished, customization-focused alternative distributed through Google Play. Sonora's source is public under GPL-3.0 and its measured low-memory behavior is documented. This review did not independently verify Projectivy's internal architecture or performance. |

## File manager comparison

| Product | Primary focus and documented strengths | Comparison with Sonora Files |
|---|---|---|
| [X-plore File Manager](https://play.google.com/store/apps/details?id=com.lonelycatgames.Xplore) | Broad storage and transfer support, including dual-pane browsing, FTP, SMB, cloud storage, USB OTG, archives, and Wi-Fi sharing. | The stronger choice for network storage and advanced file workflows. Sonora Files is designed as a TV-oriented companion to the launcher, with icon and list views, quick look, USB support, and optional LAN sharing. |
| [AnExplorer](https://github.com/1hakr/AnExplorer) | An open-source file manager that documents support for Android TV alongside phones, tablets, Chromebooks, and Wear OS. Its README lists broad local file operations and storage options. | A wider cross-device file manager. Sonora's differentiator is that the same TV-focused Files module can be opened from the launcher or shipped as a separate app. |

## Sonora's position

**Strengths**

- One project provides an Android HOME app, launcher tools, and a shared file manager with a standalone app target.
- The source is GPL-3.0 and uses Android platform APIs without third-party runtime dependencies.
- Search is local. The LAN file server runs only while the Share screen is open.
- Performance results include a repeatable device, workload, and caveats rather than an unsupported cross-product claim.

**Trade-offs**

- Sonora does not provide Watch Next or HDMI input rows.
- The Files app is not intended to match X-plore's breadth of remote-storage protocols.
- A signed public release is not available yet, and some Google TV devices may restore their stock launcher after reboot.
- The 1 GB target has not been validated on physical 1 GB hardware.

## Practical conclusion

Choose Sonora when the goal is a source-available, low-dependency Android TV home app with an integrated file manager and measured results. Choose a specialist alternative when its primary strength matters more: CleanYourTV for guided debloating, Glass TV for its combined frosted-glass and regional-content direction, LTvLauncher for launcher integrations, Projectivy for customization, or X-plore for advanced network storage.

The next meaningful evidence for Sonora is not another feature checklist. It is a signed release, real-device testing on 1 GB hardware, and repeatable same-device comparisons with the closest alternatives.

## Sources

- [Sonora README](../../README.md), [architecture](../ARCHITECTURE.md), and [performance results](../perf/2026-11-05.md)
- [CleanYourTV repository and README](https://github.com/Gathin23/cleanyourtv)
- [Glass TV repository and README](https://github.com/aqmangge/glass-tv-home)
- [FLauncher project](https://gitlab.com/flauncher/flauncher)
- [LTvLauncher repository and README](https://github.com/leanbitlab-org/LtvLauncher)
- [Projectivy Launcher on Google Play](https://play.google.com/store/apps/details?id=com.spocky.projengmenu)
- [X-plore File Manager on Google Play](https://play.google.com/store/apps/details?id=com.lonelycatgames.Xplore)
- [AnExplorer repository and README](https://github.com/1hakr/AnExplorer)
