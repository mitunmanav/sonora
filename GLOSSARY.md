# Glossary

The vocabulary this project uses. Use these terms exactly; don't drift to
synonyms.

## Screens and surfaces

- **Home screen** — the launcher grid (`HomeActivity`): hero wallpaper,
  app tiles, dock, clock capsule.
- **Tile** — one app card in the grid (`AppTileView` wrapping
  `AppIconView`, a 5:3 card).
- **Dock** — the glass bar the tiles sit on; dark at rest in both themes.
- **Shelf** — the top area above the grid: clock capsule, and the
  search/quick-settings pills that appear only while the clock has focus.
- **Hero** — the full-bleed wallpaper behind the home screen
  (`HeroBackgroundView`), with the focused app's colour wash.
- **Glass / lens** — the translucent material (`GlassDrawable`,
  `GlassLens`): dark lens at rest, near-white when focused. "Baking" a
  lens renders it once to a bitmap instead of blending every draw.
- **Screen stack** — the push/pop list navigation every tool screen uses
  (`ScreenStack`); screens slide in from the right.
- **Picker** — a `ChoiceOverlay` list for choosing one value; pickers
  restore focus to the row that opened them.
- **Row** — one `SettingRow` in a list: header, value, action, toggle,
  push, danger, choice, note or check.

## Apps and data

- **AppEntry** — one launchable app: label, package, component, icon,
  banner, accent colour.
- **Banner** — an Android TV app's 320x180 artwork, drawn inside the 5:3
  tile. Sonora never stretches one full-screen.
- **Hidden app** — an app removed from the grid but still installed;
  managed in AppPanel, restored from the unhide picker.
- **Favourite** — a ticked app shown with a star; order and favourites
  live in `Prefs`.
- **Epoch** — `apps_epoch` in `Prefs`, bumped whenever the app list must
  be re-read; part of `gridKey()`.

## Modes and tiers

- **Technical mode** — extra Settings rows (columns, clock options,
  screensaver timing, Performance) for debugging.
- **DeviceTier** — LOW / MID / HIGH, picked from RAM. Every costly
  effect has a LOW answer; the design target is a 1 GB TV.
- **Performance choice** — the Technical-mode override that pins a tier
  for measurement.
- **Theme** — `ui/Theme`: dark and light palettes. A focused surface is
  white with dark ink in both.
- **Reduced motion** — `Motion.isReduced`: springs degrade to static
  states, never to no feedback.

## Screensaver and scenes

- **Scene** — one of the five bundled CC BY-SA aerial stills (`Scenes`),
  with mandatory credit.
- **Hidden scene** — a scene the user excluded; the screensaver skips it
  (`SceneChoice`).
- **Dream** — the system daydream entry (`SonoraDreamService`) that hosts
  `ScreensaverView`; the only Service in the app.

## Files

- **files-core** — the library module holding the whole Files file
  manager (`com.mitun.files`); bundled into `app` and shipped standalone
  as `files/` (`com.mitun.files.app`).
- **Share screen** — `ShareActivity` + `FileServer`: a visible screen
  that serves files over LAN on a thread and dies with the screen.
