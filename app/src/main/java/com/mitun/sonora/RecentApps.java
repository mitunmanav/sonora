package com.mitun.sonora;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.mitun.sonora.ui.Bitmaps;

/**
 * The apps the switcher can offer, most recent first.
 *
 * Android does not tell a launcher which apps are running, so the list comes
 * from two honest sources: the system's usage history when the user has granted
 * usage access, and the apps Sonora itself opened this session either way. With
 * neither, the switcher is empty and says so.
 */
public final class RecentApps {
    private static final int LIMIT = 10;
    /** How far back usage history counts as "recent". */
    private static final long WINDOW_MS = 24L * 60 * 60 * 1000;
    private static final int SESSION_LIMIT = 30;

    /** package name to when Sonora last opened it, oldest first. */
    private static final LinkedHashMap<String, Long> session = new LinkedHashMap<>();

    private RecentApps() {
    }

    public static synchronized void noteLaunched(String packageName) {
        if (packageName == null || packageName.length() == 0) {
            return;
        }
        session.remove(packageName);
        session.put(packageName, System.currentTimeMillis());
        while (session.size() > SESSION_LIMIT) {
            Iterator<String> it = session.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    /** True when the user has let Sonora read usage history. */
    public static boolean usageGranted(Context context) {
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            // checkOpNoThrow exists since API 23; the unsafe variant is API 29.
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The recent apps as launchable entries, newest first. Packages that are no
     * longer installed or cannot be launched are left out.
     */
    public static List<AppEntry> recent(Context context) {
        Map<String, Long> lastUsed = usageTimes(context);
        for (Map.Entry<String, Long> entry : sessionSnapshot().entrySet()) {
            Long seen = lastUsed.get(entry.getKey());
            if (seen == null || entry.getValue() > seen) {
                lastUsed.put(entry.getKey(), entry.getValue());
            }
        }
        List<Map.Entry<String, Long>> ordered = new ArrayList<>(lastUsed.entrySet());
        Collections.sort(ordered, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return Long.compare(b.getValue(), a.getValue());
            }
        });

        PackageManager pm = context.getPackageManager();
        List<AppEntry> out = new ArrayList<>();
        for (Map.Entry<String, Long> entry : ordered) {
            if (out.size() >= LIMIT) {
                break;
            }
            AppEntry app = describe(context, pm, entry.getKey());
            if (app != null) {
                out.add(app);
            }
        }
        return out;
    }

    /** package name to last ACTIVITY_RESUMED time, when usage access allows. */
    private static Map<String, Long> usageTimes(Context context) {
        Map<String, Long> lastUsed = new LinkedHashMap<>();
        if (!usageGranted(context)) {
            return lastUsed;
        }
        UsageStatsManager stats = (UsageStatsManager) context.getSystemService(
                Context.USAGE_STATS_SERVICE);
        if (stats == null) {
            return lastUsed;
        }
        long now = System.currentTimeMillis();
        try {
            UsageEvents events = stats.queryEvents(now - WINDOW_MS, now);
            if (events == null) {
                return lastUsed;
            }
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                if (!events.getNextEvent(event)
                        || event.getEventType() != UsageEvents.Event.ACTIVITY_RESUMED) {
                    continue;
                }
                String pkg = event.getPackageName();
                if (pkg == null || pkg.equals(context.getPackageName())) {
                    continue;
                }
                Long seen = lastUsed.get(pkg);
                if (seen == null || event.getTimeStamp() > seen) {
                    lastUsed.put(pkg, event.getTimeStamp());
                }
            }
        } catch (RuntimeException e) {
            // A device that refuses the query gets no history, not a crash.
        }
        return lastUsed;
    }

    private static synchronized Map<String, Long> sessionSnapshot() {
        return new LinkedHashMap<>(session);
    }

    /** Label and icon for one package, or null when it cannot be launched. */
    private static AppEntry describe(Context context, PackageManager pm, String pkg) {
        try {
            if (context.getPackageName().equals(pkg)
                    || pm.getLeanbackLaunchIntentForPackage(pkg) == null
                            && pm.getLaunchIntentForPackage(pkg) == null) {
                return null;
            }
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            CharSequence label = info.loadLabel(pm);
            Drawable drawable = info.loadIcon(pm);
            Bitmap icon = Bitmaps.drawableToBitmap(drawable, 192, 192, true);
            return new AppEntry(label == null || label.length() == 0 ? pkg
                    : label.toString(), pkg, null, icon, null, 0, false);
        } catch (RuntimeException | android.content.pm.PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
