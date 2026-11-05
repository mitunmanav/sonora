package com.mitun.sonora;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

/** One launchable app, with pre-rendered art kept small enough to be cheap. */
public final class AppEntry {
    public final String label;
    public final String packageName;
    public final ComponentName component;
    public final Bitmap icon;
    public final Bitmap banner;
    public final int accent;
    public final boolean settings;

    public AppEntry(String label, String packageName, ComponentName component,
                    Bitmap icon, Bitmap banner, int accent, boolean settings) {
        this.label = label;
        this.packageName = packageName;
        this.component = component;
        this.icon = icon;
        this.banner = banner;
        this.accent = accent;
        this.settings = settings;
    }

    public boolean launch(Context context) {
        if (settings) {
            return false;
        }
        Intent intent = null;
        try {
            if (packageName != null) {
                intent = context.getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
                if (intent == null) {
                    intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
                }
            }
            if (intent == null && component != null) {
                intent = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                        .setComponent(component);
            }
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                RecentApps.noteLaunched(packageName);
                return true;
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }
}
