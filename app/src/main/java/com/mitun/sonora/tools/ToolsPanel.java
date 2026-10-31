package com.mitun.sonora.tools;

import android.content.Context;

import com.mitun.sonora.browser.BrowserActivity;
import com.mitun.sonora.ui.SettingRow;

import java.util.ArrayList;
import java.util.List;

/**
 * The tools menu. The browser and the file manager are bundled but off until
 * the user turns them on, so nothing reaches the network and nothing listens
 * on a socket unless it has been asked for.
 */
public final class ToolsPanel {
    private ToolsPanel() {
    }

    public static void show(ToolActivity host) {
        List<SettingRow> rows = new ArrayList<>();
        rows.add(SettingRow.header("Tools"));
        rows.add(SettingRow.push("Apps", "disable, uninstall, inspect", () -> AppPanel.show(host)));
        rows.add(SettingRow.push("Device", "memory, storage, battery", () -> DebugPanel.show(host)));
        rows.add(SettingRow.push("Setup guide", "turn on debugging, step by step",
                () -> SetupGuide.show(host)));
        rows.add(SettingRow.push("Files", "browse and share", () -> openFiles(host)));
        rows.add(SettingRow.push("Browser", "with an ad blocker", () -> openBrowser(host)));
        host.stack().show("Tools", "Everything runs on this TV. Nothing runs in the background.", rows);
    }

    public static void openFiles(ToolActivity host) {
        if (!host.prefs().filesEnabled()) {
            host.choose("The file manager is off",
                    new String[]{"Turn it on", "Not now"}, 0, index -> {
                        if (index == 0) {
                            host.prefs().setFilesEnabled(true);
                            FilePanel.show(host);
                        }
                    });
            return;
        }
        FilePanel.show(host);
    }

    public static void openBrowser(ToolActivity host) {
        if (!host.prefs().browserEnabled()) {
            host.choose("The browser is off",
                    new String[]{"Turn it on", "Not now"}, 0, index -> {
                        if (index == 0) {
                            host.prefs().setBrowserEnabled(true);
                            BrowserActivity.start(host);
                        }
                    });
            return;
        }
        BrowserActivity.start(host);
    }
}
