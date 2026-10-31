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
        public final int versionCode;
        public final String versionName;
        /** Lazily loaded; loading every icon up front is what makes managers slow. */
        public Bitmap icon;
        /** Hidden from the home screen by the user. */
        public boolean hidden;

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
            ComponentName launcher = launcherComponent(pm, info.packageName);
            boolean hasEntry = launcher != null;
            boolean hidden = hasEntry && pm.getComponentEnabledSetting(launcher)
                    == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            out.add(new Item(info.packageName, label, system, updated,
                    app.enabled, hasEntry,
                    info.firstInstallTime, info.versionCode, info.versionName));
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
            Intent intent = pm.getLaunchIntentForPackage(pkg);
            if (intent == null) {
                intent = pm.getLeanbackLaunchIntentForPackage(pkg);
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
}
