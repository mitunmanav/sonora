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
        host.stack().show(TITLE, subtitle(host), rootRows(host));
    }

    /** Rebuilds the screen the user is on, with the new values. */
    public static void refresh(ToolActivity host) {
        Page page = pageFor(host.stack().topTitle());
        if (page == null) {
            host.stack().refresh(TITLE, subtitle(host), rootRows(host));
            return;
        }
        host.stack().refresh(page.title, page.subtitle, rowsFor(host, page));
    }

    private static String subtitle(ToolActivity host) {
        return host.prefs().technical()
                ? "Every setting, including the ones you will never touch"
                : "The settings worth changing";
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
        return rootRows(host);
    }

    private static List<SettingRow> rootRows(ToolActivity host) {
        Prefs prefs = host.prefs();
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.push("Quick settings", "Wi-Fi, sound, display, updates",
                () -> open(host, QUICK)));
        rows.add(SettingRow.choice("Mode", Pickers.modeName(prefs),
                () -> Pickers.mode(host, () -> refresh(host))));
        rows.add(SettingRow.note("Default keeps the settings short. Technical shows every "
                + "setting, including the ones you will never touch."));
        rows.add(SettingRow.push(HOME.title, "picture and apps", () -> open(host, HOME)));
        rows.add(SettingRow.push(CLOCK.title, "time and date", () -> open(host, CLOCK)));
        rows.add(SettingRow.push(SAVER.title, "when the TV rests", () -> open(host, SAVER)));
        rows.add(SettingRow.push(APPS.title, "favourites, hidden apps and order",
                () -> open(host, APPS)));
        rows.add(SettingRow.push(TOOLS.title, "app manager, files, browser", () -> open(host, TOOLS)));
        rows.add(SettingRow.push(SYSTEM.title, "home screen and reset", () -> open(host, SYSTEM)));
        return rows;
    }

    private static List<SettingRow> homeRows(ToolActivity host) {
        Prefs prefs = host.prefs();
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.choice("Wallpaper", Pickers.wallpaperName(prefs),
                () -> Pickers.wallpaper(host, () -> refresh(host))));
        rows.add(SettingRow.choice("App names", Pickers.labelName(prefs),
                () -> Pickers.labels(host, () -> refresh(host))));
        rows.add(SettingRow.toggle("Search key", prefs.searchKey(), () -> {
            prefs.setSearchKey(!prefs.searchKey());
            refresh(host);
        }));
        rows.add(SettingRow.note("The search key on your remote opens Sonora's own search. "
                + "It looks at the apps on this TV and sends nothing anywhere."));
        if (prefs.technical()) {
            rows.add(SettingRow.choice("Apps per row", Pickers.columnsName(host),
                    () -> Pickers.columns(host, () -> refresh(host))));
            rows.add(SettingRow.toggle("Reduce motion", prefs.reduceMotion(), () -> {
                prefs.setReduceMotion(!prefs.reduceMotion());
                // The launcher caches the setting, so it has to be told that it
                // changed or the next motion keeps animating.
                Motion.refresh(host);
                refresh(host);
            }));
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
        host.stack().push("Favourites", "Ticked apps sit at the top of the home screen",
                favouriteRows(host));
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
        rows.add(SettingRow.danger("Reset all settings", () -> {
            prefs.reset();
            Ui.toast(host, "Settings reset");
            refresh(host);
        }));
        return rows;
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
