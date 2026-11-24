package com.mitun.sonora.tools;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.mitun.sonora.AppManager;
import com.mitun.sonora.PackageActions;
import com.mitun.sonora.Prefs;
import com.mitun.sonora.Scenes;
import com.mitun.sonora.ui.BackdropView;
import com.mitun.sonora.ui.ChoiceOverlay;
import com.mitun.sonora.ui.Glyph;
import com.mitun.sonora.ui.Motion;
import com.mitun.sonora.ui.ScreenStack;
import com.mitun.sonora.ui.Theme;
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
    /** "settings" (the default), "tools", "quick", "apps" or "wizard". */
    public static final String EXTRA_SCREEN = "screen";
    private static final int REQUEST_PICK_WALLPAPER = 41;

    private static final String EXTRA_PAGE = "page";

    private FrameLayout root;
    private ImageView mark;
    private Glyph markGlyph;
    private int markKind;
    private ScreenStack stack;
    private Prefs prefs;
    private View choiceOverlay;
    /** The row that had the focus before a picker took it. */
    private View stackFocus;
    /** The stack depth at that moment, so a pushed screen keeps its focus. */
    private int stackDepthAtChoice;
    /** Set while the first-run setup is on screen. */
    private SetupWizard wizard;
    /** The package an uninstall was asked for, so the result can be reported. */
    private String pendingUninstall;
    private String pendingUninstallLabel;
    /** When the ask was made; an answer cannot come back faster than this. */
    private long uninstallAskedAt;

    public static void open(Context context, String screen) {
        Intent intent = new Intent(context, ToolActivity.class);
        intent.putExtra(EXTRA_SCREEN, screen);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        Theme.load(this);
        Ui.immersive(this);

        root = new FrameLayout(this);
        BackdropView backdrop = new BackdropView(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadBackdrop(backdrop);

        // The settings pages are transparent on the left, so the mark stays
        // still while their lists slide over it, and changes with the page.
        mark = new ImageView(this);
        markGlyph = new Glyph(Glyph.GEAR);
        markKind = Glyph.GEAR;
        markGlyph.setInk(markInk());
        mark.setImageDrawable(markGlyph);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int markSize = Math.round(screenWidth * 0.24f);
        FrameLayout.LayoutParams markParams = new FrameLayout.LayoutParams(markSize, markSize,
                Gravity.START | Gravity.TOP);
        markParams.setMarginStart(Math.round(screenWidth * 0.155f));
        markParams.topMargin = Math.round(getResources().getDisplayMetrics().heightPixels * 0.25f);
        root.addView(mark, markParams);

        stack = new ScreenStack(this, new ScreenStack.Listener() {
            @Override
            public void onEmpty() {
                // Leaving the first-run setup with BACK is still having seen
                // it; otherwise it would come back at every launch.
                if (wizard != null) {
                    prefs.setWelcomeSeen(true);
                }
                finish();
            }

            @Override
            public void onPushed() {
            }

            @Override
            public void onTopChanged(String title) {
                showMark(markFor(title));
            }
        });
        root.addView(stack, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        // The backdrop paints every pixel, so the window has no need to first.
        getWindow().setBackgroundDrawable(null);

        String screen = getIntent().getStringExtra(EXTRA_SCREEN);
        if ("wizard".equals(screen)) {
            wizard = new SetupWizard(this);
            wizard.start();
        } else if ("quick".equals(screen)) {
            QuickPanel.show(this);
        } else if ("apps".equals(screen)) {
            AppPanel.show(this);
        } else {
            SettingsPanel.show(this);
            String page = getIntent().getStringExtra(EXTRA_PAGE);
            if (page != null) {
                SettingsPanel.openPage(this, page);
            }
        }
    }

    /**
     * Builds this screen again on one page of the settings, after a choice that
     * changes how everything in it is drawn.
     */
    void reopenAt(String page) {
        // After the picker has let go of the focus and its callback has returned.
        root.post(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            setIntent(getIntent().putExtra(EXTRA_PAGE, page));
            recreate();
        });
    }

    /**
     * Hands the backdrop its blur when the launcher has not made one already.
     * The view only checks the shared blur itself, so a miss is decoded here,
     * off the main thread, and handed over when it lands.
     */
    private void loadBackdrop(final BackdropView backdrop) {
        final int index = prefs.wallpaper();
        final String uri = prefs.wallpaperUri();
        final String key = BackdropView.key(index, uri);
        Bitmap ready = BackdropView.remembered(key);
        if (ready != null) {
            backdrop.setSoftened(ready);
            return;
        }
        new Thread(() -> {
            Bitmap soft = null;
            try {
                Bitmap decoded = null;
                if (index < 0 && uri != null) {
                    decoded = Ui.decodeUri(getResources(), getContentResolver(),
                            Uri.parse(uri), 96);
                }
                if (decoded == null) {
                    decoded = Ui.decodeScene(getResources(),
                            Scenes.ALL.get(Scenes.safeIndex(index)).res, 96, 54);
                }
                soft = BackdropView.soften(decoded);
                if (decoded != null) {
                    decoded.recycle();
                }
            } catch (RuntimeException | OutOfMemoryError ignored) {
            }
            if (soft == null) {
                return;
            }
            BackdropView.remember(key, soft);
            final Bitmap blur = soft;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    backdrop.setSoftened(blur);
                }
            });
        }, "sonora-backdrop").start();
    }

    private static int markInk() {
        return Ui.alpha(Theme.ink(), 0.82f);
    }

    /** The mark beside a page, as tvOS gives each settings page its own picture. */
    private static int markFor(String title) {
        switch (title == null ? "" : title) {
            case "Quick settings":
                return Glyph.SLIDERS;
            case "Home Screen":
                return Glyph.HOME;
            case "Clock":
                return Glyph.CLOCK;
            case "Screen Saver":
                return Glyph.SAVER;
            case "Apps":
                return Glyph.APPS;
            case "Tools":
                return Glyph.TOOLS;
            case "System":
                return Glyph.INFO;
            case "Favourites":
                return Glyph.STAR;
            default:
                return Glyph.GEAR;
        }
    }

    private void showMark(int kind) {
        if (kind == markKind) {
            return;
        }
        markKind = kind;
        markGlyph = new Glyph(kind);
        markGlyph.setInk(markInk());
        // A large mark drawn in a light line, the weight tvOS gives a big symbol.
        markGlyph.setWeight(1.1f);
        mark.setImageDrawable(markGlyph);
        mark.setAlpha(0f);
        Motion.spring(mark, Motion.ALPHA, 1f, Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS);
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
        // The picker takes the focus, so the row it came from has to be
        // remembered: cancelling must not leave the remote with nothing to
        // move to.
        stackFocus = stack.findFocus();
        stackDepthAtChoice = stack.depth();
        // A screen that is sliding out still holds the focus for a moment.
        // A row that is not on the screen on top must not get the focus
        // back later: by the time the picker closes it is gone.
        View topScreen = stackTopView(stack);
        if (stackFocus != null && (topScreen == null || !inside(stackFocus, topScreen))) {
            stackFocus = null;
        }
        ChoiceOverlay overlay = new ChoiceOverlay(this, title, options, selected, index -> {
            // A second OK during the fade is already ignored inside the
            // overlay: its rows stop answering once dismiss() runs.
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
            ((ChoiceOverlay) overlay).dismiss(() -> {
                root.removeView(overlay);
                if (choiceOverlay == null) {
                    // A newer picker already owns the focus if there is one.
                    restoreStackFocus();
                }
            });
        } else {
            root.removeView(overlay);
            restoreStackFocus();
        }
    }

    /**
     * Gives the list back the focus the picker took from it.
     *
     * Removing a focused overlay makes Android focus-search the screen behind
     * it, which can land on a different row. The row that opened the picker
     * is still there after a cancel, so it takes the focus back. A choice that
     * opens a screen changes the stack depth; that screen keeps its own focus.
     */
    private void restoreStackFocus() {
        View target = stackFocus;
        stackFocus = null;
        boolean sameScreen = stack.depth() == stackDepthAtChoice;
        stackDepthAtChoice = -1;
        View topScreen = stackTopView(stack);
        if (sameScreen && target != null && target.isFocusable() && target.isAttachedToWindow()
                && topScreen != null && inside(target, topScreen)) {
            target.requestFocus();
        } else if (stack.findFocus() == null) {
            // The screen was rebuilt while the picker was open.
            stack.focusTop();
        }
    }

    /**
     * The view of the screen on top of the stack, or null while a screen is
     * still sliding out - its view stays a child until the move is done.
     */
    static View stackTopView(ScreenStack stack) {
        int depth = stack.depth();
        if (depth == 0 || stack.getChildCount() != depth) {
            return null;
        }
        return stack.getChildAt(depth - 1);
    }

    /** True when the view sits inside the screen's view tree. */
    private static boolean inside(View view, View screen) {
        for (android.view.ViewParent parent = view.getParent(); parent != null;
             parent = parent.getParent()) {
            if (parent == screen) {
                return true;
            }
        }
        return false;
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
                } catch (SecurityException e) {
                    Ui.toast(this, "The photo may not survive a restart");
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
            uninstallAskedAt = SystemClock.elapsedRealtime();
        }
    }

    private void reportUninstall() {
        if (pendingUninstall == null) {
            return;
        }
        // A resume that comes straight after the ask cannot be its answer:
        // the confirmation needs a pause to arrive, so the dialog may still
        // be up and "not uninstalled" would be a guess. The report waits
        // for the next resume, where it asks the package manager again.
        if (SystemClock.elapsedRealtime() - uninstallAskedAt < 2000) {
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // The relaunch is singleTask: the screen it asks for comes from the
        // new intent, so the instance starts over the way a fresh open would.
        setIntent(intent);
        recreate();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The manifest says this activity takes configuration changes itself,
        // and the screens bake screen metrics in when they are built, so a
        // resize or a density change starts the screen over.
        recreate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        reportUninstall();
        Motion.refresh(this);
        // A system screen can change what a row says - the network, the
        // screen saver, the home-screen role - so the settings are rebuilt
        // on the way back. Screens with content of their own (the app list,
        // the log, the aerials) are left alone. A screen under an open picker
        // is left alone too: rebuilding it would yank the rows the picker is
        // talking about.
        if (choiceOverlay == null) {
            if (wizard != null) {
                // The setup is not a settings page, but its steps track the
                // same things, like the home-screen role.
                wizard.refreshCurrent();
            } else {
                View top = stack.topContent();
                if (top instanceof SettingsHomeView || SettingsPanel.isPage(this)) {
                    SettingsPanel.refresh(this);
                }
                DebloatPanel.resumed(this);
                SetupGuide.resumed(this);
                SettingsPanel.resumedFavourites(this);
            }
        }
        // An uninstall confirmed on a system dialog leaves the app list stale.
        AppPanel.resumed(this);
    }

    @Override
    protected void onDestroy() {
        // The app manager's icons are the biggest thing this activity leaves
        // behind; the package list itself stays cached for its 30 seconds.
        AppManager.trimIcons();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Ui.immersive(this);
            // A screen that was built while the activity was starting could not
            // take the focus, so the window getting it is the moment to ask
            // again. Without this the D-pad has nowhere to go. A picker keeps
            // its own focus, never the list behind it.
            if (choiceOverlay == null) {
                stack.focusTop();
            }
        }
    }
}
