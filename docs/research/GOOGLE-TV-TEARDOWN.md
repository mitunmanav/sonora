# Google TV launcher — full teardown

What the stock Google TV launcher actually is, and what of it is worth taking.

**Artifact.** `com.google.android.tvlauncher` ("Android TV Home"), version
`7.7.15-968255986-f` / versionCode `1010943512`, compileSdk 37 (DEV), minSdk 29,
`android.software.leanback` required.

| | |
|---|---|
| Files | `base.apk` 13.3 MB + `split_config.en.apk` 221 KB + `split_config.xhdpi.apk` 56 KB |
| Classes | 12,838 (R8-obfuscated: only 147 keep the `tvlauncher` package name, the rest are `defpackage.*`) |
| Resources | 6,874 public, 814 strings, 303 layouts, 674 drawables, 55 `values` folders |
| Own code | ~147 named classes + 20 own smali packages; the rest is Google Play Services, Firebase, Leanback, Glide, media3 |

**Method.** REA against the artifact: `inspect_android_package`,
`decode_android_resources`, `search_android_classes`, `inspect_android_class`,
`inspect_android_method` (JADX, engine 0.7.1), plus a full apktool smali decode
(13,269 files) so every string could be traced to the code that uses it.
Base APK SHA-256 `20b33a1b8a2cb345a648db71b4107a24a042df3c61005d801a4a73417ddd8019`.

---

## 1. Components

### Activities (the launcher's own, 25)

| Activity | Purpose | Exported entry point |
|---|---|---|
| `MainActivity` | the home screen | `MAIN`/`HOME`, **priority 2**, singleTask, affinity `.TvLauncher` |
| `HomeScreenSettingsActivity` | home screen settings | `com.android.leanbacklauncher.SETTINGS`, `com.android.tvlauncher.SETTINGS` |
| `AppsViewActivity` | the Apps tab | `android.intent.action.ALL_APPS` (thin wrapper — the real screen lives in `MainActivity` behind `EXTRA_SHOW_ALL_APPS`) |
| `InputsPanelActivity` | Inputs / Sources panel | `android.tv.action.VIEW_INPUTS` |
| `NotificationsSidePanelActivity` | notifications panel | `com.android.tvlauncher.NOTIFICATIONS` |
| `FavoriteLaunchItemsActivity` | favourite launch items | — |
| `FavoriteAppSelectorActivity` | "Select app" picker | — |
| `AppChannelPermissionActivity` | "Select an application" | — |
| `FreeplaySettingsActivity` | Google TV Freeplay settings | — |
| `AddAppLinkActivity` | "Approve app addition" | `android.tv.applink.action.ADD_APP_LINK`, priority 999 |
| `RemoveAppLinkActivity` | "Approve app removal" | `android.tv.applink.action.REMOVE_APP_LINK`, priority 999 |
| `OpenSourceActivity` | open-source licences | `android.view.VIEW_LICENSES` |
| `FullScreenVideoPlayerActivity`, `FreeplayActivity`, `TvodPlayerActivity`, `TrailerPlayerActivity`, `FullScreenUiActivity` | players | — |
| `EntityDetailsActivity`, `LegacyEntityDetailsActivity` | title details | — |
| `AccountSelectorActivity`, `AddAndSelectAccountActivity` | Google account picker | — |
| `WhyThisAdActivity`, `AdsPersonalizationAwarenessDialogActivity`, `RedeemTargetedPromotionActivity`, `PreferenceElicitationActivity`, `DerivedEntitlementActivity`, `OnboardingActivity`, `TutorialActivity`, `QrCodeInfoActivity`, `HomeScreenChannelsUpdateDialogActivity`, `AccountSignOutConfirmationActivity`, `ViewPermissionsActivity`, `ShowDialogsActivity`, `TvLauncherErrorActivity`, `BootHelperEmptyActivity`, `LibraryPageActivity` | ads, onboarding, errors | `tv.google.com/library` via `TvContentIntentReceiver` |

### Services / receivers / providers

- **Services** — `StopOutOfBoxOrderingJobService`, `NotifyRefreshOemConfigurationDataJobService`,
  `PhenotypeMetadataHolderService` (disabled), `ScheduledTaskService`,
  `GnpExecutorApiService`, `FirebaseMessagingService`, `SystemJobService`.
- **Receivers** — `MarketUpdateReceiver`, `PackageInstallReceiver` (both need the
  `com.google.android.tv.permission.ROUTE_MARKET_UPDATE` signature permission),
  `LastActiveInputReceiver`, `TvContentIntentReceiver`, `AccountChangedReceiver`,
  `LocaleChangedReceiver`, `UpdateReceiver`, `AppDoctorReceiver`,
  `FirebaseInstanceIdReceiver`, `SilentFeedbackReceiver` (separate `:crash_report` process).
- **Providers** — `WatsonKatnissConfigContentProvider` (**exported, no permission**),
  `GnpFileProvider`, `androidx.startup.InitializationProvider`, and the Room
  `MultiInstanceInvalidationService`.

### Permissions (23)

| Group | Permissions |
|---|---|
| Plain launcher | `QUERY_ALL_PACKAGES`, `CHANGE_COMPONENT_ENABLED_STATE`, `REQUEST_DELETE_PACKAGES`, `ACCESS_NETWORK_STATE`, `SET_WALLPAPER`, `SET_WALLPAPER_HINTS`, `GET_PACKAGE_SIZE`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW` |
| TV-specific | `MEDIA_CONTENT_CONTROL`, `READ_CONTENT_RATING_SYSTEMS`, `BIND_APPWIDGET`, `NOTIFY_TV_INPUTS`, `ACCESS_ALL_EPG_DATA` / `WRITE_EPG_DATA` / `READ_EPG_DATA`, `CHANGE_HDMI_CEC_ACTIVE_SOURCE` |
| Google-only / tracking | `INTERNET`, `AD_ID`, `c2dm.RECEIVE`, `READ_GSERVICES`, `READ_HOTWORD_STATE` |

`CHANGE_HDMI_CEC_ACTIVE_SOURCE` exists in the ROM but is `signature|privileged` —
an ordinary app can never be granted it.

---

## 2. The feature surface

Read from the 814 strings, the manifest and the layouts.

**Home** — a vertical list of *channels* (rows). `HomeListView` holds them; each
row is a `channel_layout` / `home_channel_row` / `favorites_channel_layout` /
`apps_channel_layout` / `launch_epg_card` / `continue_watching_card_layout`.
Row types seen: featured carousel (sponsored), app-provided content channels,
**Favorite Apps**, **Play Next**, **Your last input**, Continue watching.

**Top row** — `home_top_row.xml` → `HomeTopRowView` → `NotificationsTrayView`
(a `HorizontalGridView` of `NotificationsTrayItemView`) plus the settings gear
(`HomeTopRowButton`), the account particle (`AccountParticleTopRowButton`) and
the clock (`ClockView`, a `TextClock` that follows the system 12/24-hour setting
and updates on `TIME_SET` / `TIMEZONE_CHANGED` / `LOCALE_CHANGED`).

**Apps tab** — `AppsViewActivity` → `MainActivity` with `EXTRA_SHOW_ALL_APPS`.
Rows: "Installed Apps" and "Installed Games" folders (group key `installed_games`
in `isc#b`), a Play Store promo row, a server-cluster row, and an edit mode
(`apps_tab_edit_mode_grid_view` / `features.appseditmode.EditModeGridView`).

**Live** — Live tab, channel guide (EPG), hopper ("More channels"), flipper
("Press UP or DOWN on your remote to quickly flip channels"), on-now timeline,
favourite channels, `LiveChannelsDatabase` / `LiveEpgDatabase` / `EpgDatabase`.

**Discover / Shop / Freeplay** — recommendations, service selection, content
preferences, ratings, watchlist, purchases, rentals, TVOD player, Free tab.

**Inputs** — `InputsPanelActivity` + `defpackage.jxy` (presenter) + `jxr`
(`TvInputManager` wrapper) + `jxq` (the row). It lists `TvInputManager` inputs,
adds two synthetic ids of its own — `com.google.android.tvlauncher.input.home`
(the launcher as a source) and `…input.bundled_tuner` — groups by input id
(`HDMI1`, `HDMI2`), shows Connected / Disconnected / Standby, and flips through
sources with UP/DOWN. `jxs.c()` shows the interesting part: the launcher's
Inputs button prefers **another package's** `com.android.tv.action.VIEW_INPUTS`
activity and only uses its own panel as a fallback.

**Notifications** — the panel and the tray read
`content://com.google.android.tvrecommendations.NotificationContentProvider/notifications/{panel,tray,count}`.
That is **Google's own server-fed provider, not the system notification list**.

**Watch Next / previews** — `content://android.media.tv/watch_next_program` and
`content://android.media.tv/preview_program`, i.e. the **Android TV provider**.

**Accounts** — Google account selector, sign-out, add account, activity controls.

---

## 3. Exact behaviour, decompiled

### The home screen settings page (`ksd#i`)

Title `settings_dialog_title` = **"Home screen"**.

| Category | Rows |
|---|---|
| **Channels** | Customize channels · Enable video previews · Enable audio previews |
| **Google TV Freeplay** *(conditional)* | Content restrictions · More Information |
| **Discover** *(conditional)* | Customize Services · Content Preferences · View Permissions · Set up |
| **Apps** | Reorder apps · Reorder games |
| **Help & feedback** *(conditional)* | Send feedback · Visit Android TV support |
| **Open source licenses** | Android TV Home (version) · TV-recommendations licences |
| **Experimental** *(conditional)* | Optimize Home & Apps tabs |

"Reorder apps" and "Reorder games" are two separate orders; both open the Apps
screen with `EXTRA_SHOW_ALL_APPS` plus `extra_start_customize_apps` /
`extra_start_customize_games`.

### The "Customize channels" screen (`krl#i`, `krl#k`)

Title "Customize channels"; a **Play Next** on/off switch; then the category
**"Home screen channels"** with the summary *"Select which channels appear on
your Home screen. Empty channels will not be shown."* One switch per installed
app that publishes a content channel, with the app's banner and how many
channels it has. A `sponsored.legacy` channel gets a drill-down instead of a switch.

### The app long-press menu

`ihe` is a *factory*, not the menu: `ihe.j(id, view, list, callback)` builds
one item, and the caller decides the order. The Apps-tab tile menu
(`iea#J`) adds items in exactly this order:

| # | Item | String | Drawable |
|---|---|---|---|
| 1 | **Open** | `context_menu_primary_action_text` | `ic_context_menu_open_black` |
| 2 | **Move** | `context_menu_move_text` | `ic_context_menu_move_black` |
| 3 | **Add to favorites** / **Remove from favorites** | `context_menu_favorite_text` / `context_menu_unfavorite_text` | `ic_context_menu_favorite_black` / `…unfavorite_black` |
| 4 | **Info** | `context_menu_info_text` | `ic_context_menu_info_black` |
| 5 | **Uninstall** | `context_menu_uninstall_text` | `ic_context_menu_uninstall_black` |

The home-screen tile variant (`jge#J`) is: Open · **Can't move** ·
**Can't remove from favorites** · Move · Remove from favorites — the same list
with the disabled states spelled out instead of the actions.

So Google TV puts **Open first and Uninstall last**, and the favourite toggle
sits in the middle. (`ihe`'s switch has 18 cases: the same five for apps, plus
Play-Next and Watchlist variants for content cards.)

### The app-link approval dialog (`AddAppLinkActivity#onCreate`)

`R.layout.add_applink_dialog` — app icon, app screenshot, title, developer,
description, category, and three buttons: **Open / Allow / Deny**.

---

## 4. What is advertising and tracking (do not copy)

- **Ads SDK**: 150 `com.google.ads` classes (DoubleClick). The launcher's own
  code parses VAST: `doubleclick/vast/VastParser`,
  `doubleclick/proto/VideoCreative$Vast*` — 6 of the ~147 own classes.
- **Ad surfaces**: `home/featuredcarousel/*` (4 classes) with its own
  `WhyThisAdActivity`, `AdsPersonalizationAwarenessDialogActivity`,
  `home/instreambanner/InStreamBannerView`, `home/qrcodeinfo/QrCodeInfoActivity`,
  `targetedpromotions/RedeemTargetedPromotionActivity`,
  `apps_tab_promotion_row`, and `R.layout.in_feed_ad_channel`.
  Meta-data: `com.google.android.gms.ads.AD_MANAGER_APP = true`.
- **Tracking**: `AD_ID` + `INTERNET` + `c2dm.RECEIVE` + `READ_GSERVICES` +
  `READ_HOTWORD_STATE`; Firebase (installations, messaging, IID, datatransport),
  **20 Phenotype remote-config registrations** (clearcut, one.google, mdi.sync,
  consentverifier, primes, usage reporting), `AppDoctorReceiver`,
  `SilentFeedbackReceiver` in a `:crash_report` process, `boot_helper`,
  `MarketUpdateReceiver`, `StopOutOfBoxOrderingJobService`.
- **Data sharing** is described by the app itself:
  `edp_data_sharing_string` = *"Information about movie and show transactions may
  be shared amongst YouTube, Google TV, Google Play Movies & TV, and other Google
  Services…"*

---

## 5. What this TV cannot do at all

Observed on the device (MiTV-AYFR0, Android 11, API 30):

| Google TV feature | Needs | On this TV |
|---|---|---|
| Inputs / Sources panel | `media.tv_input` service | **absent** — no `TvInputManager` at all |
| HDMI input switching | `CHANGE_HDMI_CEC_ACTIVE_SOURCE` | `signature|privileged`, never grantable; and a stick has one input |
| Live TV tab, channel guide, hopper, flipper | tuner + EPG provider | **absent** — no `epg`/`tvprovider` package |
| Play Next row, video previews | `android.media.tv` provider | **absent** |
| "Customize channels" (app content rows) | the TV provider | **absent** |
| Google accounts, Discover, Shop, Watchlist, Library | network + Play services | out of scope by policy |
| Notifications panel | Google's own provider | out of scope by policy |

So the whole of Google TV's *content* half is unavailable here by hardware, and
its *network* half is unavailable by policy. What is left is the half Sonora
already has.

---

## 6. The list: what to add to Sonora, in order

### Tier 1 — do these next

| # | Feature | Google TV's version | Why here |
|---|---|---|---|
| 1 | **Uninstall from the app's long-press menu** | `iea#J` item 5 | Sonora buries it in Settings → Apps → app. Three taps for one action. The code path already exists (`PackageActions`). |
| 2 | **A visible search entry point in the top row** | the search orb (`lb_search_orb`, `view_discover_search_orb`) | Sonora's search is behind `KEYCODE_SEARCH`, which this remote's key layout does not have. A glass pill next to the clock makes it discoverable. `GlassIconPill` + the magnifier glyph already exist. |
| 3 | **Keyboard / Date & time / Language rows in the control centre** | the TV's own settings app | The TV does have these screens (`com.android.tv.settings.inputmethod.KeyboardActivity`, `…system.DateTimeActivity`, `…system.LanguageActivity`) — confirmed on the device. Two rows, `openFirst` already handles the fallback. |
| 4 | **Split the app order into Apps and Games** | "Reorder apps" / "Reorder games", folders "Installed Apps" / "Installed Games" | One flat order today. `AppOrdering` already takes an order string, so this is a second order plus a per-app flag. |
| 5 | **Reorder Sonora's app menu to Google TV's order** | Open · Move · Favourites · Info · Uninstall | Sonora's menu is Open · Favourites · Search apps · Edit Home Screen · Hide · App info. Same five actions, different order — Google puts the primary action first, which is also what tvOS does. "Search apps" leaves the per-app menu once #2 lands. |

### Tier 2 — worth it when there is time

| # | Feature | Why |
|---|---|---|
| 6 | **An "All apps" screen** (`android.intent.action.ALL_APPS`) | Google TV's Apps tab. Only pays off with many apps — this TV has 11 — but it is the screen Android expects a home app to provide. `ScreenStack` + `SettingRow` make it cheap. |
| 7 | **Handle "Approve app addition"** (`android.tv.applink.action.ADD_APP_LINK`) | Google TV shows a glass dialog with the app's icon, screenshot, title, developer and category, plus Open / Allow / Deny. Without it Sonora is an incomplete home app: an app asking for a home-screen shortcut gets no answer. Also a good excuse for a tvOS-styled dialog. |
| 8 | **"Previews" on/off** | Google TV: "Enable video previews" / "Enable audio previews". Sonora animates the hero; some people want that off. One boolean. |

### Tier 3 — deliberately not doing

Live TV, channel guide, hopper, channel flipper, Play Next, preview programs,
customizable content channels, the Google account selector, Discover, Shop,
Library, the notification panel, sponsored rows, the featured carousel, the
targeted-promotion rows, the ads personalisation notice, "Why this ad?", the
in-feed ad channel, the Play Store rows, Phenotype/Firebase/clearcut/primes
telemetry, `AD_ID`, `INTERNET` by default.

Reasons: hardware (no TV provider, no tuner, no `media.tv_input`) for the first
group; policy (no network, no trackers) for the second. Every one of them was
read out of the artifact before being ruled out — see §4 and §5.

---

## 7. One structural difference worth naming

Google TV's Home is a list of **content channels** supplied by apps; the apps
themselves live in a separate tab. Sonora's Home *is* the apps, because there
are no content channels to show on this device. Copying the tab split would
give the user an empty tab. The split only becomes worth building if a
`content://android.media.tv` provider ever appears on the device — at which
point "Customize channels" and a "Play Next" row are the two things to copy,
in that order.

---

## 8. The subtle half — read from the layouts and view code

The obvious features are in §2. These are the ones that only show up in the
layouts, the custom views and the string catalogue.

### The system bar (`system_bar_view.xml`)

Not one bar but a row: **search widget · notifications button · restricted
profile · inputs · partner widget · settings · account particle · clock**, over a
scrim (`fc_system_bar_scrim`). Every slot is `visibility="gone"` except search
and settings. The clock sits last and is right-aligned; the account particle is
a `ViewStub`, so it only inflates when there is an account.

### The pill / chip row

`pill_view.xml` and `chip_view.xml` — a horizontal row of chips, each
`icon + text + subtext`, tinted by `duplicateParentState` so the whole chip
follows the focus, driven by a `HorizontalGridView` with `focusOutFront="true"`.
`LinearChannelWithChipsView` puts one above a content row. This is the tvOS
segmented-control idiom.

### The edge fade (`FadingEdgeContainer.dispatchDraw`)

The recipe, and it is worth copying exactly: `saveLayer` the strip, draw the
content, then draw a `LinearGradient` with `PorterDuff.Mode.DST_IN` over it.
The result is a genuine alpha ramp, so a row **dissolves** at the screen edge
instead of being sliced. RTL-aware: it fades the opposite side.

### Focus search (`FocusSearchFrameLayout`, `DispatchKeyAndFocusSearchFrameLayout`)

`focusSearch` + `dispatchKeyEvent` — when a direction key finds no focusable
neighbour, the container **searches along that axis** for the next focusable
thing and scrolls to it. This is what lets Google TV's Home be a vertical list
of horizontal rows and still navigate cleanly.

### The idle timer (`idle_timer_view.xml`)

A 50dp `ProgressBar` styled as a ring, `max=30`, `progress=30`,
`scaleX="-1"` so the ring **unwinds**; the countdown number sits inside it;
then a title, a description and a 200×40dp **Keep watching** button. A
`accessibilityLiveRegion="polite"` hidden TextView announces the countdown.
Google TV pauses playback when you stop touching the remote.

### Install state on a tile (`install_state_overlay.xml`)

A greyed-out banner plus an indeterminate `ProgressBar` pinned to the bottom,
with the strings `downloading`, `installing`, `install_pending`,
`update_pending`, `restore_pending`.

### The card itself (`view_app_card.xml`, `view_app_banner_apps_tab.xml`)

- The title `TextView` is `visibility="invisible"` — it **appears only when the
  card has focus**.
- `stateListAnimator="@animator/app_animator"`; the Apps-tab banner uses
  `app:focused_scale` with `app:scale_anim_duration_ms="175"`.
- `edit_focused_frame` overlays an `edit_mode_frame` drawable in arrange mode.
- The banner image is `fitCenter` on a `banner_container` of fixed height.

### Other details

- `notification_tray_item.xml` — title and body use `ellipsize="marquee"` with
  `marqueeRepeatLimit="marquee_forever"`, plus per-item **See more** and
  **Dismiss** buttons, and a `NowPlayingIndicatorView` (a hand-drawn animated
  bar equaliser with its own `ValueAnimator`).
- `OverlayTutorialArrow` — draws a real arrow (`Path` + `Paint`) pointing at the
  control it wants you to press.
- `MemoryConservedImageView` — pauses image loading in `onDetachedFromWindow`
  and resumes in `onAttachedToWindow`.
- `chip_view` subtext is 11sp against a 12sp label: the count is a whisper
  under the name.
- `entity_details_truncated_description` — long text ends with a **"More"**
  link in the same weight as the body, not an ellipsis.

### Error handling, and what it teaches

| String | Text |
|---|---|
| `failed_launch` | "Can't open this right now" |
| `app_unavailable` | "Application unavailable" |
| `network_error_title_text` | "Internet connection lost" |
| `network_error_fragment_primary_action_text` | "Network settings" |
| `network_error_fragment_secondary_action_text` | "Go back" |
| `content_failed_to_load_storage_full_title` | "Can't load content because device storage is full" |
| `content_failed_to_load_clear_data_suggestion_description` | "…or Clear storage in Settings. Go to Settings > Apps > Look for TV Home > Clear data." |
| `empty_channel_message` | "Channel is empty" |
| `channel_temporarily_hidden_message` | "This channel is empty and will be temporarily hidden until more programs are available." |
| `no_apps_available_title` | "All available apps are already in favorites" |
| `channel_flipper_nudge_text` | "Press UP or DOWN on your remote to quickly flip channels" |

Two patterns: an error screen has a **primary action that fixes it** and a
secondary that backs out; and a **dead row hides itself** instead of showing an
empty box.

### Activity flags

`stateNotNeeded="true"` and `resumeWhilePausing="true"` on `MainActivity`;
`noHistory="true"` on all seven side panels; `taskAffinity=".TvLauncher"`.

### Privacy warts, for the record

- `WatsonKatnissConfigContentProvider` is **exported with no permission** — any
  app on the TV can read the launcher's configuration.
- `AdsPersonalizationAwarenessNotificationReceiver` pushes a notification about
  ad personalisation.
- `BootHelperEmptyActivity` runs at boot.
- `MarketUpdateReceiver` + `StopOutOfBoxOrderingJobService` reorder the Play
  Store's install queue in the background.
- `SilentFeedbackReceiver` reports crashes from a separate `:crash_report`
  process.
- `AppDoctorReceiver` accepts self-repair actions from Play services.

---

## 9. The voice assistant and the keyboard

The search entry point is the one part of the launcher that is *not* in the
launcher. This is the whole mechanism, traced end to end.

### The launcher has no voice code and no search key

`KEYCODE_SEARCH` appears **nowhere** in the 13,269-file decode, and the
manifest has no `SEARCH`, `ASSIST` or `VOICE` intent filter. The launcher
never handles the remote's mic button. That is the system's job: the system
fires `ACTION_ASSIST` at whatever assist handler is installed.

What the launcher *does* own is the **search widget in the system bar**, and
that widget is **two orbs in one slot**:

| | id | title | icon |
|---|---|---|---|
| voice | `mic_orb` | "Click to speak" | `ic_mic_grey` |
| keyboard | `keyboard_orb` | "Click to type" | `ic_keyboard_black` / `ic_keyboard_grey` |

`keyboard_orb` is `visibility="invisible"` in the layout. The widget
**morphs** between the two: `jib.a(0|1)` slides the outgoing orb out with
`setTranslationX` and scales the incoming one in with `setScaleX/Y`, and
`jhz.onAnimationEnd` then **restores the saved visibilities** — the mic
title goes, the keyboard title comes, the "Search" label shifts. The saved
state lives in `jia`, which also captures the scale, the translation and the
background colour of all four views, so the morph can be undone exactly.

### Both orbs fire the same intent

`hkg.q(int)` builds it and `hkg.r(context, intent)` launches it:

```
action  android.intent.action.ASSIST
flags   FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
extra   "search_type" : int   ->  1 = voice, 0 = keyboard
```

`search_type` is the entire difference between speaking and typing. It
appears **exactly once** in the whole APK — the launcher sending it. Nothing
in this APK reads it; the receiver is the Google app. It is a one-way,
undocumented contract between two apps that ship together.

If no assist handler resolves, `hkg.r` catches `ActivityNotFoundException`
and toasts **`app_unavailable`** — "Application unavailable". That message
is wrong about the cause: it is not the app that is missing, it is the
assistant. Google TV never learned to fall back to the keyboard.

### Google TV's launcher has no keyboard

There is no `EditText`, no `InputMethodManager` and no IME handling anywhere
in the launcher for search. The keyboard comes from the assist handler, so
the launcher never has to worry about one.

The only keyboard subtlety in the entire APK is in the Leanback library:
`androidx.leanback.widget.SearchEditText.onKeyPreIme` intercepts
`KEYCODE_BACK` **before** the IME sees it and turns it into "dismiss the
search bar". The lesson is the hook, not the behaviour: `onKeyPreIme` fires
*before* the IME, `setOnEditorActionListener` fires *after* it.

### What this means for Sonora

Sonora took the opposite route: its own `EditText` and
`InputMethodManager.showSoftInput(field, SHOW_IMPLICIT)`. That is the right
call for a local, offline search, but it moves three responsibilities onto
the launcher that Google TV never had to carry:

1. **An IME might not be enabled.** If no IME is enabled or none is the
   default, the field still takes focus and the user can type nothing — a
   silent dead end. Check `getEnabledInputMethodList()` first.
2. **The TV IME can take the D-pad.** A TV keyboard navigated with the
   remote wants the arrow keys. If it keeps them, the user is trapped in the
   text field and cannot reach the results. `onKeyPreIme` is the hook that
   takes them back.
3. **Back with the keyboard open** must close the keyboard, not the
   launcher.

And the voice option itself is not a feature Sonora can build: a local voice
search needs a speech recogniser, which means a new dependency and a
network round trip — both forbidden. The only honest voice option is the one
Google uses: fire `ACTION_ASSIST` and let the TV's own assistant take it,
falling back to the keyboard when there is no assistant.

So: **one search pill, keyboard first, voice as the extra that degrades to
the keyboard** — not two entry points and not a toast that says "Application
unavailable".

---

## 10. The rest of the subtle half

Six more, found in this pass, on top of §8.

### The top row is circles, not pills

`HomeTopRowButton` gives its background an explicit `ViewOutlineProvider`
whose `getOutline` calls **`Outline.setOval`** — the settings, inputs,
profile and notification buttons are **round glass discs**, clipped with
`setClipToOutline(true)` so the shadow follows the circle. The search orb
does the same with the default outline provider (`jib.h`).

tvOS 26 uses circular glass buttons in the top right for exactly this. Sonora
uses pills. The circle is the correct shape.

### Focus is four things at once, not one

`HomeTopRowButton.onFinishInflate` builds the focus animation from four
parts:

- `expand_fade_in` — `alpha 0→1`, `scaleX/Y 0.8→1`
- `shrink_fade_out` — `alpha 1→0`, `scaleX/Y 1→0.8`
- an `ArgbEvaluator` tween between two background colours
- the title's `alpha 0→1`

All at `top_row_button_animation_duration_ms` = **250 ms**. The icon tint
also flips on focus (`hasFocus() ? focused : unfocused`).

Sonora's glass pill springs to 1.055 and raises its elevation, but the glass
never *changes colour*. A tween of the glass tint is the missing half.

### The system bar does not trap the focus

`SystemBarView.addFocusables` routes the focus search into the search widget
when the bar itself holds focus, and throws
`IllegalStateException("Widget view must be added to System Bar")` if the
widget was never added. The bar is a place you pass through, not a place
you get stuck.

### A disabled menu item is a feature

`context_menu_can_not_move_text` = "Can't move" and
`context_menu_can_not_remove_text` = "Can't remove from favorites". Google TV
keeps the row **visible but disabled**, with a reason. A missing row teaches
nothing; a greyed row with a reason teaches the rule.

### Edit mode has a move menu, with accessibility labels

`EditModeGridView` builds four menu items whose titles are the
accessibility strings `accessibility_menu_item_move_up` / `_down` / `_left` /
`_right`, and each one is enabled or disabled by a legality check
(`aW(0x82)`, `aW(0x21)`). So arrange mode is not "drag the tile": it is a
menu of four directional moves, each of which knows whether it is allowed.

Sonora's arrange mode has no move menu at all — only the D-pad.

### First-run guidance is a drawn arrow

`OverlayTutorialArrow` draws a real arrow (`Path` + `Paint`) pointing at the
control the user should press, and `TutorialActivity` + a tray notification
("Awareness tour home screen notification") deliver it once. Sonora's
`SetupGuide` is a text list of adb commands, and "Edit Home Screen" has never
been confirmed to work on the device — an arrow pointing at the tile the
first time someone long-presses would answer both.

### A confirmation toast after every change

`add_to_favorite_apps_toast_message` = "Added to favorites". Sonora already
does this ("X pinned to the top"). Keep it.

