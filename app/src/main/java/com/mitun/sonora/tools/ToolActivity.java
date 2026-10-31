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
    /** "settings" (the default) or "tools". */
    public static final String EXTRA_SCREEN = "screen";
    private static final int REQUEST_PICK_WALLPAPER = 41;

    private FrameLayout root;
    private ScreenStack stack;
    private Prefs prefs;
    private View choiceOverlay;

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
        if ("tools".equals(screen)) {
            ToolsPanel.show(this);
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
        if (choiceOverlay != null) {
            root.removeView(choiceOverlay);
            choiceOverlay = null;
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
                SettingsPanel.refresh(this);
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

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Ui.immersive(this);
        }
    }
}
