package com.mitun.sonora.tools;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.mitun.sonora.PackageActions;
import com.mitun.sonora.Prefs;
import com.mitun.sonora.ui.ChoiceOverlay;
import com.mitun.sonora.ui.ScreenStack;
import com.mitun.sonora.ui.Ui;

/**
 * The host for every panel: settings, the app manager, the debug panel, the
 * setup guide and the file manager.
 *
 * They all live in one {@link ScreenStack}, so they all navigate the same way
 * and BACK always means one step back. Nothing here runs in the background:
 * the activity and its views are the whole footprint.
 */
public final class ToolActivity extends Activity {
    /** "settings" (the default), "tools", "quick" or "wizard". */
    public static final String EXTRA_SCREEN = "screen";
    private static final int REQUEST_PICK_WALLPAPER = 41;

    private FrameLayout root;
    private ScreenStack stack;
    private Prefs prefs;
    private View choiceOverlay;
    /** Set while the first-run setup is on screen. */
    private SetupWizard wizard;
    /** The package an uninstall was asked for, so the result can be reported. */
    private String pendingUninstall;
    private String pendingUninstallLabel;

    public static void open(Context context, String screen) {
        Intent intent = new Intent(context, ToolActivity.class);
        intent.putExtra(EXTRA_SCREEN, screen);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        Ui.immersive(this);

        root = new FrameLayout(this);
        stack = new ScreenStack(this, new ScreenStack.Listener() {
            @Override
            public void onEmpty() {
                finish();
            }

            @Override
            public void onPushed() {
            }
        });
        root.addView(stack, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        String screen = getIntent().getStringExtra(EXTRA_SCREEN);
        if ("wizard".equals(screen)) {
            wizard = new SetupWizard(this);
            wizard.start();
        } else if ("tools".equals(screen)) {
            ToolsPanel.show(this);
        } else if ("quick".equals(screen)) {
            QuickPanel.show(this);
        } else {
            SettingsPanel.show(this);
        }
    }

    public ScreenStack stack() {
        return stack;
    }

    public Prefs prefs() {
        return prefs;
    }

    /** Opens a tvOS-style picker over the current screen. */
    public void choose(String title, String[] options, int selected, ChoiceOverlay.Callback callback) {
        dismissChoice();
        ChoiceOverlay overlay = new ChoiceOverlay(this, title, options, selected, index -> {
            dismissChoice();
            if (callback != null) {
                callback.onPicked(index);
            }
        });
        choiceOverlay = overlay;
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.focusSelected();
    }

    public void dismissChoice() {
        if (choiceOverlay == null) {
            return;
        }
        final View overlay = choiceOverlay;
        choiceOverlay = null;
        if (overlay instanceof ChoiceOverlay) {
            ((ChoiceOverlay) overlay).dismiss(() -> root.removeView(overlay));
        } else {
            root.removeView(overlay);
        }
    }

    /** Asks the system for a picture to use as the wallpaper. */
    public void pickWallpaperPhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_PICK_WALLPAPER);
        } catch (RuntimeException e) {
            Ui.toast(this, "No file picker available");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_WALLPAPER && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                }
                prefs.setWallpaper(-1, uri.toString());
                if (wizard != null) {
                    wizard.refreshCurrent();
                } else {
                    SettingsPanel.refresh(this);
                }
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (choiceOverlay != null) {
                    dismissChoice();
                    return true;
                }
                // Popping the last screen finishes the activity through the
                // stack listener, so BACK always ends up at the home screen.
                stack.pop();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * Asks the system to uninstall an app, and reports what happened.
     *
     * The system's uninstall screen does not tell the app what it decided,
     * so the only honest way to know is to look at whether the package is
     * still there when the app comes back.
     */
    public void uninstallApp(String packageName, String label) {
        PackageActions.Result result = PackageActions.requestUninstall(this, packageName, label);
        Ui.toast(this, result.message);
        if (result.ok) {
            pendingUninstall = packageName;
            pendingUninstallLabel = label;
        }
    }

    private void reportUninstall() {
        if (pendingUninstall == null) {
            return;
        }
        String packageName = pendingUninstall;
        String label = pendingUninstallLabel;
        pendingUninstall = null;
        pendingUninstallLabel = null;
        if (isInstalled(packageName)) {
            Ui.toast(this, label + " was not uninstalled");
        } else {
            Ui.toast(this, "Uninstalled " + label);
        }
    }

    private boolean isInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        reportUninstall();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Ui.immersive(this);
            // A screen that was built while the activity was starting could not
            // take the focus, so the window getting it is the moment to ask
            // again. Without this the D-pad has nowhere to go.
            stack.focusTop();
        }
    }
}
