package com.mitun.sonora.tools;

import android.content.Intent;

import com.mitun.files.FilesActivity;

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
                            startFiles(host);
                        }
                    });
            return;
        }
        startFiles(host);
    }

    /** The same file manager as the standalone Files app, bundled in this one. */
    private static void startFiles(ToolActivity host) {
        host.startActivity(new Intent(host, FilesActivity.class)
                .putExtra(FilesActivity.EXTRA_OWN_FOLDER, FileServer.appFolder(host).getAbsolutePath())
                .putExtra(FilesActivity.EXTRA_OWN_LABEL, "Sonora")
                .putExtra(FilesActivity.EXTRA_LIGHT, host.prefs().lightAppearance())
                .putExtra(FilesActivity.EXTRA_SHARE_CLASS, ShareActivity.class.getName())
                .putExtra(FilesActivity.EXTRA_SHARE_LABEL, "Share over the Network")
                .putExtra(FilesActivity.EXTRA_SHARE_PATH, ShareActivity.EXTRA_PATH)
                .putExtra(FilesActivity.EXTRA_SHARE_NAME, ShareActivity.EXTRA_NAME));
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
