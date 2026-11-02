package com.mitun.sonora.tools;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mitun.sonora.AppEntry;
import com.mitun.sonora.AppRepository;
import com.mitun.sonora.Prefs;
import com.mitun.sonora.R;
import com.mitun.sonora.ui.Motion;
import com.mitun.sonora.ui.SettingRow;
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

    /** One screen of the settings: its title, its subtitle and its rows. */
    private static final class Page {
        final String title;
        final String subtitle;

        Page(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private static final Page QUICK = new Page("Quick settings", "The TV's own screens, one tap away");
    private static final Page HOME = new Page("Home Screen", "The picture and the apps");
    private static final Page CLOCK = new Page("Clock", "The time in the corner");
    private static final Page SAVER = new Page("Screen Saver", "What the TV does when you rest");
    private static final Page APPS = new Page("Apps", "What the home screen shows");
    private static final Page TOOLS = new Page("Tools", "Everything runs on this TV");
    private static final Page SYSTEM = new Page("System", "Sonora and this TV");

    private SettingsPanel() {
    }

    public static void show(ToolActivity host) {
        host.stack().showContent(TITLE, subtitle(host), homeView(host, 0));
    }

    /** Rebuilds the screen the user is on, with the new values. */
    public static void refresh(ToolActivity host) {
        Page page = pageFor(host.stack().topTitle());
        if (page == null) {
            // The index is rebuilt in place when the mode changes, so it has
            // to stay on the category the user was on.
            View top = host.stack().topContent();
            int selected = top instanceof SettingsHomeView
                    ? ((SettingsHomeView) top).selectedCategory() : 0;
            host.stack().refreshContent(TITLE, subtitle(host), homeView(host, selected));
            return;
        }
        host.stack().refresh(page.title, page.subtitle, rowsFor(host, page));
        // The index behind the page shows the values this page edits, so it
        // is rebuilt too; otherwise BACK reveals the values from before.
        View below = host.stack().topContentBelow();
        if (below instanceof SettingsHomeView) {
            int selected = ((SettingsHomeView) below).selectedCategory();
            host.stack().refreshContentBelow(TITLE, subtitle(host), homeView(host, selected));
        }
    }

    private static String subtitle(ToolActivity host) {
        return host.prefs().technical()
                ? "All controls for Sonora and your TV"
                : "Personalize Sonora and your TV";
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
                "Choose Sonora’s wallpaper, app labels, focus effects, and remote search.",
                new SettingsHomeView.Detail[]{
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
                "Choose when Sonora’s scenes begin and open the TV’s screen saver settings.",
                saverDetails.toArray(new SettingsHomeView.Detail[0]),
                () -> open(host, SAVER)));
        categories.add(new SettingsHomeView.Category(APPS.title,
                "Choose favourites, hide apps, and manage what is installed.",
                detail("Favourites", Pickers.favouritesName(prefs)),
                detail("Hidden apps", Pickers.hiddenName(prefs)),
                () -> open(host, APPS)));
        categories.add(new SettingsHomeView.Category(TOOLS.title,
                "On-device tools for diagnostics, files, and browsing.",
                detail("Device", "Information and diagnostics"),
                detail("Files", "Browse and share"),
                detail("Browser", "Optional; stays off until opened"),
                () -> open(host, TOOLS)));
        categories.add(new SettingsHomeView.Category(SYSTEM.title,
                "Set Sonora as home, open TV updates, or reset its settings.",
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
        if (prefs.technical()) {
            rows.add(SettingRow.choice("Apps per row", Pickers.columnsName(host),
                    () -> Pickers.columns(host, () -> refresh(host))));
        }
        return rows;
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
        rows.add(SettingRow.choice("Start after", Pickers.delayName(prefs),
                () -> Pickers.screensaverDelay(host, () -> refresh(host))));
        if (prefs.technical()) {
            rows.add(SettingRow.choice("Each scene", Pickers.sceneName(prefs),
                    () -> Pickers.sceneDuration(host, () -> refresh(host))));
            rows.add(SettingRow.choice("Clock", Pickers.screensaverClockName(prefs),
                    () -> Pickers.screensaverClock(host, () -> refresh(host))));
        }
        rows.add(SettingRow.action("Screen saver settings", () -> Ui.openDreamSettings(host)));
        return rows;
    }

    private static List<SettingRow> appsRows(ToolActivity host) {
        Prefs prefs = host.prefs();
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.push("Favourites", Pickers.favouritesName(prefs),
                () -> openFavourites(host)));
        rows.add(SettingRow.push("Manage apps", "disable and uninstall", () -> AppPanel.show(host)));
        rows.add(SettingRow.choice("Hidden apps", Pickers.hiddenName(prefs),
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
        final String subtitle = "Ticked apps sit at the top of the home screen";
        // The app list can be cold, and reading it means asking the system
        // for every installed app. That is not work for the main thread.
        host.stack().push("Favourites", subtitle,
                Collections.singletonList(SettingRow.value("Reading", "…")));
        new Thread(() -> {
            final List<SettingRow> rows = favouriteRows(host);
            host.runOnUiThread(() -> {
                if (host.isFinishing() || host.isDestroyed()) {
                    return;
                }
                if (!"Favourites".equals(host.stack().topTitle())) {
                    // The user left the screen while the list was being read.
                    return;
                }
                host.stack().refresh("Favourites", subtitle, rows);
            });
        }, "sonora-favourites").start();
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
                host.stack().refresh("Favourites",
                        "Ticked apps sit at the top of the home screen", favouriteRows(host));
            }));
        }
        if (rows.isEmpty()) {
            rows.add(SettingRow.note("No apps found on this device."));
        }
        return rows;
    }

    private static List<SettingRow> toolsRows(ToolActivity host) {
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.push("Device", "memory, storage, battery", () -> DebugPanel.show(host)));
        rows.add(SettingRow.push("Setup guide", "turn on debugging, step by step",
                () -> SetupGuide.show(host)));
        rows.add(SettingRow.push("Files", "browse and share", () -> ToolsPanel.openFiles(host)));
        rows.add(SettingRow.push("Browser", "with an ad blocker", () -> ToolsPanel.openBrowser(host)));
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
        rows.add(SettingRow.push("System update", "the TV's own update",
                () -> QuickPanel.openUpdate(host)));
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
                    Ui.toast(host, "Settings reset");
                    refresh(host);
                });
    }

    private static void open(ToolActivity host, Page page) {
        host.stack().push(page.title, page.subtitle, rowsFor(host, page));
    }

    /**
     * Opens the screen saver settings.
     *
     * The quick settings point here too: Sonora has its own screen saver, so
     * that is the one worth changing.
     */
    public static void openScreenSaver(ToolActivity host) {
        host.stack().push(SAVER.title, SAVER.subtitle, saverRows(host));
    }

    private static void showAbout(ToolActivity host) {
        TextView text = new TextView(host);
        text.setText(host.getString(R.string.about_text));
        text.setTextColor(Color.WHITE);
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
