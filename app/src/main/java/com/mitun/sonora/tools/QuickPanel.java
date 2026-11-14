package com.mitun.sonora.tools;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;

import com.mitun.sonora.ui.SettingRow;
import com.mitun.sonora.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import com.mitun.sonora.Platform;

/**
 * The control centre: the TV's own settings, one tap away.
 *
 * Every row here is a door to a screen the TV already has. Sonora does not
 * re-implement Wi-Fi or sound - it only makes them reachable without walking
 * through the settings first, in the order somebody would want them.
 */
public final class QuickPanel {
    /** The tag the quick settings screen carries on the stack. */
    static final Object TAG = new Object();

    private QuickPanel() {
    }

    public static void show(ToolActivity host) {
        host.stack().pushSettings(TAG, "Quick settings", rows(host));
    }

    public static List<SettingRow> rows(ToolActivity host) {
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.push("Wi-Fi", wifiName(host),
                () -> openWifi(host)));
        rows.add(SettingRow.push("Bluetooth", "",
                () -> openBluetooth(host)));
        rows.add(SettingRow.push("Display", "",
                () -> openDisplay(host)));
        rows.add(SettingRow.push("Sound", "",
                () -> openSound(host)));
        rows.add(SettingRow.push("Keyboard", "",
                () -> openKeyboard(host)));
        rows.add(SettingRow.push("Date & time", "",
                () -> openDateTime(host)));
        rows.add(SettingRow.push("Language", "",
                () -> openLanguage(host)));
        rows.add(SettingRow.push("Screen saver", "Sonora's own",
                () -> SettingsPanel.openScreenSaver(host)));
        rows.add(SettingRow.push("Apps", "", () -> AppPanel.show(host)));
        rows.add(SettingRow.push("Background apps", "",
                () -> openFirst(host,
                        new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
                        new Intent(Settings.ACTION_APPLICATION_SETTINGS),
                        new Intent(Settings.ACTION_SETTINGS))));
        rows.add(SettingRow.note("Android does not give launchers a clear-all control. Use "
                + "the TV's Recents screen, if available, to dismiss recent apps; Android settings "
                + "can stop apps one at a time."));
        rows.add(SettingRow.push("System update", "",
                () -> openUpdate(host)));
        rows.add(SettingRow.action("Tidy up again", () -> tidyUpNow(host)));
        rows.add(SettingRow.note("A system update from the manufacturer puts the trackers and "
                + "the TV's own sponsored launcher back. “Tidy up again” turns them off once "
                + "more, and leaves everything you use alone."));
        return rows;
    }

    public static void openWifi(Context context) {
        openFirst(context, new Intent(Settings.ACTION_WIFI_SETTINGS),
                component("com.android.tv.settings",
                        "com.android.tv.settings.connectivity.NetworkActivity"));
    }

    public static void openBluetooth(Context context) {
        openFirst(context, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                component("com.android.tv.settings",
                        "com.android.tv.settings.accessories.AddAccessoryActivity"));
    }

    public static void openDisplay(Context context) {
        openFirst(context, new Intent(Settings.ACTION_DISPLAY_SETTINGS),
                component("com.android.tv.settings",
                        "com.android.tv.settings.device.displaysound.DisplaySoundActivity"));
    }

    public static void openSound(Context context) {
        openFirst(context, new Intent(Settings.ACTION_SOUND_SETTINGS),
                component("com.android.tv.settings",
                        "com.android.tv.settings.device.displaysound.DisplaySoundActivity"));
    }

    /** The TV's keyboard picker: which on-screen keyboard the system uses. */
    public static void openKeyboard(Context context) {
        openFirst(context,
                component("com.android.tv.settings",
                        "com.android.tv.settings.inputmethod.KeyboardActivity"),
                new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS));
    }

    /** The TV's date and time screen. */
    public static void openDateTime(Context context) {
        openFirst(context,
                component("com.android.tv.settings",
                        "com.android.tv.settings.system.DateTimeActivity"),
                new Intent(Settings.ACTION_DATE_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS));
    }

    /** The TV's language and input screen. */
    public static void openLanguage(Context context) {
        openFirst(context,
                component("com.android.tv.settings",
                        "com.android.tv.settings.system.LanguageActivity"),
                new Intent(Settings.ACTION_LOCALE_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS));
    }

    /**
     * Opens the first of these screens that the TV actually has.
     *
     * A TV does not have to answer the standard settings actions. This one
     * has no display settings page and no Bluetooth settings action, but it
     * does have those screens under other names. So every row tries the
     * action first and then the screens it knows about, and says so plainly
     * when the TV has neither.
     */
    private static void openFirst(Context context, Intent... intents) {
        for (Intent intent : intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            } catch (RuntimeException ignored) {
                // This TV does not have that one. Try the next.
            }
        }
        Ui.toast(context, "This TV does not have that screen");
    }

    /** A screen of the TV's own settings, named directly. */
    private static Intent component(String packageName, String className) {
        return new Intent().setComponent(new ComponentName(packageName, className));
    }

    /**
     * Opens the TV's own system update screen.
     *
     * On a Google TV that is the panel in Play services; on other TVs it is
     * the manufacturer's updater. When the TV has neither, the About screen
     * is where the update lives.
     */
    public static void openUpdate(Context context) {
        try {
            context.startActivity(new Intent("android.settings.SYSTEM_UPDATE_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (RuntimeException ignored) {
            // This TV has no update panel of its own; fall through.
        }
        openFirst(context, new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
    }

    /**
     * The state of the TV's network, so the row says something useful.
     *
     * The name of the network is not shown: from Android 10 a normal app is
     * not allowed to read it unless it knows where the device is, and a
     * launcher should not ask for that.
     */
    public static String wifiName(Context context) {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) {
                return "";
            }
            if (!wifi.isWifiEnabled()) {
                return "Off";
            }
            ConnectivityManager network = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (network == null) {
                return "";
            }
            NetworkInfo info = network.getActiveNetworkInfo();
            // Connected over Ethernet is not a Wi-Fi state; the row names
            // what it is, not what the device happens to be plugged into.
            if (info != null && info.isConnected()
                    && info.getType() == ConnectivityManager.TYPE_WIFI) {
                return "Connected";
            }
            return "Not connected";
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** True while a tidy-up run is going; a second tap must not start another. */
    private static volatile boolean tidyRunning;

    /**
     * Turns off everything that a system update put back.
     *
     * The work happens off the main thread: there are twenty packages to ask
     * about and to change, and the screen must not stutter while it happens.
     */
    public static void tidyUpNow(final ToolActivity host) {
        if (tidyRunning) {
            return;
        }
        tidyRunning = true;
        new Thread(() -> {
            try {
                final Cleanup.Result result = Cleanup.run(host);
                host.runOnUiThread(() -> {
                    String message = result.message();
                    if (!Platform.isDefaultLauncher(host)) {
                        message += ". Sonora is not the home screen any more";
                    }
                    Ui.toast(host, message);
                });
            } finally {
                tidyRunning = false;
            }
        }, "sonora-cleanup").start();
    }
}
