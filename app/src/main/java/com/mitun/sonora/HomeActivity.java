package com.mitun.sonora;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mitun.sonora.ui.AppTileView;
import com.mitun.sonora.ui.ChoiceOverlay;
import com.mitun.sonora.ui.GlassDrawable;
import com.mitun.sonora.ui.GlassPillView;
import com.mitun.sonora.ui.HeroBackgroundView;
import com.mitun.sonora.ui.Motion;
import com.mitun.sonora.ui.ScreensaverView;
import com.mitun.sonora.ui.Type;
import com.mitun.sonora.ui.Ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Sonora: a tvOS-style home screen for Android TV.
 *
 * The whole thing is plain platform views on purpose - no AndroidX, no Compose,
 * no networking - so it stays a few hundred KB and draws with the GPU only.
 */
public class HomeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private AppEntry[] appEntries = new AppEntry[0];
    private final List<AppTileView> tiles = new ArrayList<>();

    private FrameLayout root;
    private HeroBackgroundView hero;
    private GlassPillView clockPill;
    private TextView shelfTitle;
    private TextView shelfSubtitle;
    private LinearLayout grid;
    private ScrollView scroller;
    private FrameLayout overlayHost;
    private View choiceOverlay;
    private ScreensaverView screensaver;

    private long lastInteraction;
    private boolean suppressIdle;
    private boolean initialFocusDone;

    /** The decoded wallpaper, kept so a resume does not re-decode a full JPEG. */
    private Bitmap wallpaperBitmap;
    private String wallpaperKey;
    private Bitmap settingsIcon;
    private TextView emptyState;

    /** The app that had the focus, so a rebuild can put the focus back on it. */
    private String focusedPackage;
    /** A fingerprint of the settings the grid was built from. */
    private String gridKey = "";

    private final Runnable idleWatcher = new Runnable() {
        @Override
        public void run() {
            int delay = prefs.screensaverDelaySec();
            if (delay <= 0) {
                // The screen saver is off, so there is nothing to watch for.
                return;
            }
            if (!suppressIdle && screensaver == null
                    && System.currentTimeMillis() - lastInteraction > delay * 1000L) {
                showScreensaver();
                return;
            }
            // Sleep until it is actually due. Waking up every second to find
            // out that it is not is exactly the kind of thing a launcher on a
            // TV must not do.
            long due = lastInteraction + delay * 1000L;
            handler.postDelayed(this, Math.max(1000L, due - System.currentTimeMillis()));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        Ui.immersive(this);
        buildLayout();
        handler.post(idleWatcher);
        // Seeded here so the first resume does not read the grid as changed
        // and rebuild it a second time.
        gridKey = prefs.gridKey();
        loadApps();
    }

    private void buildLayout() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.NIGHT);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        Grid.Metrics metrics = gridMetrics();
        int margin = Ui.dp(this, metrics.marginDp);

        hero = new HeroBackgroundView(this);
        root.addView(hero, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Top shelf: the focused app's title, plus the clock pill on the right.
        FrameLayout shelf = new FrameLayout(this);
        shelf.setClipChildren(false);
        shelf.setPadding(margin, Ui.dp(this, 34), margin, 0);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        shelfTitle = new TextView(this);
        shelfTitle.setTextColor(Color.WHITE);
        Type.apply(shelfTitle, Type.TITLE);
        titles.addView(shelfTitle);
        shelfSubtitle = new TextView(this);
        shelfSubtitle.setTextColor(0xB3FFFFFF);
        Type.apply(shelfSubtitle, Type.CAPTION);
        shelfSubtitle.setPadding(0, Ui.dp(this, 6), 0, 0);
        titles.addView(shelfSubtitle);
        shelf.addView(titles, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));

        clockPill = new GlassPillView(this, prefs);
        clockPill.setId(View.generateViewId());
        clockPill.setOnOpenSettings(this::openSettings);
        FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        shelf.addView(clockPill, pillParams);

        // The icon grid is anchored to the bottom of the screen, the way tvOS
        // floats it over the artwork. It only scrolls when the list outgrows
        // the space above it.
        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(margin, Ui.dp(this, 12), margin, Ui.dp(this, 26));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        scroller.addView(grid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams scrollerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(scroller, scrollerParams);

        root.addView(shelf, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        overlayHost = new FrameLayout(this);
        root.addView(overlayHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    private void loadApps() {
        final int tileWidthPx = Ui.dp(this, gridMetrics().tileWidthDp);
        new Thread(() -> {
            List<AppEntry> result;
            try {
                result = AppRepository.get(this, tileWidthPx);
            } catch (RuntimeException | OutOfMemoryError e) {
                result = new ArrayList<>();
            }
            final List<AppEntry> loaded = result;
            final List<AppEntry> ordered = AppOrdering.order(loaded, prefs.order(), prefs.hidden());
            runOnUiThread(() -> {
                appEntries = ordered.toArray(new AppEntry[0]);
                buildGrid();
            });
        }, "sonora-apps").start();
    }

    private void buildGrid() {
        grid.removeAllViews();
        tiles.clear();

        Grid.Metrics metrics = gridMetrics();
        int columns = metrics.columns;
        int tileWidth = Ui.dp(this, metrics.tileWidthDp);
        int tileHeight = Ui.dp(this, metrics.tileHeightDp);
        int gap = Ui.dp(this, metrics.gapDp);
        boolean labels = prefs.labels() != Prefs.LABELS_OFF;
        int labelHeight = labels ? Ui.dp(this, 22) : 0;

        AppEntry[] all = new AppEntry[appEntries.length + 1];
        System.arraycopy(appEntries, 0, all, 0, appEntries.length);
        all[appEntries.length] = new AppEntry("Settings", getPackageName(), null,
                settingsIcon(), null, 0xFF4A505C, true);

        if (appEntries.length == 0) {
            grid.addView(emptyStateView(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        for (int start = 0; start < all.length; start += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            int end = Math.min(all.length, start + columns);
            for (int i = start; i < end; i++) {
                AppTileView tile = new AppTileView(this, tileHeight, labelHeight, prefs);
                tile.setId(View.generateViewId());
                tile.setEntry(all[i]);
                tile.setFocusListener(this::onTileFocused);
                tile.setOnClickListener(v -> launch((AppTileView) v));
                tile.setOnLongClickListener(v -> {
                    showAppMenu((AppTileView) v);
                    return true;
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(tileWidth,
                        tileHeight + labelHeight);
                params.rightMargin = gap;
                row.addView(tile, params);
                tiles.add(tile);
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, tileHeight + labelHeight);
            rowParams.bottomMargin = Ui.dp(this, 16);

            if (start == 0) {
                // The first row sits in a translucent glass bar, the way tvOS 26
                // floats the app row over the artwork.
                FrameLayout bar = new FrameLayout(this);
                bar.setBackground(new GlassDrawable(this, 26f, 1f));
                bar.setClipChildren(false);
                bar.setClipToPadding(false);
                int barPad = Ui.dp(this, 12);
                bar.setPadding(barPad, barPad, barPad, barPad);
                bar.addView(row, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                barParams.leftMargin = -barPad;
                barParams.rightMargin = -barPad;
                barParams.bottomMargin = Ui.dp(this, 22) - barPad;
                grid.addView(bar, barParams);
            } else {
                grid.addView(row, rowParams);
            }
        }
        if (!tiles.isEmpty()) {
            // The grid is not laid out yet, so the focus has to wait for it.
            // Asking now leaves the focus on whichever tile the framework
            // picks instead of the first one.
            restoreFocus();
        }
        // The grid now matches these settings, so a later resume that finds
        // the same key knows there is nothing to rebuild.
        gridKey = prefs.gridKey();
        updateShelf(appEntries.length > 0 ? appEntries[0] : null);
    }

    /**
     * Puts the focus back where it was.
     *
     * A rebuilt grid is a new set of views, so the old focus is gone. The app
     * that had it gets it again; if it is no longer there, the first tile does.
     */
    private void restoreFocus() {
        if (tiles.isEmpty()) {
            return;
        }
        AppTileView target = null;
        if (focusedPackage != null) {
            for (AppTileView tile : tiles) {
                AppEntry entry = tile.entry();
                if (entry != null && focusedPackage.equals(entry.packageName)) {
                    target = tile;
                    break;
                }
            }
        }
        if (target == null) {
            target = tiles.get(0);
        }
        final AppTileView focus = target;
        focus.post(() -> {
            // isFocused, not hasWindowFocus: the window having focus says
            // nothing about which tile in it is focused.
            if (!isFinishing() && !focus.isFocused()) {
                focus.requestFocus();
            }
        });
    }

    /** The gear for the Settings tile, drawn once and reused. */
    private Bitmap settingsIcon() {
        if (settingsIcon == null || settingsIcon.isRecycled()) {
            settingsIcon = Ui.gear(this, Ui.dp(this, 192), true);
        }
        return settingsIcon;
    }

    /** A real empty state, so a device with no apps shows a reason and a hint. */
    private View emptyStateView() {
        if (emptyState == null) {
            emptyState = new TextView(this);
            emptyState.setTextColor(Color.WHITE);
            Type.apply(emptyState, Type.BODY);
            emptyState.setGravity(Gravity.CENTER);
            emptyState.setPadding(Ui.dp(this, 30), Ui.dp(this, 24), Ui.dp(this, 30), Ui.dp(this, 24));
        }
        emptyState.setText(getString(R.string.no_apps));
        return emptyState;
    }

    /** Grid metrics for the real screen, so a 720p stick and a 4K panel match. */
    private Grid.Metrics gridMetrics() {
        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        return Grid.resolve(widthDp, prefs.columns());
    }

    private void onTileFocused(AppTileView tile, AppEntry entry) {
        if (entry == null) {
            return;
        }
        hero.setEntry(entry);
        updateShelf(entry);
        focusedPackage = entry.packageName;
        View focused = getCurrentFocus();
        View previous = lastFocused;
        if (previous != null && previous != focused) {
            float dx = 0f;
            float dy = 0f;
            if (focused != null) {
                int[] a = new int[2];
                int[] b = new int[2];
                previous.getLocationOnScreen(a);
                focused.getLocationOnScreen(b);
                dx = Math.signum(b[0] - a[0]);
                dy = Math.signum(b[1] - a[1]);
            }
            // The card that just took the focus leans into the direction of
            // travel and settles back, the way a tvOS card does.
            if (focused instanceof AppTileView) {
                ((AppTileView) focused).lean(dx, dy);
            }
        }
        lastFocused = focused;
    }

    /** Puts the focus on the first app, once, when the screen is ready for it. */
    private void focusFirstTile() {
        if (initialFocusDone || tiles.isEmpty()) {
            return;
        }
        View focused = getCurrentFocus();
        if (focused instanceof AppTileView) {
            // The framework already picked a tile; leave it alone.
            initialFocusDone = true;
            return;
        }
        initialFocusDone = true;
        tiles.get(0).requestFocus();
    }

    private View lastFocused;

    private void updateShelf(AppEntry entry) {
        if (entry == null) {
            shelfTitle.setText("Sonora");
            shelfSubtitle.setText("No apps yet — open Settings to add some");
            return;
        }
        shelfTitle.setText(entry.label);
        shelfSubtitle.setText(entry.settings ? "Press OK to open Sonora settings"
                : "Hold OK for options");
    }

    private void launch(AppTileView tile) {
        AppEntry entry = tile.entry();
        if (entry == null) {
            return;
        }
        if (entry.settings) {
            openSettings();
            return;
        }
        if (!entry.launch(this)) {
            Ui.toast(this, "Could not open " + entry.label);
        }
    }

    private void showAppMenu(AppTileView tile) {
        AppEntry entry = tile.entry();
        if (entry == null || entry.settings) {
            return;
        }
        suppressIdle = true;
        String[] options = {"Open " + entry.label, "Move to front", "Move to end",
                "Hide from Home", "App info"};
        ChoiceOverlay overlay = new ChoiceOverlay(this, entry.label, options, -1, index -> {
            dismissChoice();
            switch (index) {
                case 0:
                    entry.launch(this);
                    break;
                case 1:
                    move(entry, 0);
                    break;
                case 2:
                    move(entry, appEntries.length - 1);
                    break;
                case 3:
                    hide(entry);
                    break;
                case 4:
                    try {
                        Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + entry.packageName));
                        info.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(info);
                    } catch (ActivityNotFoundException e) {
                        Ui.toast(this, "No app info screen");
                    }
                    break;
                default:
                    break;
            }
            suppressIdle = false;
        });
        showOverlay(overlay);
        overlay.focusSelected();
    }

    private void move(AppEntry entry, int index) {
        List<AppEntry> list = new ArrayList<>(Arrays.asList(appEntries));
        list.remove(entry);
        index = Math.max(0, Math.min(list.size(), index));
        list.add(index, entry);
        appEntries = list.toArray(new AppEntry[0]);
        prefs.setOrder(AppOrdering.saveOrder(list));
        buildGrid();
    }

    private void hide(AppEntry entry) {
        Set<String> hidden = prefs.hidden();
        hidden.add(entry.packageName);
        prefs.setHidden(hidden);
        loadApps();
        Ui.toast(this, entry.label + " hidden");
    }

    private void openSettings() {
        // The settings and the tools live in their own screen stack, so they
        // navigate the same way as everything else.
        try {
            startActivity(new Intent(this, com.mitun.sonora.tools.ToolActivity.class)
                    .putExtra(com.mitun.sonora.tools.ToolActivity.EXTRA_SCREEN, "settings"));
        } catch (RuntimeException e) {
            Ui.toast(this, "Could not open the settings");
        }
    }

    private void showOverlay(View overlay) {
        if (choiceOverlay != null) {
            overlayHost.removeView(choiceOverlay);
        }
        choiceOverlay = overlay;
        overlayHost.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void dismissChoice() {
        if (choiceOverlay != null) {
            overlayHost.removeView(choiceOverlay);
            choiceOverlay = null;
            // The overlay had the focus, and removing it leaves nothing
            // focused, so the tile the user came from gets it back.
            restoreFocus();
        }
        suppressIdle = false;
    }

    private void applyWallpaper() {
        int index = prefs.wallpaper();
        String uri = prefs.wallpaperUri();
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        String key = (index < 0 ? "u:" + uri : "s:" + index) + "@" + width + "x" + height;
        if (key.equals(wallpaperKey) && wallpaperBitmap != null && !wallpaperBitmap.isRecycled()) {
            hero.setWallpaper(wallpaperBitmap);
            return;
        }
        // Decoding a full-screen JPEG takes long enough to drop frames, so it
        // happens off the main thread and the result is applied back on it.
        final String wanted = key;
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                if (index < 0 && uri != null) {
                    // A photo the user picked; fall back to a bundled scene if
                    // the permission was refused or the file has gone.
                    bitmap = Ui.decodeUri(getResources(), getContentResolver(),
                            Uri.parse(uri), width);
                }
                if (bitmap == null) {
                    bitmap = Ui.decodeScene(getResources(), Scenes.ALL.get(
                            Scenes.safeIndex(index)).res, width, height);
                }
            } catch (RuntimeException | OutOfMemoryError ignored) {
                bitmap = null;
            }
            final Bitmap decoded = bitmap;
            runOnUiThread(() -> {
                // A newer wallpaper was asked for while this one was decoding:
                // this one is stale and its memory is better given back.
                if (!wanted.equals(wallpaperKey)) {
                    if (decoded != null) {
                        decoded.recycle();
                    }
                    return;
                }
                if (isFinishing() || isDestroyed()) {
                    if (decoded != null) {
                        decoded.recycle();
                    }
                    return;
                }
                wallpaperBitmap = decoded;
                wallpaperKey = wanted;
                hero.setWallpaper(wallpaperBitmap);
            });
        }, "sonora-wallpaper").start();
    }

    private void showScreensaver() {
        if (screensaver != null) {
            return;
        }
        screensaver = new ScreensaverView(this);
        screensaver.setSceneDurationMs(prefs.screensaverSceneSec());
        screensaver.setShowOverlay(prefs.screensaverClock() == 1);
        root.addView(screensaver, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        screensaver.setFocusable(true);
        screensaver.requestFocus();
        screensaver.start();
        screensaver.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                screensaver.previousScene();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                screensaver.nextScene();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_BACK) {
                hideScreensaver();
                return true;
            }
            return false;
        });
    }

    private void hideScreensaver() {
        if (screensaver == null) {
            return;
        }
        screensaver.stop();
        root.removeView(screensaver);
        screensaver = null;
        lastInteraction = System.currentTimeMillis();
        restoreFocus();
        // The watcher stops when the screen saver comes up, so it has to
        // start again now that it is gone.
        handler.removeCallbacks(idleWatcher);
        handler.post(idleWatcher);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            lastInteraction = System.currentTimeMillis();
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // BACK is a breadcrumb, never a toggle and never a dialog.
            if (event.getAction() == KeyEvent.ACTION_UP && handleBack()) {
                return true;
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // The D-pad is driven here rather than by the framework's spatial
            // search: on a grid that search jumps to whatever happens to be
            // nearest on screen, which is not what a row of apps means.
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    // Only the grid is driven from here. An overlay or the
                    // screen saver moves its own focus, and it can only do
                    // that if the key reaches it.
                    if (moveFocus(event.getKeyCode())) {
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        // Everything else must reach the focused view: swallowing it here is
        // what kills the D-pad inside the overlays.
        return super.dispatchKeyEvent(event);
    }

    /**
     * Moves the focus the way tvOS does: along the row, then between the
     * rows, with the clock above the first one.
     *
     * @return true when the grid handled the key. False means something else
     *         has the focus and the key belongs to it.
     */
    private boolean moveFocus(int key) {
        View focused = getCurrentFocus();
        if (focused instanceof AppTileView) {
            int index = tiles.indexOf(focused);
            if (index < 0) {
                return false;
            }
            int columns = gridMetrics().columns;
            int row = index / columns;
            int column = index % columns;
            int target = -1;
            switch (key) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    target = column > 0 ? index - 1 : -1;
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    target = (column + 1 < columns && index + 1 < tiles.size()) ? index + 1 : -1;
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    target = row > 0 ? index - columns : -1;
                    break;
                default:
                    target = (index + columns < tiles.size()) ? index + columns : -1;
                    break;
            }
            if (target >= 0) {
                tiles.get(target).requestFocus();
            } else if (key == KeyEvent.KEYCODE_DPAD_UP && row == 0) {
                clockPill.requestFocus();
            }
            return true;
        }
        if (focused == clockPill) {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && !tiles.isEmpty()) {
                tiles.get(0).requestFocus();
            }
            return true;
        }
        return false;
    }

    /** Handles BACK. Returns true when something consumed it. */
    private boolean handleBack() {
        if (screensaver != null) {
            hideScreensaver();
            return true;
        }
        if (choiceOverlay != null) {
            dismissChoice();
            return true;
        }
        int focusedIndex = -1;
        View focused = getCurrentFocus();
        if (focused instanceof AppTileView) {
            focusedIndex = tiles.indexOf(focused);
        }
        if (BackPolicy.atTopLevel(scroller.getScrollY(), focusedIndex,
                Ui.isDefaultLauncher(this)) == BackPolicy.SCROLL_TO_TOP) {
            scrollGridToTop();
            return true;
        }
        // Nothing left to go back to, and Sonora is not the home screen, so
        // hand the user back to the home screen the TV really uses.
        goHome();
        return true;
    }

    /**
     * Leaves for the TV's own home screen.
     *
     * Finishing would drop the user back into whatever they were using before
     * they opened Sonora, which on a TV is the last app they watched. The
     * documented rule is that BACK always ends at the home screen.
     */
    private void goHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            startActivity(home);
        } catch (RuntimeException ignored) {
            finish();
        }
    }

    /**
     * BACK on the app grid returns to the first row and the first tile. It
     * never exits the launcher.
     */
    private void scrollGridToTop() {
        scroller.scrollTo(0, 0);
        if (!tiles.isEmpty()) {
            tiles.get(0).requestFocus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        lastInteraction = System.currentTimeMillis();
        // The settings live in another activity, so a change made there is
        // only seen when the launcher comes back. The grid is rebuilt when
        // something that changes it has changed; the wallpaper has its own
        // key, so it is checked every time.
        if (gridChanged()) {
            loadApps();
        }
        applyWallpaper();
        clockPill.refresh();
        Motion.refresh(this);
        if (screensaver != null) {
            hideScreensaver();
        } else {
            handler.removeCallbacks(idleWatcher);
            handler.post(idleWatcher);
        }
    }

    /**
     * True when a setting that changes the app grid has changed.
     *
     * The columns, the labels, the order and the hidden apps are all read
     * when the grid is built, so a change to any of them means the grid has
     * to be built again.
     */
    private boolean gridChanged() {
        String key = prefs.gridKey();
        if (key.equals(gridKey)) {
            return false;
        }
        gridKey = key;
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The grid is sized from the live screen, so a resize or a density
        // change has to rebuild it. The artwork is cached at the size of the
        // old tile, so the cache goes too.
        AppRepository.invalidate();
        loadApps();
        // The wallpaper is sized for the screen, so it goes as well.
        wallpaperBitmap = null;
        wallpaperKey = null;
        hero.release();
        applyWallpaper();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // The wallpaper is a screen-sized bitmap; drop it and decode it
            // again when the UI comes back.
            wallpaperBitmap = null;
            wallpaperKey = null;
            hero.release();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (screensaver != null) {
            hideScreensaver();
        }
        // Nothing runs while the launcher is in the background.
        handler.removeCallbacks(idleWatcher);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Ui.immersive(this);
            focusFirstTile();
        }
    }
}
