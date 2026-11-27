package com.mitun.sonora;

import android.content.Context;
import android.content.pm.ChangedPackages;
import android.content.pm.LauncherApps;
import android.os.Build;
import android.os.UserHandle;

/**
 * Notices apps installed, removed or updated outside Sonora: from the Play
 * Store, over adb, or in the system settings.
 *
 * There is no receiver. On Android 8 and up the package manager keeps a
 * sequence number that moves with every change, and asking whether it moved
 * is one cheap call on resume. Below that a {@link LauncherApps.Callback} is
 * held by the home screen while it exists; it is told about a change and does
 * nothing else.
 */
public final class AppChanges {
    private final Context context;
    /** The sequence number the app list was read at; -1 before the first read. */
    private int sequence = -1;
    private volatile boolean dirty;
    private LauncherApps.Callback callback;

    public AppChanges(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Pure: whether the package manager moved on since the list was read. */
    static boolean moved(int known, int now) {
        return known >= 0 && now != known;
    }

    /**
     * Call just before the app list is read, so a change that lands during
     * the read still counts on the next resume.
     */
    public void markRead() {
        dirty = false;
        if (Build.VERSION.SDK_INT >= 26) {
            ChangedPackages since = changedSince(Math.max(0, sequence));
            if (since != null) {
                sequence = since.getSequenceNumber();
            } else if (sequence < 0) {
                // Nothing changed since boot: the count is still at its start.
                sequence = 0;
            }
        }
    }

    /** True when an app changed since {@link #markRead()}. */
    public boolean changed() {
        if (Build.VERSION.SDK_INT >= 26) {
            if (sequence < 0) {
                return false;
            }
            // Null, the usual answer, means nothing moved: one small call.
            ChangedPackages since = changedSince(sequence);
            return since != null && moved(sequence, since.getSequenceNumber());
        }
        return dirty;
    }

    private ChangedPackages changedSince(int from) {
        try {
            return context.getPackageManager().getChangedPackages(from);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Below Android 8: listen while the home screen exists. */
    public void start() {
        if (Build.VERSION.SDK_INT >= 26 || callback != null) {
            return;
        }
        LauncherApps apps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if (apps == null) {
            return;
        }
        callback = new LauncherApps.Callback() {
            @Override
            public void onPackageRemoved(String packageName, UserHandle user) {
                dirty = true;
            }

            @Override
            public void onPackageAdded(String packageName, UserHandle user) {
                dirty = true;
            }

            @Override
            public void onPackageChanged(String packageName, UserHandle user) {
                dirty = true;
            }

            @Override
            public void onPackagesAvailable(String[] packageNames, UserHandle user,
                                            boolean replacing) {
                dirty = true;
            }

            @Override
            public void onPackagesUnavailable(String[] packageNames, UserHandle user,
                                              boolean replacing) {
                dirty = true;
            }
        };
        try {
            apps.registerCallback(callback);
        } catch (RuntimeException e) {
            callback = null;
        }
    }

    public void stop() {
        if (callback == null) {
            return;
        }
        LauncherApps apps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        try {
            if (apps != null) {
                apps.unregisterCallback(callback);
            }
        } catch (RuntimeException ignored) {
        }
        callback = null;
    }
}
