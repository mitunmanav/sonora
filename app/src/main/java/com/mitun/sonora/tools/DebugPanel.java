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
import com.mitun.sonora.ui.Ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * The device panel: what this TV actually is and what it is doing right now.
 *
 * Everything here is a cheap platform query except the log tail, which runs
 * once, on a thread, only when the user asks for it.
 */
public final class DebugPanel {
    private static final int LOG_LINES = 60;

    private DebugPanel() {
    }

    public static void show(ToolActivity host) {
        SystemInfo info = SystemInfo.get(host);
        List<SettingRow> rows = new ArrayList<>();

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
        rows.add(SettingRow.action("Refresh", () -> show(host)));

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
        rows.add(SettingRow.push("Recent log", "the last " + LOG_LINES + " lines",
                () -> showLog(host)));
        rows.add(SettingRow.push("Self check", "is this device set up well",
                () -> showSelfCheck(host)));

        host.stack().show("Device", "What this TV is and what it is doing", rows);
    }

    private static void showProcesses(ToolActivity host) {
        List<SettingRow> rows = new ArrayList<>();
        List<ActivityManager.RunningAppProcessInfo> processes = new ArrayList<>();
        ActivityManager manager = (ActivityManager)
                host.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            processes.addAll(manager.getRunningAppProcesses());
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
                total += info.getTotalPrivateDirty();
            }
        }
        rows.add(SettingRow.header("Running"));
        rows.add(SettingRow.value("Processes", String.valueOf(processes.size())));
        rows.add(SettingRow.value("Private memory in use", SystemInfo.bytes(total)));
        for (int i = 0; i < processes.size(); i++) {
            long bytes = i < memory.length && memory[i] != null
                    ? memory[i].getTotalPrivateDirty() : 0;
            rows.add(SettingRow.value(processes.get(i).processName, SystemInfo.bytes(bytes)));
        }
        host.stack().push("Processes", "Everything running right now", rows);
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
        rows.add(SettingRow.header("Battery"));
        if (level >= 0 && scale > 0) {
            rows.add(SettingRow.value("Charge", Math.round(level * 100f / scale) + "%"));
        }
        rows.add(SettingRow.value("Status", status));
        rows.add(SettingRow.value("Power", plugged == 0 ? "On battery" : "Plugged in"));
        rows.add(SettingRow.value("Low power mode", isPowerSaver(host) ? "On" : "Off"));
        host.stack().push("Battery", "Charge and power", rows);
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
        host.stack().push("Network", "Addresses and connectivity", rows);
    }

    private static String wifiName(Context context) {
        try {
            android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager)
                    context.getSystemService(Context.WIFI_SERVICE);
            if (wifi == null || !wifi.isWifiEnabled()) {
                return "Off";
            }
            return wifi.getConnectionInfo().getSSID().replace("\"", "");
        } catch (RuntimeException e) {
            return "Unknown";
        }
    }

    /** The tail of the system log. Runs once, off the main thread, on request. */
    private static void showLog(ToolActivity host) {
        final android.widget.TextView text = new android.widget.TextView(host);
        text.setText("Reading…");
        text.setTextColor(0xFFF5F6F8);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        text.setTextSize(11);
        text.setPadding(0, 0, 0, 0);
        text.setTextIsSelectable(true);
        android.widget.ScrollView scroller = new android.widget.ScrollView(host);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.addView(text, new android.widget.ScrollView.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        host.stack().pushContent("Recent log", "The last " + LOG_LINES + " lines", scroller);

        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            try {
                Process process = new ProcessBuilder("logcat", "-d", "-v", "time",
                        "-t", String.valueOf(LOG_LINES)).redirectErrorStream(true).start();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                }
                process.waitFor();
            } catch (Exception e) {
                sb.append("Could not read the log: ").append(e.getMessage());
            }
            final String result = sb.toString();
            host.runOnUiThread(() -> text.setText(result));
        }, "sonora-logcat").start();
    }

    private static void showSelfCheck(ToolActivity host) {
        SystemInfo info = SystemInfo.get(host);
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.header("Self check"));
        rows.add(SettingRow.value("Default launcher",
                Ui.isDefaultLauncher(host) ? "Yes" : "No — set it in Settings"));
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
        rows.add(SettingRow.value("Screen saver",
                host.prefs().screensaverDelaySec() > 0 ? "On" : "Off"));
        host.stack().push("Self check", "How this device is set up", rows);
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
