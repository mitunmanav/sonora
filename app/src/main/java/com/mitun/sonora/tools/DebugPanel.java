package com.mitun.sonora.tools;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import com.mitun.sonora.SystemInfo;
import com.mitun.sonora.ui.SettingRow;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import com.mitun.sonora.Platform;

/**
 * The device panel: what this TV actually is and what it is doing right now.
 *
 * Everything here is a cheap platform query except the log tail, which runs
 * once, on a thread, only when the user asks for it.
 */
public final class DebugPanel {
    private static final int LOG_LINES = 60;

    /** The tags the device and processes screens carry on the stack. */
    private static final Object TAG_DEVICE = new Object();
    private static final Object TAG_PROCESSES = new Object();
    private static final Object TAG_BATTERY = new Object();
    private static final Object TAG_NETWORK = new Object();
    private static final Object TAG_LOG = new Object();
    private static final Object TAG_SELF_CHECK = new Object();

    private DebugPanel() {
    }

    public static void show(ToolActivity host) {
        SystemInfo.invalidate();
        host.stack().pushSettings(TAG_DEVICE, "Device", rows(host));
    }

    /** Rebuilds the device screen with the numbers as they are now. */
    private static void refresh(ToolActivity host) {
        SystemInfo.invalidate();
        host.stack().refreshSettings(TAG_DEVICE, "Device", rows(host));
    }

    private static List<SettingRow> rows(ToolActivity host) {
        SystemInfo info = SystemInfo.get(host);
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.note("What this TV is and what it is doing."));

        rows.add(SettingRow.header("Device"));
        rows.add(SettingRow.value("Model", info.manufacturer + " " + info.model));
        rows.add(SettingRow.value("Android", info.androidVersion + " (API " + info.sdk + ")"));
        rows.add(SettingRow.value("Build", Build.DISPLAY));
        rows.add(SettingRow.value("Screen", info.widthDp + " × " + info.heightDp + " dp"));
        rows.add(SettingRow.value("Density", info.densityDpi + " dpi"));
        rows.add(SettingRow.value("Memory class", info.memoryClassMb + " MB"
                + (info.lowRam ? " · low RAM device" : "")));

        rows.add(SettingRow.header("Memory"));
        rows.add(SettingRow.value("App heap in use", SystemInfo.bytes(SystemInfo.usedHeap())));
        rows.add(SettingRow.value("App heap limit", SystemInfo.bytes(info.javaHeapLimit)));
        rows.add(SettingRow.value("Native heap", SystemInfo.bytes(SystemInfo.nativeHeap())));
        rows.add(SettingRow.value("System memory", SystemInfo.bytes(info.availableMemory)
                + " free of " + SystemInfo.bytes(info.totalMemory)));
        rows.add(SettingRow.action("Refresh", () -> refresh(host)));

        rows.add(SettingRow.header("Storage"));
        rows.add(SettingRow.value("Internal storage", storage(host, Environment.getDataDirectory())));
        if (info.externalStorageMounted) {
            rows.add(SettingRow.value("External storage",
                    storage(host, Environment.getExternalStorageDirectory())));
        } else {
            rows.add(SettingRow.value("External storage", "Not mounted"));
        }

        rows.add(SettingRow.header("More"));
        rows.add(SettingRow.push("Running processes", "what is using the memory",
                () -> showProcesses(host)));
        rows.add(SettingRow.push("Battery", "charge and power state", () -> showBattery(host)));
        rows.add(SettingRow.push("Network", "addresses and connectivity",
                () -> showNetwork(host)));
        rows.add(SettingRow.push("Sonora's log", "the last " + LOG_LINES + " lines",
                () -> showLog(host)));
        rows.add(SettingRow.push("Self check", "is this device set up well",
                () -> showSelfCheck(host)));
        return rows;
    }

    private static void showProcesses(ToolActivity host) {
        final String title = "Processes";
        List<SettingRow> reading = new ArrayList<>();
        reading.add(SettingRow.note("Everything running right now."));
        reading.add(SettingRow.value("Reading", "…"));
        host.stack().pushSettings(TAG_PROCESSES, title, reading);
        // Asking the system for every process is a binder call each time. On a
        // slow stick that is long enough to be an ANR, so it happens off the
        // UI thread and the screen fills in when it is done.
        new Thread(() -> {
            final List<SettingRow> rows = processRows(host);
            host.runOnUiThread(() -> {
                if (host.isFinishing() || host.isDestroyed()) {
                    return;
                }
                if (host.stack().topTag() != TAG_PROCESSES) {
                    // The user pressed BACK while the list was being read, so
                    // the screen on top is another one. Rebuilding it with
                    // these rows would replace the wrong screen.
                    return;
                }
                host.stack().refreshSettings(TAG_PROCESSES, title, rows);
            });
        }, "sonora-processes").start();
    }

    private static List<SettingRow> processRows(ToolActivity host) {
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.note("Everything running right now."));
        List<ActivityManager.RunningAppProcessInfo> processes = new ArrayList<>();
        ActivityManager manager = (ActivityManager)
                host.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            try {
                List<ActivityManager.RunningAppProcessInfo> running =
                        manager.getRunningAppProcesses();
                // It can be null when there is nothing to report.
                if (running != null) {
                    processes.addAll(running);
                }
            } catch (RuntimeException ignored) {
            }
        }
        int[] pids = new int[processes.size()];
        for (int i = 0; i < processes.size(); i++) {
            pids[i] = processes.get(i).pid;
        }
        android.os.Debug.MemoryInfo[] memory = new android.os.Debug.MemoryInfo[0];
        try {
            if (manager != null && pids.length > 0) {
                memory = manager.getProcessMemoryInfo(pids);
            }
        } catch (RuntimeException ignored) {
        }
        long total = 0;
        for (android.os.Debug.MemoryInfo info : memory) {
            if (info != null) {
                // getTotalPrivateDirty answers in kB, not bytes.
                total += info.getTotalPrivateDirty() * 1024L;
            }
        }
        rows.add(SettingRow.header("Running"));
        rows.add(SettingRow.value("Processes", String.valueOf(processes.size())));
        rows.add(SettingRow.value("Private memory in use", SystemInfo.bytes(total)));
        for (int i = 0; i < processes.size(); i++) {
            long bytes = i < memory.length && memory[i] != null
                    ? memory[i].getTotalPrivateDirty() * 1024L : 0;
            String name = processes.get(i).processName;
            rows.add(SettingRow.value(name == null ? "Unnamed" : name,
                    SystemInfo.bytes(bytes)));
        }
        return rows;
    }

    private static void showBattery(ToolActivity host) {
        int level = -1;
        int scale = -1;
        int plugged = -1;
        String status = "Unknown";
        try {
            Intent intent = host.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent != null) {
                level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                status = batteryStatus(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1));
            }
        } catch (RuntimeException ignored) {
        }
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.note("Charge and power."));
        rows.add(SettingRow.header("Battery"));
        if (level >= 0 && scale > 0) {
            rows.add(SettingRow.value("Charge", Math.round(level * 100f / scale) + "%"));
        }
        rows.add(SettingRow.value("Status", status));
        rows.add(SettingRow.value("Power", plugged < 0 ? "Unknown"
                : plugged == 0 ? "On battery" : "Plugged in"));
        rows.add(SettingRow.value("Low power mode", isPowerSaver(host) ? "On" : "Off"));
        host.stack().pushSettings(TAG_BATTERY, "Battery", rows);
    }

    private static String batteryStatus(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "Not charging";
            default:
                return "Unknown";
        }
    }

    private static boolean isPowerSaver(Context context) {
        try {
            android.os.PowerManager power = (android.os.PowerManager)
                    context.getSystemService(Context.POWER_SERVICE);
            return power != null && power.isPowerSaveMode();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void showNetwork(ToolActivity host) {
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.note("Addresses and connectivity."));
        rows.add(SettingRow.header("Network"));
        String ip = SetupGuide.ipAddress();
        rows.add(SettingRow.value("IP address", ip.length() == 0 ? "Not connected" : ip));
        rows.add(SettingRow.value("Wi-Fi", wifiName(host)));
        rows.add(SettingRow.value("Developer options",
                SetupGuide.developerOptionsOn(host) ? "On" : "Off"));
        rows.add(SettingRow.value("USB debugging",
                SetupGuide.usbDebuggingOn(host) ? "On" : "Off"));
        if (Build.VERSION.SDK_INT >= 30) {
            rows.add(SettingRow.value("Wireless debugging",
                    SetupGuide.wirelessDebuggingOn(host) ? "On" : "Off"));
        }
        host.stack().pushSettings(TAG_NETWORK, "Network", rows);
    }

    private static String wifiName(Context context) {
        try {
            android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager)
                    context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null || !wifi.isWifiEnabled()) {
                return "Off";
            }
            android.net.wifi.WifiInfo connection = wifi.getConnectionInfo();
            if (connection == null) {
                return "Unknown";
            }
            String ssid = connection.getSSID();
            // getSSID answers "<unknown ssid>" when it cannot name the
            // network; that is still a connection, just an unnamed one.
            if (ssid == null || ssid.startsWith("<unknown")) {
                return "Connected";
            }
            return ssid.replace("\"", "");
        } catch (RuntimeException e) {
            return "Unknown";
        }
    }

    /** The tail of the system log. Runs once, off the main thread, on request. */
    private static void showLog(ToolActivity host) {
        final android.widget.TextView text = new android.widget.TextView(host);
        text.setText("Reading…");
        text.setTextColor(com.mitun.sonora.ui.Theme.ink());
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        text.setTextSize(11);
        text.setPadding(0, 0, 0, 0);
        text.setTextIsSelectable(true);
        android.widget.ScrollView scroller = new android.widget.ScrollView(host);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.addView(text, new android.widget.ScrollView.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        host.stack().pushContent(TAG_LOG, "Sonora's log", "The last " + LOG_LINES + " lines", scroller);

        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            try {
                final Process process = new ProcessBuilder("logcat", "-d", "-v", "time",
                        "-t", String.valueOf(LOG_LINES)).redirectErrorStream(true).start();
                // A wedged logd keeps readLine blocked and waitFor can only
                // bound a process that has ended; destroying it breaks the
                // read, so the watchdog does that after the deadline.
                Thread watchdog = new Thread(() -> {
                    try {
                        Thread.sleep(4000);
                        process.destroy();
                    } catch (InterruptedException ignored) {
                    }
                }, "sonora-logcat-watchdog");
                watchdog.setDaemon(true);
                watchdog.start();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                }
                watchdog.interrupt();
                waitFor(process);
            } catch (Exception e) {
                sb.append("Could not read the log: ").append(e.getMessage());
            }
            final String result = sb.toString();
            host.runOnUiThread(() -> text.setText(result));
        }, "sonora-logcat").start();
    }

    /**
     * Waits for a process, but not forever: waitFor(time, unit) needs API 26
     * and a hung logcat must not keep this thread.
     */
    private static void waitFor(Process process) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            try {
                process.exitValue();
                return;
            } catch (IllegalThreadStateException e) {
                Thread.sleep(40);
            }
        }
        process.destroy();
    }

    private static void showSelfCheck(ToolActivity host) {
        SystemInfo info = SystemInfo.get(host);
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.note("How this device is set up."));
        rows.add(SettingRow.header("Self check"));
        rows.add(SettingRow.value("Default launcher",
                Platform.isDefaultLauncher(host) ? "Yes" : "No. Set it in Settings"));
        rows.add(SettingRow.value("Developer options",
                SetupGuide.developerOptionsOn(host) ? "On" : "Off"));
        rows.add(SettingRow.value("Network",
                SetupGuide.ipAddress().length() == 0 ? "Not connected" : "Connected"));
        rows.add(SettingRow.value("External storage",
                info.externalStorageMounted ? "Mounted" : "Not mounted"));
        long heapUsed = SystemInfo.usedHeap();
        rows.add(SettingRow.value("App heap",
                SystemInfo.bytes(heapUsed) + " of " + SystemInfo.bytes(info.javaHeapLimit)));
        rows.add(SettingRow.value("Low RAM device", info.lowRam ? "Yes" : "No"));
        rows.add(SettingRow.value("Screen saver", screenSaverState(host)));
        host.stack().pushSettings(TAG_SELF_CHECK, "Self check", rows);
    }

    /**
     * The screen saver is really running when Sonora is the one the TV shows.
     * The in-app saver is on its own timer, so the two are reported apart.
     */
    private static String screenSaverState(ToolActivity host) {
        if (host.prefs().screensaverDelaySec() <= 0) {
            return "Off";
        }
        switch (Platform.dreamState(host)) {
            case SELECTED:
                return "On, and set as the TV's screen saver";
            case STALE:
                return "On in Sonora. The TV's screen saver points to an app that is not installed";
            default:
                return "On in Sonora, not set as the TV's screen saver";
        }
    }

    private static String storage(Context context, java.io.File path) {
        try {
            StatFs stat = new StatFs(path.getAbsolutePath());
            return SystemInfo.bytes(stat.getAvailableBytes()) + " free of "
                    + SystemInfo.bytes(stat.getTotalBytes());
        } catch (RuntimeException e) {
            return "Unknown";
        }
    }
}
