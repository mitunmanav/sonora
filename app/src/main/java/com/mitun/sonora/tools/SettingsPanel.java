package com.mitun.sonora.tools;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mitun.sonora.AppRepository;
import com.mitun.sonora.Grid;
import com.mitun.sonora.Prefs;
import com.mitun.sonora.R;
import com.mitun.sonora.Scenes;
import com.mitun.sonora.ui.ChoiceOverlay;
import com.mitun.sonora.ui.SettingRow;
import com.mitun.sonora.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Settings screen. Every row is a {@link SettingRow} so the settings, the
 * app manager and the debug panel all look and behave the same.
 */
public final class SettingsPanel {
    private static final String TITLE = "Settings";
    private static final String SUBTITLE = "Sonora · a quiet home for your TV";

    private SettingsPanel() {
    }

    public static void show(ToolActivity host) {
        host.stack().show(TITLE, SUBTITLE, rows(host));
    }

    public static void refresh(ToolActivity host) {
        host.stack().refresh(TITLE, SUBTITLE, rows(host));
    }

    private static List<SettingRow> rows(ToolActivity host) {
        Prefs prefs = host.prefs();
        List<SettingRow> rows = new ArrayList<>();

        rows.add(SettingRow.header("Home Screen"));
        rows.add(SettingRow.choice("Wallpaper", wallpaperName(prefs),
                () -> pickWallpaper(host)));
        rows.add(SettingRow.choice("Apps per row", columnsName(host),
                () -> pickColumns(host)));
        rows.add(SettingRow.choice("App names", labelName(prefs),
                () -> pickLabels(host)));
        rows.add(SettingRow.toggle("24-hour clock", prefs.clock24(),
                () -> {
                    prefs.setClock24(!prefs.clock24());
                    refresh(host);
                }));
        rows.add(SettingRow.toggle("Show date", prefs.showDate(),
                () -> {
                    prefs.setShowDate(!prefs.showDate());
                    refresh(host);
                }));
        rows.add(SettingRow.toggle("Show seconds", prefs.showSeconds(),
                () -> {
                    prefs.setShowSeconds(!prefs.showSeconds());
                    refresh(host);
                }));

        rows.add(SettingRow.header("Screen Saver"));
        rows.add(SettingRow.choice("Start after", delayName(prefs),
                () -> pickScreensaverDelay(host)));
        rows.add(SettingRow.choice("Each scene", sceneName(prefs),
                () -> pickSceneDuration(host)));
        rows.add(SettingRow.choice("Clock", screensaverClockName(prefs),
                () -> pickScreensaverClock(host)));
        rows.add(SettingRow.action("Screen saver settings", () -> Ui.openDreamSettings(host)));

        rows.add(SettingRow.header("Apps"));
        rows.add(SettingRow.choice("Hidden apps", prefs.hidden().isEmpty() ? "None" : "Manage",
                () -> pickHiddenApps(host)));
        rows.add(SettingRow.action("Reload apps", () -> {
            AppRepository.invalidate();
            Ui.toast(host, "Apps will reload when you go back");
        }));
        rows.add(SettingRow.action("Reset app order", () -> {
            prefs.setOrder("");
            Ui.toast(host, "App order reset");
        }));

        rows.add(SettingRow.header("Tools"));
        rows.add(SettingRow.push("Apps", "disable and uninstall", () -> AppPanel.show(host)));
        rows.add(SettingRow.push("Device", "memory, storage, battery", () -> DebugPanel.show(host)));
        rows.add(SettingRow.push("Setup guide", "turn on debugging, step by step",
                () -> SetupGuide.show(host)));
        rows.add(SettingRow.push("Files", "browse and share", () -> ToolsPanel.openFiles(host)));
        rows.add(SettingRow.push("Browser", "with an ad blocker", () -> ToolsPanel.openBrowser(host)));

        rows.add(SettingRow.header("System"));
        rows.add(SettingRow.action(Ui.isDefaultLauncher(host)
                        ? "Sonora is the default launcher" : "Set as default launcher",
                () -> Ui.openDefaultLauncherSettings(host)));
        rows.add(SettingRow.action("About Sonora", () -> showAbout(host)));
        rows.add(SettingRow.danger("Reset all settings", () -> {
            prefs.reset();
            Ui.toast(host, "Settings reset");
            refresh(host);
        }));
        return rows;
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

    private static void pickWallpaper(ToolActivity host) {
        int count = Scenes.ALL.size();
        String[] names = new String[count + 1];
        for (int i = 0; i < count; i++) {
            names[i] = Scenes.ALL.get(i).name;
        }
        names[count] = "Choose a photo…";
        host.choose("Wallpaper", names, host.prefs().wallpaper(), index -> {
            if (index == count) {
                host.pickWallpaperPhoto();
            } else {
                host.prefs().setWallpaper(index, null);
                refresh(host);
            }
        });
    }

    private static void pickColumns(ToolActivity host) {
        String[] options = {"Auto", "4", "5", "6", "7", "8"};
        int stored = host.prefs().columns();
        int current = stored == 0 ? 0 : stored - 3;
        host.choose("Apps per row", options, current, index -> {
            host.prefs().setColumns(index == 0 ? 0 : index + 3);
            refresh(host);
        });
    }

    private static void pickLabels(ToolActivity host) {
        String[] options = {"Hidden", "Focused only", "Always"};
        host.choose("App names", options, host.prefs().labels(), index -> {
            host.prefs().setLabels(index);
            refresh(host);
        });
    }

    private static void pickScreensaverDelay(ToolActivity host) {
        String[] options = {"Never", "1 min", "3 min", "5 min", "10 min", "30 min"};
        int[] seconds = {0, 60, 180, 300, 600, 1800};
        int current = 0;
        int value = host.prefs().screensaverDelaySec();
        for (int i = 0; i < seconds.length; i++) {
            if (seconds[i] == value) {
                current = i;
            }
        }
        host.choose("Start after", options, current, index -> {
            host.prefs().setScreensaverDelaySec(seconds[index]);
            refresh(host);
        });
    }

    private static void pickSceneDuration(ToolActivity host) {
        String[] options = {"30 s", "45 s", "60 s", "90 s", "2 min", "5 min"};
        int[] seconds = {30, 45, 60, 90, 120, 300};
        int current = 0;
        int value = host.prefs().screensaverSceneSec();
        for (int i = 0; i < seconds.length; i++) {
            if (seconds[i] == value) {
                current = i;
            }
        }
        host.choose("Each scene", options, current, index -> {
            host.prefs().setScreensaverSceneSec(seconds[index]);
            refresh(host);
        });
    }

    private static void pickScreensaverClock(ToolActivity host) {
        String[] options = {"On tap", "Always", "Never"};
        host.choose("Clock in screen saver", options, host.prefs().screensaverClock(), index -> {
            host.prefs().setScreensaverClock(index);
            refresh(host);
        });
    }

    private static void pickHiddenApps(ToolActivity host) {
        Set<String> hidden = host.prefs().hidden();
        if (hidden.isEmpty()) {
            Ui.toast(host, "No hidden apps");
            return;
        }
        List<String> names = new ArrayList<>();
        List<String> packages = new ArrayList<>();
        for (com.mitun.sonora.AppEntry entry : AppRepository.get(host, 0)) {
            if (hidden.contains(entry.packageName)) {
                names.add(entry.label);
                packages.add(entry.packageName);
            }
        }
        if (names.isEmpty()) {
            Ui.toast(host, "No hidden apps");
            return;
        }
        host.choose("Unhide app", names.toArray(new String[0]), -1, index -> {
            Set<String> current = host.prefs().hidden();
            current.remove(packages.get(index));
            host.prefs().setHidden(current);
            refresh(host);
        });
    }

    private static String wallpaperName(Prefs prefs) {
        if (prefs.wallpaper() < 0) {
            return "Photo";
        }
        return Scenes.ALL.get(Scenes.safeIndex(prefs.wallpaper())).name;
    }

    private static String columnsName(ToolActivity host) {
        int stored = host.prefs().columns();
        if (stored != 0) {
            return String.valueOf(stored);
        }
        int widthDp = Math.round(host.getResources().getDisplayMetrics().widthPixels
                / host.getResources().getDisplayMetrics().density);
        return "Auto (" + Grid.resolve(widthDp, 0).columns + ")";
    }

    private static String labelName(Prefs prefs) {
        switch (prefs.labels()) {
            case Prefs.LABELS_FOCUSED:
                return "Focused only";
            case Prefs.LABELS_ALL:
                return "Always";
            default:
                return "Hidden";
        }
    }

    private static String delayName(Prefs prefs) {
        int sec = prefs.screensaverDelaySec();
        if (sec <= 0) {
            return "Never";
        }
        if (sec % 3600 == 0) {
            return (sec / 3600) + " hr";
        }
        return (sec / 60) + " min";
    }

    private static String sceneName(Prefs prefs) {
        return prefs.screensaverSceneSec() + " s";
    }

    private static String screensaverClockName(Prefs prefs) {
        switch (prefs.screensaverClock()) {
            case 1:
                return "Always";
            case 2:
                return "Never";
            default:
                return "On tap";
        }
    }
}
