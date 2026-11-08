package com.mitun.sonora;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import java.io.File;
import java.util.List;

/**
 * The actions the app manager offers, and the honest limits of each one.
 *
 * On a retail, non-rooted Android TV a normal app can:
 *   - hide an app from every launcher by disabling its launcher activity
 *     (CHANGE_COMPONENT_ENABLED_STATE is signature|privileged: it works on
 *     builds where Sonora ships as a privileged app and fails honestly
 *     everywhere else);
 *   - uninstall an app with the user's confirmation (REQUEST_DELETE_PACKAGES);
 *   - force-stop a background app (KILL_BACKGROUND_PROCESSES). Clearing an
 *     app's data takes a privileged permission, so the user is sent to the
 *     system page that has it.
 *
 * What it cannot do is delete a preinstalled app from /system: the uninstall
 * is applied per-user, which hides the app but leaves the system copy in place.
 * The UI says so rather than pretending otherwise.
 */
public final class PackageActions {
    private PackageActions() {
    }

    /** Result of an action, so the UI can report what actually happened. */
    public static final class Result {
        public final boolean ok;
        public final String message;

        Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    /**
     * Disables the launcher activities of a package, which hides it from the
     * home screen and from other launchers.
     *
     * Every launcher component is switched, not just the first: a package with
     * both a TV and a phone entry would stay launchable through the one left
     * alone, while the app reported itself hidden.
     */
    public static Result setHidden(Context context, String packageName, boolean hidden) {
        PackageManager pm = context.getPackageManager();
        try {
            if (hidden) {
                java.util.List<ComponentName> launchers =
                        launcherComponents(pm, packageName);
                if (launchers.isEmpty()) {
                    // No launcher entry at all: disable the whole application's
                    // components, the only "hidden" such an app can have.
                    pm.setApplicationEnabledSetting(packageName,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP);
                    if (!isOff(pm, packageName, null)) {
                        return new Result(false, "This device did not hide the app");
                    }
                    return new Result(true, "Hidden");
                }
                for (ComponentName component : launchers) {
                    pm.setComponentEnabledSetting(component,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP);
                }
                // The state is read back: a launcher that still resolves
                // means the device ignored the request.
                if (pm.getLeanbackLaunchIntentForPackage(packageName) != null
                        || pm.getLaunchIntentForPackage(packageName) != null) {
                    return new Result(false, "This device did not hide the app");
                }
                return new Result(true, "Hidden from the home screen");
            }
            // Restoring has to find the disabled components itself: the
            // launch-intent queries cannot see them, so the query asks for
            // them by name - and only for the ones that launch, never for the
            // whole package. Every DISABLED activity restored blindly would
            // also turn on entry points the app ships turned off.
            for (ComponentName component : launcherComponents(pm, packageName, true)) {
                if (pm.getComponentEnabledSetting(component)
                        == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(component,
                            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                            PackageManager.DONT_KILL_APP);
                }
            }
            if (pm.getApplicationEnabledSetting(packageName)
                    == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                // DISABLED_USER is left alone: it came from Debloat, not hiding.
                pm.setApplicationEnabledSetting(packageName,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        PackageManager.DONT_KILL_APP);
            }
            Intent restored = pm.getLeanbackLaunchIntentForPackage(packageName);
            if (restored == null) {
                restored = pm.getLaunchIntentForPackage(packageName);
            }
            if (restored != null || !isOff(pm, packageName, null)) {
                return new Result(true, "Restored");
            }
            return new Result(false, "This device did not restore the app");
        } catch (SecurityException e) {
            return new Result(false, "Not allowed by this device");
        } catch (RuntimeException e) {
            return new Result(false, hidden ? "Could not hide this app"
                    : "Could not restore this app");
        }
    }

    /** The components that make a package launchable, TV entries first. */
    private static java.util.List<ComponentName> launcherComponents(
            PackageManager pm, String packageName) {
        return launcherComponents(pm, packageName, false);
    }

    /**
     * As above.
     *
     * @param includeDisabled asks for the ones a hide turned off too, which a
     *                        plain query cannot see.
     */
    private static java.util.List<ComponentName> launcherComponents(
            PackageManager pm, String packageName, boolean includeDisabled) {
        int flags = includeDisabled ? PackageManager.MATCH_DISABLED_COMPONENTS : 0;
        java.util.List<ComponentName> out = new java.util.ArrayList<>();
        Intent leanback = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(packageName);
        Intent regular = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName);
        for (android.content.pm.ResolveInfo info
                : pm.queryIntentActivities(leanback, flags)) {
            out.add(new ComponentName(packageName, info.activityInfo.name));
        }
        for (android.content.pm.ResolveInfo info
                : pm.queryIntentActivities(regular, flags)) {
            out.add(new ComponentName(packageName, info.activityInfo.name));
        }
        return out;
    }

    /**
     * Whether the app is turned off right now.
     *
     * @param component the launcher component, or null for the whole package.
     */
    private static boolean isOff(PackageManager pm, String packageName, ComponentName component) {
        try {
            if (component != null) {
                return pm.getComponentEnabledSetting(component)
                        == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            }
            int state = pm.getApplicationEnabledSetting(packageName);
            return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Turns a whole package off for this user.
     *
     * This is what "pm disable-user" does, and it is how the apps that were
     * turned off are turned off again after a system update puts them back.
     */
    public static Result setPackageEnabled(Context context, String packageName, boolean enabled) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.setApplicationEnabledSetting(packageName,
                    enabled ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                    PackageManager.DONT_KILL_APP);
            // The state is read back: a device that quietly ignores the
            // request must not be told it worked.
            if (isOff(pm, packageName, null) == enabled) {
                return new Result(false, enabled
                        ? "This device did not restore the app"
                        : "This device did not turn the app off");
            }
            return new Result(true, enabled ? "Restored" : "Turned off");
        } catch (SecurityException e) {
            return new Result(false, "This device does not allow it");
        } catch (RuntimeException e) {
            return new Result(false, "Could not change this app");
        }
    }

    /** Opens the system uninstall confirmation for a package. */
    public static Result requestUninstall(Activity activity, String packageName, String label) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE,
                    Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return new Result(true, "Confirm to uninstall " + label);
        } catch (RuntimeException e) {
            return new Result(false, "This device will not uninstall apps");
        }
    }

    /**
     * Clears another app's data.
     *
     * A normal app cannot do this: CLEAR_APP_USER_DATA is a privileged
     * permission and the public API for it is not exposed. So we do the only
     * honest thing and send the user to the system page that can.
     */
    public static Result clearData(Context context, String packageName) {
        Result info = openAppInfo(context, packageName);
        if (!info.ok) {
            return info;
        }
        return new Result(true, "Use “Clear storage” on the app info page");
    }

    /** Clears our own cached files. Always allowed, and the one thing we can do silently. */
    public static Result clearOwnCache(Context context) {
        try {
            File dir = context.getCacheDir();
            long freed = 0;
            File[] files = dir == null ? null : dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    freed += file.length();
                    delete(file);
                }
            }
            return new Result(true, "Cleared " + SystemInfo.bytes(freed));
        } catch (RuntimeException e) {
            return new Result(false, "Could not clear the cache");
        }
    }

    private static boolean delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete(child);
                }
            }
        }
        return file.delete();
    }

    /**
     * Stops a running app.
     *
     * Only a background app can be stopped this way. An app in the foreground
     * belongs to the user, and saying it was stopped when it was not would be
     * a lie, so the message says what happened.
     */
    public static Result forceStop(Context context, String packageName) {
        try {
            android.app.ActivityManager am =
                    (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return new Result(false, "No activity manager");
            }
            if (!isRunning(context, packageName)) {
                // Nothing was asked to stop, so "Stopped" would be a lie.
                return new Result(true, "Not running");
            }
            am.killBackgroundProcesses(packageName);
            if (isRunning(context, packageName)) {
                return new Result(false, "Still running: only background apps can be stopped");
            }
            return new Result(true, "Stopped");
        } catch (RuntimeException e) {
            return new Result(false, "Could not stop this app");
        }
    }

    /** True when the package still has a process alive. */
    private static boolean isRunning(Context context, String packageName) {
        try {
            android.app.ActivityManager am =
                    (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return false;
            }
            List<android.app.ActivityManager.RunningAppProcessInfo> running =
                    am.getRunningAppProcesses();
            if (running == null) {
                return false;
            }
            for (android.app.ActivityManager.RunningAppProcessInfo info : running) {
                // pkgList names every package inside the process, which a
                // "pkg:remote" style processName comparison would miss.
                if (info != null && info.pkgList != null) {
                    for (String pkg : info.pkgList) {
                        if (packageName.equals(pkg)) {
                            return true;
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    /** Opens the system app-info page for a package. */
    public static Result openAppInfo(Context context, String packageName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return new Result(true, "");
        } catch (RuntimeException e) {
            return new Result(false, "No app info screen on this device");
        }
    }

    /** Launches an app by package name. */
    public static Result launch(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            // The TV entry comes first, the way the grid launches the app.
            Intent intent = pm.getLeanbackLaunchIntentForPackage(packageName);
            if (intent == null) {
                intent = pm.getLaunchIntentForPackage(packageName);
            }
            if (intent == null) {
                return new Result(false, "This app cannot be started");
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return new Result(true, "");
        } catch (RuntimeException e) {
            return new Result(false, "This app cannot be started");
        }
    }

    /** True when the package is a preinstalled app we can only hide, not remove. */
    public static boolean isSystemApp(Context context, String packageName) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
