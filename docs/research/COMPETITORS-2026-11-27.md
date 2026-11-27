# The other TV launchers, 2026-11-27

What people pick instead of Sonora, and why. Read from public store pages,
release notes and roundups on 2026-11-27. No competitor was installed or
measured on a TV for this note; Sonora's own numbers are in
`docs/perf/2026-11-05.md`.

## Who they are

| Launcher | What people choose it for | Where Sonora stands |
|---|---|---|
| Google TV (stock) | Recommendations, Continue Watching, Gemini, profiles; a "Short videos" row is coming to Home | About 7× lighter on the same stick. No Watch Next: a third-party app cannot read it on Google TV |
| Projectivy | The most settings: layouts, categories, folders (4.71), wallpaper providers, input switching, parental time limits, automatic backups (4.72). An optional accessibility service for remote shortcuts. Some features paid | Fewer knobs; no accessibility service; free and GPL |
| ATV Launcher / Pro | Clean and popular; rearrangeable rows and tiles; TV-input switching in its status bar (0.1.41) | Similar scope; Sonora adds Control Center, the switcher and Files |
| FLauncher, LTvLauncher, Arc | Open source (Flutter); categories, row or grid, TV inputs (LTv 2026.05.01), an OLED saver, a HOME-button fix through accessibility | No Flutter runtime, a fraction of the memory |
| AT4K | Fast on old hardware; Watch Next where the device allows it | The same aim; Sonora has measured it |
| CleanYourTV | Per-brand debloat scripts, a 572 KB launcher, 31 languages | Sonora is the launcher itself; English only |

The wish list in the community launcher list (gist by denilsonsa): folders or
tags, sorting inside a row, phone apps as well as TV apps, more than three
per row, App info and Force close close at hand, a clock with the date, no
ads, no intrusive permissions, open source. Its author found no launcher that
does all of it.

## Gaps Sonora can close

Tracked as tickets 25–32 in `.scratch/audit-2026-11-27/issues/`: a signed
release, TV inputs, folders, layout backup, translations, TalkBack, an app
lock, a QR code for the share address.

## Gaps that stay closed

- **Watch Next, Continue Watching, channel rows.** Reading other apps'
  programs takes a signature permission on Google TV.
- **A HOME-button fix through an accessibility service.** The rules allow no
  service but the DreamService, and not needing one is the point.
- **Weather, online wallpapers, data-usage widgets.** They need the network.
- **Profiles.** Android TV user switching needs system permissions.

## Sources

- [Android Authority: Projectivy](https://androidauthority.com/projectivy-cleanest-android-tv-launcher-not-google-3684205)
- [Android Authority: ATV Launcher v0.1.41](https://androidauthority.com/atv-launcher-v0-1-41-update-3715420)
- [AT4K Launcher on Google Play](https://play.google.com/store/apps/details?id=com.overdevs.at4k)
- [Projectivy 4.70](https://newreleases.io/project/github/spocky/miproja1/release/4.70), [4.64](https://newreleases.io/project/github/spocky/miproja1/release/4.64), [4.72 notes](https://www.apkmirror.com/apk/spocky/projectivy-launcher-android-tv)
- [LtvLauncher v2026.05.01](https://newreleases.io/project/github/leanbitlab-org/LtvLauncher/release/v2026.05.01)
- [CleanYourTV](https://github.com/Gathin23/cleanyourtv)
- [List of alternative launchers for Android TV](https://gist.github.com/denilsonsa/80cbb3a14b3bf63de75e6bf81c89ee05)
- [Google: Gemini on Google TV, March 2026](https://blog.google/products-and-platforms/platforms/google-tv/new-gemini-features-march-2026/)
