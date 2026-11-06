package com.mitun.sonora.tools;

import com.mitun.sonora.Grid;
import com.mitun.sonora.PackageActions;
import com.mitun.sonora.Prefs;
import com.mitun.sonora.Scenes;
import com.mitun.sonora.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The pickers and the names behind them, in one place.
 *
 * The settings and the setup wizard ask the same questions, so they use the
 * same options and the same words. Every picker takes the work to do after
 * the user has chosen, which is how the screen the picker was opened from
 * refreshes itself.
 */
public final class Pickers {
    private Pickers() {
    }

    public static void wallpaper(ToolActivity host, Runnable after) {
        int count = Scenes.ALL.size();
        String[] names = new String[count + 1];
        for (int i = 0; i < count; i++) {
            names[i] = Scenes.ALL.get(i).name;
        }
        names[count] = "Choose a photo…";
        // A wallpaper that is a photo is stored as -1, which is not a row of
        // the list. The photo row is the one that is ticked then, or the
        // picker would claim nothing is chosen and OK would replace it.
        int stored = host.prefs().wallpaper();
        host.choose("Wallpaper", names, stored < 0 ? count : stored, index -> {
            if (index == count) {
                host.pickWallpaperPhoto();
            } else {
                host.prefs().setWallpaper(index, null);
                if (after != null) {
                    after.run();
                }
            }
        });
    }

    public static void columns(ToolActivity host, Runnable after) {
        String[] options = {"Auto", "4", "5", "6", "7", "8"};
        int stored = host.prefs().columns();
        // A stored value outside the choices ticks nothing: ticking "Auto"
        // would turn an honest OK into a silent change.
        int selected = stored == 0 ? 0 : stored >= 4 && stored <= 8 ? stored - 3 : -1;
        host.choose("Apps per row", options, selected, index -> {
            host.prefs().setColumns(index == 0 ? 0 : index + 3);
            if (after != null) {
                after.run();
            }
        });
    }

    public static void labels(ToolActivity host, Runnable after) {
        String[] options = {"Hidden", "Focused only", "Always"};
        host.choose("App names", options, host.prefs().labels(), index -> {
            host.prefs().setLabels(index);
            if (after != null) {
                after.run();
            }
        });
    }

    /** One row for the whole clock, so nobody has to think about two toggles. */
    public static void clock(ToolActivity host, Runnable after) {
        String[] options = {"12-hour", "24-hour", "24-hour with seconds"};
        host.choose("Clock", options, clockIndex(host.prefs()), index -> {
            host.prefs().setClock24(index != 0);
            host.prefs().setShowSeconds(index == 2);
            if (after != null) {
                after.run();
            }
        });
    }

    public static void screensaverDelay(ToolActivity host, Runnable after) {
        String[] options = {"Never", "1 min", "3 min", "5 min", "10 min", "30 min"};
        int[] seconds = {0, 60, 180, 300, 600, 1800};
        host.choose("Start after", options, secondsIndex(host.prefs().screensaverDelaySec(), seconds),
                index -> {
                    host.prefs().setScreensaverDelaySec(seconds[index]);
                    if (after != null) {
                        after.run();
                    }
                });
    }

    public static void sceneDuration(ToolActivity host, Runnable after) {
        String[] options = {"30 s", "45 s", "60 s", "90 s", "2 min", "5 min"};
        int[] seconds = {30, 45, 60, 90, 120, 300};
        host.choose("Each scene", options, secondsIndex(host.prefs().screensaverSceneSec(), seconds),
                index -> {
                    host.prefs().setScreensaverSceneSec(seconds[index]);
                    if (after != null) {
                        after.run();
                    }
                });
    }

    public static void screensaverClock(ToolActivity host, Runnable after) {
        String[] options = {"On tap", "Always", "Never"};
        host.choose("Clock in the screen saver", options, host.prefs().screensaverClock(), index -> {
            host.prefs().setScreensaverClock(index);
            if (after != null) {
                after.run();
            }
        });
    }

    public static void mode(ToolActivity host, Runnable after) {
        String[] options = {"Default, just the basics", "Technical, every setting"};
        host.choose("Mode", options, host.prefs().mode(), index -> {
            host.prefs().setMode(index);
            if (after != null) {
                after.run();
            }
        });
    }

    /** Unhide one of the apps that were hidden from the home screen. */
    public static void hiddenApps(ToolActivity host, Runnable after) {
        Set<String> hidden = host.prefs().hidden();
        // The list comes straight from the pref, not the app list: a hide
        // that went through the app manager switched the launcher entry off,
        // so a launchable-apps query could never name it back.
        new Thread(() -> {
            List<String> names = new ArrayList<>();
            List<String> packages = new ArrayList<>();
            boolean failed = false;
            android.content.pm.PackageManager pm = host.getPackageManager();
            for (String pkg : hidden) {
                try {
                    names.add(pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString());
                    packages.add(pkg);
                } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
                    // Gone is not hidden: it cannot come back either.
                } catch (RuntimeException e) {
                    failed = true;
                }
            }
            final boolean readFailed = failed;
            host.runOnUiThread(() -> {
                if (host.isFinishing() || host.isDestroyed()) {
                    return;
                }
                if (!SettingsPanel.topIsPage(host, "Apps")) {
                    // The user left the Apps screen while the list was read.
                    return;
                }
                if (readFailed) {
                    Ui.toast(host, "The list of apps could not be read");
                    return;
                }
                if (names.isEmpty()) {
                    Ui.toast(host, "No hidden apps");
                    return;
                }
                host.choose("Unhide app", names.toArray(new String[0]), -1, index -> {
                    String pkg = packages.get(index);
                    Set<String> current = host.prefs().hidden();
                    current.remove(pkg);
                    host.prefs().setHidden(current);
                    // The other half of the hide: a launcher entry switched
                    // off by the app manager is switched back on here.
                    PackageActions.Result result =
                            PackageActions.setHidden(host, pkg, false);
                    if (!result.ok) {
                        Ui.toast(host, result.message);
                    }
                    if (after != null) {
                        after.run();
                    }
                });
            });
        }, "sonora-hidden-apps").start();
    }

    // The names the rows show.

    public static String wallpaperName(Prefs prefs) {
        if (prefs.wallpaper() < 0) {
            return "A photo";
        }
        return Scenes.ALL.get(Scenes.safeIndex(prefs.wallpaper())).name;
    }

    public static String columnsName(ToolActivity host) {
        int stored = host.prefs().columns();
        if (stored != 0) {
            return String.valueOf(stored);
        }
        int widthDp = Math.round(host.getResources().getDisplayMetrics().widthPixels
                / host.getResources().getDisplayMetrics().density);
        return "Auto (" + Grid.resolve(widthDp, 0).columns + ")";
    }

    public static String labelName(Prefs prefs) {
        switch (prefs.labels()) {
            case Prefs.LABELS_FOCUSED:
                return "Focused only";
            case Prefs.LABELS_ALL:
                return "Always";
            default:
                return "Hidden";
        }
    }

    public static String clockName(Prefs prefs) {
        if (!prefs.clock24()) {
            return "12-hour";
        }
        return prefs.showSeconds() ? "24-hour with seconds" : "24-hour";
    }

    public static String delayName(Prefs prefs) {
        int sec = prefs.screensaverDelaySec();
        if (sec <= 0) {
            return "Never";
        }
        if (sec % 3600 == 0) {
            return (sec / 3600) + " hr";
        }
        return (sec / 60) + " min";
    }

    public static String sceneName(Prefs prefs) {
        return prefs.screensaverSceneSec() + " s";
    }

    public static String screensaverClockName(Prefs prefs) {
        switch (prefs.screensaverClock()) {
            case 1:
                return "Always";
            case 2:
                return "Never";
            default:
                return "On tap";
        }
    }

    public static String modeName(Prefs prefs) {
        return prefs.technical() ? "Technical" : "Default";
    }

    public static String hiddenName(ToolActivity host) {
        int count = installedCount(host, host.prefs().hidden());
        return count == 0 ? "None" : count + (count == 1 ? " app" : " apps");
    }

    public static String favouritesName(ToolActivity host) {
        int count = installedCount(host, host.prefs().favourites());
        return count == 0 ? "None" : count + (count == 1 ? " app" : " apps");
    }

    /**
     * How many of the packages are still installed.
     *
     * An uninstalled package cannot be hidden or pinned any more, and it can
     * never be cleared from the list either - counting it would report a row
     * that does not exist.
     */
    private static int installedCount(android.content.Context context,
                                      java.util.Collection<String> packages) {
        android.content.pm.PackageManager pm = context.getPackageManager();
        int count = 0;
        for (String pkg : packages) {
            try {
                pm.getPackageInfo(pkg, 0);
                count++;
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
                // Gone is not hidden, and it is not a favourite either.
            } catch (RuntimeException e) {
                // The system cannot say, so it counts: saying fewer would hide
                // a real row.
                count++;
            }
        }
        return count;
    }

    private static int clockIndex(Prefs prefs) {
        if (!prefs.clock24()) {
            return 0;
        }
        return prefs.showSeconds() ? 2 : 1;
    }

    private static int secondsIndex(int value, int[] seconds) {
        for (int i = 0; i < seconds.length; i++) {
            if (seconds[i] == value) {
                return i;
            }
        }
        // A stored value that is not a choice ticks nothing: ticking the
        // first row would turn an honest OK into a silent change.
        return -1;
    }
}
