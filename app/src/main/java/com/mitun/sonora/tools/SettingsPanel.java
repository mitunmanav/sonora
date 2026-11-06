package com.mitun.sonora.tools;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mitun.sonora.AppEntry;
import com.mitun.sonora.AppRepository;
import com.mitun.sonora.DeviceTier;
import com.mitun.sonora.PackageActions;
import com.mitun.sonora.Prefs;
import com.mitun.sonora.PhotoLoader;
import com.mitun.sonora.RecentApps;
import com.mitun.sonora.R;
import com.mitun.sonora.ui.Motion;
import com.mitun.sonora.ui.SettingRow;
import com.mitun.sonora.ui.Theme;
import com.mitun.sonora.ui.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The settings.
 *
 * One screen of everything was too long to read, so the settings are an index
 * of short screens: the home screen, the clock, the screen saver, the apps,
 * the tools and the system. Every screen holds a handful of rows.
 *
 * There are two modes. Default shows the settings anybody needs, in plain
 * words. Technical adds the rest: columns, seconds, reduce motion, the order
 * of the apps. The mode is picked in the setup and can be changed here.
 */
public final class SettingsPanel {
    private static final String TITLE = "Settings";

    /** One screen of the settings. */
    private static final class Page {
        final String title;

        Page(String title) {
            this.title = title;
        }
    }

    private static final Page QUICK = new Page("Quick settings");
    private static final Page HOME = new Page("Home Screen");
    private static final Page CLOCK = new Page("Clock");
    private static final Page SAVER = new Page("Screen Saver");
    private static final Page APPS = new Page("Apps");
    private static final Page TOOLS = new Page("Tools");
    private static final Page SYSTEM = new Page("System");

    private SettingsPanel() {
    }

    public static void show(ToolActivity host) {
        host.stack().showContent(TITLE, null, homeView(host, 0), true);
    }

    /** Rebuilds the screen the user is on, with the new values. */
    public static void refresh(ToolActivity host) {
        Page page = topPage(host);
        if (page == null) {
            // The index is rebuilt in place when the mode changes, so it has
            // to stay on the category the user was on. A screen that is not
            // the index keeps what it has: it is not the settings.
            View top = host.stack().topContent();
            if (top instanceof SettingsHomeView) {
                int selected = ((SettingsHomeView) top).selectedCategory();
                host.stack().refreshContent(TITLE, null, homeView(host, selected), true);
            }
            return;
        }
        host.stack().refreshSettings(page.title, rowsFor(host, page));
        tagTop(host, page);
        // The index behind the page shows the values this page edits, so it
        // is rebuilt too; otherwise BACK reveals the values from before.
        View below = host.stack().topContentBelow();
        if (below instanceof SettingsHomeView) {
            int selected = ((SettingsHomeView) below).selectedCategory();
            host.stack().refreshContentBelow(TITLE, null, homeView(host, selected), true);
        }
    }

    /** True when the screen on top is one of the settings pages. */
    static boolean isPage(ToolActivity host) {
        return topPage(host) != null;
    }

    /** True when the screen on top is the settings page with this title. */
    static boolean topIsPage(ToolActivity host, String title) {
        Page page = topPage(host);
        return page != null && page.title.equals(title);
    }

    /**
     * The page the screen on top is, or null when it is something else.
     *
     * Pages are matched by the tag their view was pushed with, not by the
     * title: a screen of its own can share a page's name, and it must not
     * be rebuilt into a settings page on resume.
     */
    private static Page topPage(ToolActivity host) {
        View top = ToolActivity.stackTopView(host.stack());
        Object tag = top == null ? null : top.getTag();
        return tag instanceof Page ? (Page) tag : null;
    }

    /** Marks the screen just pushed as a settings page. */
    private static void tagTop(ToolActivity host, Page page) {
        int count = host.stack().getChildCount();
        if (count > 0) {
            host.stack().getChildAt(count - 1).setTag(page);
        }
    }

    private static Page pageFor(String title) {
        if (QUICK.title.equals(title)) {
            return QUICK;
        }
        if (HOME.title.equals(title)) {
            return HOME;
        }
        if (CLOCK.title.equals(title)) {
            return CLOCK;
        }
        if (SAVER.title.equals(title)) {
            return SAVER;
        }
        if (APPS.title.equals(title)) {
            return APPS;
        }
        if (TOOLS.title.equals(title)) {
            return TOOLS;
        }
        if (SYSTEM.title.equals(title)) {
            return SYSTEM;
        }
        return null;
    }

    private static List<SettingRow> rowsFor(ToolActivity host, Page page) {
        if (QUICK == page) {
            return QuickPanel.rows(host);
        }
        if (HOME == page) {
            return homeRows(host);
        }
        if (CLOCK == page) {
            return clockRows(host);
        }
        if (SAVER == page) {
            return saverRows(host);
        }
        if (APPS == page) {
            return appsRows(host);
        }
        if (TOOLS == page) {
            return toolsRows(host);
        }
        if (SYSTEM == page) {
            return systemRows(host);
        }
        return new ArrayList<>();
    }

    private static View homeView(ToolActivity host, int initialSelection) {
        Prefs prefs = host.prefs();
        List<SettingsHomeView.Category> categories = new ArrayList<>();
        categories.add(new SettingsHomeView.Category(QUICK.title,
                "Open the system settings available on this TV.",
                detail("System controls", "Provided by your TV"),
                () -> open(host, QUICK)));
        categories.add(new SettingsHomeView.Category(HOME.title,
                "Change how Sonora looks and works.",
                new SettingsHomeView.Detail[]{
                    detail("Appearance", prefs.lightAppearance() ? "Light" : "Dark"),
                    detail("Wallpaper", Pickers.wallpaperName(prefs)),
                    detail("App names", Pickers.labelName(prefs)),
                    detail("Reduce motion", onOff(prefs.reduceMotion())),
                    detail("Search key", onOff(prefs.searchKey()))},
                () -> open(host, HOME)));

        SettingsHomeView.Detail[] clockDetails = prefs.technical()
                ? new SettingsHomeView.Detail[]{
                    detail("24-hour clock", onOff(prefs.clock24())),
                    detail("Show the date", onOff(prefs.showDate())),
                    detail("Show seconds", onOff(prefs.showSeconds()))}
                : new SettingsHomeView.Detail[]{
                    detail("Clock", Pickers.clockName(prefs)),
                    detail("Show the date", onOff(prefs.showDate()))};
        categories.add(new SettingsHomeView.Category(CLOCK.title,
                "Set the time display shown over your wallpaper.", clockDetails,
                () -> open(host, CLOCK)));

        List<SettingsHomeView.Detail> saverDetails = new ArrayList<>();
        saverDetails.add(detail("Start after", Pickers.delayName(prefs)));
        if (prefs.technical()) {
            saverDetails.add(detail("Each scene", Pickers.sceneName(prefs)));
            saverDetails.add(detail("Screen saver clock", Pickers.screensaverClockName(prefs)));
        }
        categories.add(new SettingsHomeView.Category(SAVER.title,
                "Pick when the scenes start. The TV's own screen saver settings are here too.",
                saverDetails.toArray(new SettingsHomeView.Detail[0]),
                () -> open(host, SAVER)));
        categories.add(new SettingsHomeView.Category(APPS.title,
                "Pick favourites, hide apps and manage what is installed.",
                detail("Favourites", Pickers.favouritesName(host)),
                detail("Hidden apps", Pickers.hiddenName(host)),
                () -> open(host, APPS)));
        categories.add(new SettingsHomeView.Category(TOOLS.title,
                "Files, a browser and info about this TV.",
                detail("Device", "Info and checks"),
                detail("Files", "Browse and share"),
                detail("Browser", "Optional; stays off until opened"),
                () -> open(host, TOOLS)));
        categories.add(new SettingsHomeView.Category(SYSTEM.title,
                "Set Sonora as home, open TV updates or reset its settings.",
                detail("Home screen", Ui.isDefaultLauncher(host) ? "Sonora" : "Not set"),
                detail("System update", "Open the TV’s settings"),
                () -> open(host, SYSTEM)));
        categories.add(new SettingsHomeView.Category("Mode",
                "Default keeps the list focused. Technical adds more controls.",
                detail("Current mode", Pickers.modeName(prefs)),
                () -> Pickers.mode(host, () -> refresh(host))));
        return new SettingsHomeView(host, categories, initialSelection);
    }

    private static SettingsHomeView.Detail detail(String label, String value) {
        return new SettingsHomeView.Detail(label, value);
    }

    private static String onOff(boolean enabled) {
        return enabled ? "On" : "Off";
    }

    private static List<SettingRow> homeRows(ToolActivity host) {
        Prefs prefs = host.prefs();
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.choice("Appearance", prefs.lightAppearance() ? "Light" : "Dark",
                () -> chooseAppearance(host)));
        rows.add(SettingRow.choice("Wallpaper", Pickers.wallpaperName(prefs),
                () -> Pickers.wallpaper(host, () -> refresh(host))));
        rows.add(SettingRow.choice("App names", Pickers.labelName(prefs),
                () -> Pickers.labels(host, () -> refresh(host))));
        rows.add(SettingRow.toggle("Reduce motion", prefs.reduceMotion(), () -> {
            prefs.setReduceMotion(!prefs.reduceMotion());
            // The launcher caches the setting, so it has to be told that it
            // changed or the next motion keeps animating.
            Motion.refresh(host);
            refresh(host);
        }));
        rows.add(SettingRow.toggle("Search key", prefs.searchKey(), () -> {
            prefs.setSearchKey(!prefs.searchKey());
            refresh(host);
        }));
        rows.add(SettingRow.note("The search key on your remote opens Sonora's own search. "
                + "It looks at the apps on this TV and sends nothing anywhere."));
        if (RecentApps.usageGranted(host)) {
            rows.add(SettingRow.value("App switcher", "Double-press Home"));
        } else {
            rows.add(SettingRow.note("Double-press Home lists recent apps. Allow usage "
                    + "access so Sonora can see them."));
            rows.add(SettingRow.action("Allow usage access",
                    () -> Ui.openUsageAccessSettings(host)));
        }
        if (prefs.technical()) {
            rows.add(SettingRow.choice("Apps per row", Pickers.columnsName(host),
                    () -> Pickers.columns(host, () -> refresh(host))));
            rows.add(SettingRow.choice("Performance", performanceName(host),
                    () -> choosePerformance(host)));
        }
        return rows;
    }

    /** Settings, Search and Control Center follow this. The home screen stays itself. */
    private static void chooseAppearance(ToolActivity host) {
        Prefs prefs = host.prefs();
        host.choose("Appearance", new String[]{"Dark", "Light"},
                prefs.lightAppearance() ? 1 : 0, index -> {
                    boolean light = index == 1;
                    if (light != prefs.lightAppearance()) {
                        prefs.setLightAppearance(light);
                        host.reopenAt(HOME.title);
                    }
                });
    }

    private static String performanceName(ToolActivity host) {
        switch (host.prefs().performance()) {
            case DeviceTier.LOW:
                return "Lightest";
            case DeviceTier.MID:
                return "Balanced";
            case DeviceTier.HIGH:
                return "Full";
            default:
                return "Automatic";
        }
    }

    private static void choosePerformance(ToolActivity host) {
        Prefs prefs = host.prefs();
        host.choose("Performance", new String[]{"Automatic", "Lightest, for 1 GB of memory",
                "Balanced, for 2 GB of memory", "Full, for 3 GB or more"},
                prefs.performance() + 1, index -> {
                    prefs.setPerformance(index - 1);
                    DeviceTier.refresh();
                    AppRepository.invalidate();
                    host.reopenAt(HOME.title);
                });
    }

    /** Opens one page over the index, for a screen that has just been rebuilt. */
    static void openPage(ToolActivity host, String title) {
        Page page = pageFor(title);
        if (page != null) {
            open(host, page);
        }
    }

    private static List<SettingRow> clockRows(ToolActivity host) {
        Prefs prefs = host.prefs();
        List<SettingRow> rows = new ArrayList<>();
        if (prefs.technical()) {
            rows.add(SettingRow.toggle("24-hour clock", prefs.clock24(), () -> {
                prefs.setClock24(!prefs.clock24());
                refresh(host);
            }));
            rows.add(SettingRow.toggle("Show the date", prefs.showDate(), () -> {
                prefs.setShowDate(!prefs.showDate());
                refresh(host);
            }));
            rows.add(SettingRow.toggle("Show seconds", prefs.showSeconds(), () -> {
                prefs.setShowSeconds(!prefs.showSeconds());
                refresh(host);
            }));
        } else {
            rows.add(SettingRow.choice("Clock", Pickers.clockName(prefs),
                    () -> Pickers.clock(host, () -> refresh(host))));
            rows.add(SettingRow.toggle("Show the date", prefs.showDate(), () -> {
                prefs.setShowDate(!prefs.showDate());
                refresh(host);
            }));
        }
        return rows;
    }

    private static List<SettingRow> saverRows(ToolActivity host) {
        Prefs prefs = host.prefs();
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.choice("Photo source", photoSourceName(prefs),
                () -> choosePhotoSource(host)));
        rows.add(SettingRow.choice("Start after", Pickers.delayName(prefs),
                () -> Pickers.screensaverDelay(host, () -> refresh(host))));
        if (prefs.technical()) {
            rows.add(SettingRow.choice("Each scene", Pickers.sceneName(prefs),
                    () -> Pickers.sceneDuration(host, () -> refresh(host))));
            rows.add(SettingRow.choice("Clock", Pickers.screensaverClockName(prefs),
                    () -> Pickers.screensaverClock(host, () -> refresh(host))));
        }
        Ui.DreamState dream = Ui.dreamState(host);
        if (dream == Ui.DreamState.STALE) {
            rows.add(SettingRow.note("The TV's screen saver is set to an app that is not "
                    + "installed, so it shows nothing. Pick Sonora in the TV's settings."));
        } else if (dream != Ui.DreamState.SELECTED) {
            rows.add(SettingRow.note("When the TV dozes it can show Sonora's scenes too. "
                    + "Pick Sonora in the TV's screen saver settings."));
        }
        rows.add(SettingRow.action("Screen saver settings", () -> Ui.openDreamSettings(host)));
        rows.add(SettingRow.header("Screen saver preferences"));
        rows.add(SettingRow.push("Aerials", "", () -> host.stack().pushFullBleed(
                "Choose Aerials", new AerialsView(host, prefs))));
        rows.add(SettingRow.note("Choose which pictures the screen saver shows. Left and right "
                + "on the remote change the picture while it is running."));
        return rows;
    }

    private static String photoSourceName(Prefs prefs) {
        switch (prefs.screensaverPhotoSource()) {
            case 0:
                return "Bundled only";
            case 1:
                return "Device photos";
            case 2:
                return "Both";
            default:
                return "Bundled only";
        }
    }

    private static void choosePhotoSource(ToolActivity host) {
        Prefs prefs = host.prefs();
        host.choose("Photo source", new String[]{"Bundled only", "Device photos", "Both"},
                prefs.screensaverPhotoSource(), index -> {
                    if (index == prefs.screensaverPhotoSource()) {
                        return;
                    }
                    if (index != 0 && !PhotoLoader.hasStorageAccess(host)) {
                        // Without access the setting would claim photos it
                        // cannot read, while the saver quietly plays the
                        // bundled set: say so and land the user where access
                        // can actually be granted.
                        Ui.toast(host, "Allow photo access for Sonora here, then try again");
                        PackageActions.openAppInfo(host, host.getPackageName());
                        return;
                    }
                    prefs.setScreensaverPhotoSource(index);
                    PhotoLoader.invalidateCache();
                    refresh(host);
                });
    }

    private static List<SettingRow> appsRows(ToolActivity host) {
        Prefs prefs = host.prefs();
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.push("Favourites", Pickers.favouritesName(host),
                () -> openFavourites(host)));
        rows.add(SettingRow.push("Manage apps", "", () -> AppPanel.show(host)));
        rows.add(SettingRow.push("Debloat", "Turn off apps you do not use",
                () -> DebloatPanel.show(host)));
        rows.add(SettingRow.choice("Hidden apps", Pickers.hiddenName(host),
                () -> Pickers.hiddenApps(host, () -> refresh(host))));
        if (prefs.technical()) {
            rows.add(SettingRow.action("Reset app order", () -> {
                prefs.setOrder("");
                Ui.toast(host, "App order reset");
            }));
        }
        rows.add(SettingRow.action("Reload apps", () -> {
            AppRepository.invalidate();
            // The launcher compares this key on resume, so this is how it
            // learns that the app list has to be read again.
            prefs.bumpAppsEpoch();
            refresh(host);
            Ui.toast(host, "Apps will reload when you go back");
        }));
        return rows;
    }

    /**
     * The apps the user can pin to the top of the home screen.
     *
     * A ticked app sits at the top of the home screen, in the order it was
     * ticked. Everything else follows.
     */
    private static void openFavourites(ToolActivity host) {
        // The app list can be cold, and reading it means asking the system
        // for every installed app. That is not work for the main thread.
        host.stack().pushSettings("Favourites",
                Collections.singletonList(SettingRow.value("Reading", "…")));
        reloadFavourites(host);
    }

    /** Rebuilds the check rows the way the screen first loads: off the main thread. */
    private static void reloadFavourites(ToolActivity host) {
        new Thread(() -> {
            List<SettingRow> built;
            try {
                built = favouriteRows(host);
            } catch (RuntimeException e) {
                built = new ArrayList<>();
                built.add(SettingRow.note("The list of apps could not be read."));
            }
            final List<SettingRow> rows = built;
            host.runOnUiThread(() -> {
                if (host.isFinishing() || host.isDestroyed()) {
                    return;
                }
                if (!"Favourites".equals(host.stack().topTitle())) {
                    // The user left the screen while the list was being read.
                    return;
                }
                host.stack().refreshSettings("Favourites", rows);
            });
        }, "sonora-favourites").start();
    }

    /** The screen is not a settings page, so it asks for its own rebuild on resume. */
    static void resumedFavourites(ToolActivity host) {
        if ("Favourites".equals(host.stack().topTitle())) {
            reloadFavourites(host);
        }
    }

    private static List<SettingRow> favouriteRows(ToolActivity host) {
        List<SettingRow> rows = new ArrayList<>();
        for (AppEntry entry : AppRepository.get(host, 0)) {
            final String pkg = entry.packageName;
            if (pkg == null) {
                continue;
            }
            boolean pinned = host.prefs().isFavourite(pkg);
            rows.add(SettingRow.check(entry.label, pinned, () -> {
                List<String> pinned2 = new ArrayList<>(host.prefs().favourites());
                if (pinned2.contains(pkg)) {
                    pinned2.remove(pkg);
                } else {
                    pinned2.add(0, pkg);
                }
                host.prefs().setFavourites(pinned2);
                // A cold app list would put the package scan on the key
                // handler's thread, which is the main one.
                reloadFavourites(host);
            }));
        }
        if (rows.isEmpty()) {
            rows.add(SettingRow.note("No apps found on this device."));
        }
        return rows;
    }

    private static List<SettingRow> toolsRows(ToolActivity host) {
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.push("Device", "", () -> DebugPanel.show(host)));
        rows.add(SettingRow.push("Setup guide", "", () -> SetupGuide.show(host)));
        rows.add(SettingRow.push("Files", "", () -> ToolsPanel.openFiles(host)));
        rows.add(SettingRow.push("Browser", host.prefs().browserEnabled() ? "On" : "Off",
                () -> ToolsPanel.openBrowser(host)));
        return rows;
    }

    private static List<SettingRow> systemRows(ToolActivity host) {
        Prefs prefs = host.prefs();
        List<SettingRow> rows = new ArrayList<>();
        boolean home = Ui.isDefaultLauncher(host);
        rows.add(SettingRow.value("Home screen", home ? "Sonora" : "Not yet"));
        if (!home) {
            rows.add(SettingRow.action("Set Sonora as the home screen",
                    () -> Ui.openDefaultLauncherSettings(host)));
        }
        rows.add(SettingRow.push("System update", "", () -> QuickPanel.openUpdate(host)));
        rows.add(SettingRow.action("Tidy up again", () -> QuickPanel.tidyUpNow(host)));
        rows.add(SettingRow.action("About Sonora", () -> showAbout(host)));
        if (prefs.technical()) {
            rows.add(SettingRow.action("Run setup again", () -> ToolActivity.open(host, "wizard")));
        }
        rows.add(SettingRow.danger("Reset all settings", () -> confirmReset(host)));
        return rows;
    }

    /** Resetting cannot be undone, so it asks first, with Cancel focused. */
    private static void confirmReset(ToolActivity host) {
        host.choose("Reset all settings?",
                new String[]{"Reset everything", "Cancel"}, 1, index -> {
                    if (index != 0) {
                        return;
                    }
                    host.prefs().reset();
                    Motion.refresh(host);
                    DeviceTier.refresh();
                    Theme.load(host);
                    Ui.toast(host, "Settings reset");
                    // The reset can change the theme and the motion, which a
                    // rebuilt row cannot repaint - the screens are remade.
                    host.reopenAt(SYSTEM.title);
                });
    }

    private static void open(ToolActivity host, Page page) {
        host.stack().pushSettings(page.title, rowsFor(host, page));
        tagTop(host, page);
    }

    /**
     * Opens the screen saver settings.
     *
     * The quick settings point here too: Sonora has its own screen saver, so
     * that is the one worth changing.
     */
    public static void openScreenSaver(ToolActivity host) {
        open(host, SAVER);
    }

    private static void showAbout(ToolActivity host) {
        TextView text = new TextView(host);
        text.setText(host.getString(R.string.about_text));
        text.setTextColor(Theme.ink());
        text.setTextSize(16);
        text.setLineSpacing(Ui.dpf(host, 6), 1f);
        text.setPadding(Ui.dp(host, 10), Ui.dp(host, 10), Ui.dp(host, 10), Ui.dp(host, 10));
        ScrollView scroller = new ScrollView(host);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.addView(text, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.stack().pushContent("About Sonora", "Imagery and credits", scroller);
    }
}
