package com.mitun.sonora;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every package installed on the device, not just the launchable ones - the app
 * manager needs to see the bloat a launcher hides.
 *
 * The list is built once and cached. TVs kill background processes, so
 * re-querying PackageManager on every resume is a real cost.
 */
public final class AppManager {
    /** One installed package. */
    public static final class Item {
        public final String packageName;
        public final String label;
        public final boolean systemApp;
        public final boolean updatedSystemApp;
        public final boolean enabled;
        public final boolean hasLauncherEntry;
        public final long installTime;
        public final long versionCode;
        public final String versionName;
        /** Lazily loaded; loading every icon up front is what makes managers slow. */
        public volatile Bitmap icon;
        /** Hidden from the home screen by the user. */
        public boolean hidden;

        Item(String packageName, String label, boolean systemApp, boolean updatedSystemApp,
             boolean enabled, boolean hasLauncherEntry, long installTime, long versionCode,
             String versionName) {
            this.packageName = packageName;
            this.label = label;
            this.systemApp = systemApp;
            this.updatedSystemApp = updatedSystemApp;
            this.enabled = enabled;
            this.hasLauncherEntry = hasLauncherEntry;
            this.installTime = installTime;
            this.versionCode = versionCode;
            this.versionName = versionName;
        }
    }

    private static volatile List<Item> cached;
    private static volatile long cachedAt;

    private AppManager() {
    }

    /** The cached list, loading it on a background thread if it is stale. */
    public static List<Item> get(Context context) {
        List<Item> local = cached;
        if (local != null && System.currentTimeMillis() - cachedAt < 30_000L) {
            return local;
        }
        return load(context);
    }

    /** Rebuilds the list. Call off the main thread. */
    public static List<Item> load(Context context) {
        PackageManager pm = context.getPackageManager();
        List<Item> out = new ArrayList<>();
        List<PackageInfo> packages;
        try {
            packages = pm.getInstalledPackages(0);
        } catch (RuntimeException e) {
            packages = Collections.emptyList();
        }
        for (PackageInfo info : packages) {
            if (info == null || info.packageName == null) {
                continue;
            }
            ApplicationInfo app = info.applicationInfo;
            if (app == null) {
                // A package with no application info cannot be shown, and
                // touching it would kill the thread it is loaded on.
                continue;
            }
            CharSequence name = null;
            try {
                name = app.loadLabel(pm);
            } catch (RuntimeException ignored) {
            }
            String label = name == null ? null : name.toString();
            if (label == null || label.length() == 0) {
                label = info.packageName;
            }
            boolean system = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean updated = (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            ComponentName launcher = launcherComponent(pm, info.packageName);
            boolean hasEntry = launcher != null;
            boolean hidden = false;
            if (hasEntry) {
                try {
                    hidden = pm.getComponentEnabledSetting(launcher)
                            == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                } catch (RuntimeException e) {
                    hidden = false;
                }
            }
            @SuppressWarnings("deprecation")
            long versionCode = android.os.Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
            String versionName = info.versionName == null ? "Unknown" : info.versionName;
            out.add(new Item(info.packageName, label, system, updated,
                    app.enabled, hasEntry,
                    info.firstInstallTime, versionCode, versionName));
            out.get(out.size() - 1).hidden = hidden;
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        cached = out;
        cachedAt = System.currentTimeMillis();
        return out;
    }

    /** The component that launches the app, or null when it has none. */
    private static ComponentName launcherComponent(PackageManager pm, String pkg) {
        try {
            // The TV entry comes first, the way the grid launches the app.
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) {
                intent = pm.getLaunchIntentForPackage(pkg);
            }
            return intent == null ? null : intent.getComponent();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void invalidate() {
        cached = null;
        cachedAt = 0;
    }

    /**
     * Drops the lazily decoded icons but keeps the package list.
     *
     * The manager shows a row per installed package, and each icon is 36 KB
     * once decoded. Nothing outside that screen needs them, so they do not
     * outlive it.
     */
    public static void trimIcons() {
        List<Item> local = cached;
        if (local == null) {
            return;
        }
        for (Item item : local) {
            item.icon = null;
        }
    }
}
