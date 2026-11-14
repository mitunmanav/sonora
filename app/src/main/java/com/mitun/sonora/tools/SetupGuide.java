package com.mitun.sonora.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;

import com.mitun.sonora.SystemInfo;
import com.mitun.sonora.ui.SettingRow;
import com.mitun.sonora.ui.Ui;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * The guided debugging setup.
 *
 * Every step reads the live state of the device, so a step is only ever shown
 * as done when it really is. Each step opens the exact Settings screen it
 * needs, and the adb commands can be copied straight to the clipboard.
 */
public final class SetupGuide {
    private SetupGuide() {
    }

    public static void show(ToolActivity host) {
        // It is pushed, not shown: the screen the user came from stays behind
        // it, so BACK goes back there instead of out of the activity.
        host.stack().pushSettings("Setup guide", "Setup guide", rows(host));
    }

    /**
     * Rebuilds the steps when the screen is back on top: debugging can be
     * turned on or off while the guide sits under a system screen.
     */
    static void resumed(ToolActivity host) {
        if ("Setup guide".equals(host.stack().topTag())) {
            host.stack().refreshSettings("Setup guide", "Setup guide", rows(host));
        }
    }

    private static List<SettingRow> rows(ToolActivity host) {
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.note("Turn on debugging, then connect from your computer."));
        boolean developerOptions = developerOptionsOn(host);
        boolean usbDebugging = usbDebuggingOn(host);
        boolean wireless = wirelessDebuggingOn(host);
        String ip = ipAddress();

        rows.add(SettingRow.header("Step 1 · Developer options"));
        rows.add(SettingRow.value("Developer options", developerOptions ? "On" : "Off"));
        if (!developerOptions) {
            rows.add(SettingRow.action("Open Developer options", () -> open(host,
                    android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        }

        rows.add(SettingRow.header("Step 2 · USB debugging"));
        rows.add(SettingRow.value("USB debugging", usbDebugging ? "On" : "Off"));
        rows.add(SettingRow.action("Open Developer options", () -> open(host,
                android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));

        if (Build.VERSION.SDK_INT >= 30) {
            rows.add(SettingRow.header("Step 3 · Wireless debugging"));
            rows.add(SettingRow.value("Wireless debugging", wireless ? "On" : "Off"));
            rows.add(SettingRow.action("Open wireless debugging", () -> open(host,
                    "android.settings.WIRELESS_DEBUGGING_SETTINGS")));
        }

        rows.add(SettingRow.header("Connect"));
        rows.add(SettingRow.value("This TV's IP address", ip.length() == 0 ? "Not connected" : ip));
        if (Build.VERSION.SDK_INT >= 30) {
            // Wireless debugging listens on a port of its own and has to be
            // paired first. The number is on the wireless debugging screen.
            rows.add(SettingRow.note("Wireless debugging uses the port and the code shown "
                    + "on the TV's wireless debugging screen."));
            rows.add(SettingRow.action("Copy the adb pair command",
                    () -> copy(host, "adb pair " + (ip.length() == 0 ? "<this TV's IP>" : ip)
                            + ":<port shown on the TV>")));
            rows.add(SettingRow.action("Copy the adb connect command",
                    () -> copy(host, "adb connect " + (ip.length() == 0 ? "<this TV's IP>" : ip)
                            + ":<port shown on the TV>")));
        } else {
            rows.add(SettingRow.action("Copy the adb connect command",
                    () -> copy(host, "adb connect "
                            + (ip.length() == 0 ? "<this TV's IP>" : ip) + ":5555")));
        }
        rows.add(SettingRow.action("Copy the adb devices command",
                () -> copy(host, "adb devices -l")));
        rows.add(SettingRow.action("Copy this TV's details", () -> copy(host, details(host))));

        rows.add(SettingRow.header("Check"));
        rows.add(SettingRow.action("Copy the logcat command",
                () -> copy(host, "adb logcat -d -v time | tail -200")));
        rows.add(SettingRow.action("Copy the memory dump command",
                () -> copy(host, "adb shell dumpsys meminfo " + host.getPackageName())));
        rows.add(SettingRow.value("Note",
                "Whether your computer is allowed to talk to this TV can only be "
                        + "checked with \"adb devices\" on the computer."));

        rows.add(SettingRow.header("Help"));
        rows.add(SettingRow.push("Device information", "memory, storage, battery",
                () -> DebugPanel.show(host)));
        return rows;
    }

    /** Reads the live developer-options state. Never guesses it is on. */
    public static boolean developerOptionsOn(Context context) {
        return globalInt(context, "development_settings_enabled") == 1;
    }

    public static boolean usbDebuggingOn(Context context) {
        return globalInt(context, "adb_enabled") == 1;
    }

    public static boolean wirelessDebuggingOn(Context context) {
        if (Build.VERSION.SDK_INT < 30) {
            return false;
        }
        return globalInt(context, "adb_wifi_enabled") == 1;
    }

    private static int globalInt(Context context, String name) {
        try {
            return android.provider.Settings.Global.getInt(
                    context.getContentResolver(), name, 0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static void open(ToolActivity host, String action) {
        try {
            android.content.Intent intent = new android.content.Intent(action);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            host.startActivity(intent);
        } catch (RuntimeException e) {
            Ui.toast(host, "This device has no screen for that");
        }
    }

    private static void copy(ToolActivity host, String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager)
                    host.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                Ui.toast(host, "No clipboard on this device");
                return;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("Sonora", text));
            // Android 13 shows its own clipboard preview; a toast would
            // say the same thing twice.
            if (Build.VERSION.SDK_INT < 33) {
                Ui.toast(host, "Copied");
            }
        } catch (RuntimeException e) {
            Ui.toast(host, "Could not copy");
        }
    }

    private static String details(ToolActivity host) {
        SystemInfo info = SystemInfo.get(host);
        return info.manufacturer + " " + info.model
                + "\nAndroid " + info.androidVersion + " (API " + info.sdk + ")"
                + "\nBuild " + Build.DISPLAY
                + "\n" + info.widthDp + "x" + info.heightDp + "dp at " + info.densityDpi + "dpi";
    }

    /** The TV's address on the local network, or an empty string. */
    public static String ipAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return "";
            }
            String fallback = "";
            String fallback6 = "";
            for (NetworkInterface network : Collections.list(interfaces)) {
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                // A VPN or a tethered phone has an address too, but a computer
                // on the same network can only reach the Wi-Fi or wired one.
                boolean lan = network.getName().startsWith("wlan")
                        || network.getName().startsWith("eth");
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    // A link-local address is only good on its own segment;
                    // adb on the computer cannot reach the TV through it.
                    if (address.isLoopbackAddress() || address.isLinkLocalAddress()) {
                        continue;
                    }
                    if (address instanceof Inet4Address) {
                        if (lan) {
                            return address.getHostAddress();
                        }
                        if (fallback.isEmpty()) {
                            fallback = address.getHostAddress();
                        }
                    } else if (fallback6.isEmpty()) {
                        // IPv6 in an address takes brackets, like a URL does.
                        String v6 = address.getHostAddress();
                        int scope = v6.indexOf('%');
                        if (scope >= 0) {
                            // A scope id ("%wlan0") is for this stack, not an address bar.
                            v6 = v6.substring(0, scope);
                        }
                        fallback6 = "[" + v6 + "]";
                    }
                }
            }
            return fallback.isEmpty() ? fallback6 : fallback;
        } catch (Exception ignored) {
        }
        return "";
    }
}
