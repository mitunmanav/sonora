package com.mitun.sonora;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.view.Window;

import com.mitun.sonora.ui.Ui;

/** What the app asks of Android itself: immersion, roles and settings screens. */
public final class Platform {

    private Platform() {
    }

    public static void immersive(Activity activity) {
        immersive(activity.getWindow());
    }

    public static void immersive(Window window) {
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    public static boolean isDefaultLauncher(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo info = context.getPackageManager()
                    .resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            return info != null && info.activityInfo != null
                    && context.getPackageName().equals(info.activityInfo.packageName);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void openDefaultLauncherSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
        if (!start(context, intent)) {
            if (!start(context, new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) {
                if (!start(context, new Intent(Settings.ACTION_SETTINGS))) {
                    Ui.toast(context, "No launcher settings available");
                }
            }
        }
    }

    /** What the TV's own screen saver setting points at. */
    public enum DreamState { SELECTED, OTHER, STALE, UNKNOWN }

    /**
     * Reads the system screen saver setting. SELECTED means Sonora's dream is
     * the one the TV shows; STALE means it names a package that is not
     * installed, which leaves the TV with a blank screen saver.
     */
    public static DreamState dreamState(Context context) {
        String components;
        try {
            components = Settings.Secure.getString(
                    context.getContentResolver(), "screensaver_components");
        } catch (RuntimeException e) {
            return DreamState.UNKNOWN;
        }
        if (components == null || components.trim().isEmpty()) {
            return DreamState.UNKNOWN;
        }
        // A package that merely contains our name is not us: compare the
        // package token of each component, the part before the '/'.
        for (String component : components.split(",")) {
            int slash = component.indexOf('/');
            String name = (slash >= 0 ? component.substring(0, slash) : component).trim();
            if (context.getPackageName().equals(name)) {
                return DreamState.SELECTED;
            }
        }
        int slash = components.indexOf('/');
        String pkg = (slash >= 0 ? components.substring(0, slash) : components).trim();
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return DreamState.OTHER;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return DreamState.STALE;
        }
    }

    public static void openUsageAccessSettings(Context context) {
        if (!start(context, new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))) {
            Ui.toast(context, "No usage access settings available");
        }
    }

    public static void openDreamSettings(Context context) {
        if (!start(context, new Intent(Settings.ACTION_DREAM_SETTINGS))) {
            Ui.toast(context, "No screen saver settings available");
        }
    }

    private static boolean start(Context context, Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
