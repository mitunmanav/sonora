package com.mitun.sonora.tools;

import android.content.pm.PackageManager;

import com.mitun.sonora.PackageActions;
import com.mitun.sonora.ui.SettingRow;
import com.mitun.sonora.ui.Ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.mitun.sonora.Platform;

/**
 * Turns off apps the user does not want without uninstalling them.
 *
 * The list is a curated set of packages common on Android TV. Asking the
 * system about each one is a binder call, so the screen fills in on a
 * background thread the way the favourites list does.
 */
public final class DebloatPanel {
    private static final String TITLE = "Debloat";

    /** The tag the panel carries on the stack. */
    static final Object TAG = new Object();

    private static final class BloatItem {
        final String packageName;
        final String name;
        /** A warning shown before the row when turning it off can go wrong. */
        final String warn;

        BloatItem(String packageName, String name, String warn) {
            this.packageName = packageName;
            this.name = name;
            this.warn = warn;
        }
    }

    /** Common bloatware packages on Android TV. */
    private static final List<BloatItem> BLOATWARE = Arrays.asList(
            new BloatItem("com.google.android.tvlauncher", "Google TV Launcher",
                    "The stock launcher. Turn it off only if Sonora is your launcher."),
            new BloatItem("com.google.android.gms", "Google Play Services",
                    "Core services for Google apps. Turn it off only if you never use them."),
            new BloatItem("com.google.android.youtube.tv", "YouTube", null),
            new BloatItem("com.netflix.ninja", "Netflix", null),
            new BloatItem("com.amazon.amazonvideo.livingroom", "Prime Video", null),
            new BloatItem("com.spotify.tv.android", "Spotify", null),
            new BloatItem("com.google.android.apps.youtube.gaming", "YouTube Gaming", null),
            new BloatItem("com.google.android.videos", "Google Play Movies", null),
            new BloatItem("com.google.android.music", "Google Play Music", null),
            new BloatItem("com.disney.disneyplus", "Disney+", null),
            new BloatItem("com.hulu.livingroomplus", "Hulu", null),
            new BloatItem("com.google.android.apps.paidtasks", "Google Opinion Rewards", null),
            new BloatItem("com.google.android.apps.photos", "Google Photos", null),
            new BloatItem("com.google.android.apps.docs", "Google Docs", null),
            new BloatItem("com.google.android.apps.books", "Google Play Books", null)
    );

    private DebloatPanel() {
    }

    public static void show(ToolActivity host) {
        List<SettingRow> reading = new ArrayList<>();
        reading.add(SettingRow.value("Reading", "…"));
        host.stack().pushSettings(TAG, TITLE, reading);
        reload(host);
    }

    /**
     * Re-reads the toggles when the screen is back on top: an app can be
     * turned off or removed while the panel sits under a system screen.
     */
    static void resumed(ToolActivity host) {
        if (host.stack().topTag() == TAG) {
            reload(host);
        }
    }

    private static void reload(ToolActivity host) {
        new Thread(() -> {
            List<SettingRow> built;
            try {
                built = rows(host);
            } catch (RuntimeException e) {
                built = new ArrayList<>();
                built.add(SettingRow.note("The list of apps could not be read."));
            }
            final List<SettingRow> rows = built;
            host.runOnUiThread(() -> {
                if (host.isFinishing() || host.isDestroyed()) {
                    return;
                }
                if (host.stack().topTag() != TAG) {
                    // The user left the screen while the list was being read.
                    return;
                }
                host.stack().refreshSettings(TAG, TITLE, rows);
            });
        }, "sonora-debloat").start();
    }

    private static List<SettingRow> rows(ToolActivity host) {
        PackageManager pm = host.getPackageManager();
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.note("Turn off apps you do not use. They stay installed "
                + "but cannot be opened."));
        boolean any = false;
        for (BloatItem item : BLOATWARE) {
            final boolean enabled;
            try {
                enabled = pm.getApplicationInfo(item.packageName, 0).enabled;
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            any = true;
            if (item.warn != null) {
                rows.add(SettingRow.note(item.warn));
            }
            if (enabled && "com.google.android.tvlauncher".equals(item.packageName)
                    && !Platform.isDefaultLauncher(host)) {
                // Turning off the launcher that still answers HOME leaves the
                // button with nowhere to go, so there is no switch to flip.
                rows.add(SettingRow.value(item.name,
                        "On. Set Sonora as the home screen before turning it off."));
            } else {
                rows.add(SettingRow.toggle(item.name, enabled,
                        () -> toggle(host, item, enabled)));
            }
        }
        if (!any) {
            rows.add(SettingRow.note("No known bloatware found on this TV."));
        }
        return rows;
    }

    private static void toggle(ToolActivity host, BloatItem item, boolean enabled) {
        PackageActions.Result result =
                PackageActions.setPackageEnabled(host, item.packageName, !enabled);
        Ui.toast(host, result.message);
        if (!result.ok) {
            return;
        }
        // A turned-off app leaves the home screen: the epoch bump drops the
        // cached lists in the launcher and the app manager.
        host.prefs().bumpAppsEpoch();
        reload(host);
    }
}
