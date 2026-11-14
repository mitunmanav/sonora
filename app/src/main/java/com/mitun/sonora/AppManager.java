package com.mitun.sonora;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

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
        public final int versionCode;
        public final String versionName;
        /** Lazily loaded; loading every icon up front is what makes managers slow. */
        public Drawable icon;

        Item(String packageName, String label, boolean systemApp, boolean updatedSystemApp,
             boolean enabled, boolean hasLauncherEntry, long installTime, int versionCode,
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
            String label = info.applicationInfo != null ? info.applicationInfo.loadLabel(pm).toString() : null;
            if (label == null || label.length() == 0) {
                label = info.packageName;
            }
            boolean system = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean updated = (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            out.add(new Item(info.packageName, label, system, updated,
                    app.enabled, hasLauncherEntry(pm, info.packageName),
                    info.firstInstallTime, info.versionCode, info.versionName));
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        cached = out;
        cachedAt = System.currentTimeMillis();
        return out;
    }

    private static boolean hasLauncherEntry(PackageManager pm, String pkg) {
        try {
            return pm.getLaunchIntentForPackage(pkg) != null
                    || pm.getLeanbackLaunchIntentForPackage(pkg) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void invalidate() {
        cached = null;
        cachedAt = 0;
    }
}
