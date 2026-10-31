package com.mitun.sonora;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import com.mitun.sonora.ui.ScreensaverView;
import com.mitun.sonora.ui.ChoiceOverlay;
import com.mitun.sonora.ui.GlassDrawable;
import com.mitun.sonora.ui.GlassPillView;
import com.mitun.sonora.ui.HeroBackgroundView;
import com.mitun.sonora.ui.SettingsOverlay;
import com.mitun.sonora.ui.Ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sonora: a tvOS-style home screen for Android TV.
 *
 * The whole thing is plain platform views on purpose - no AndroidX, no Compose,
 * no networking - so it stays a few hundred KB and draws with the GPU only.
 */
public class HomeActivity extends Activity {
    private static final int REQUEST_PICK_WALLPAPER = 41;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private AppEntry[] appEntries = new AppEntry[0];
    private final List<AppTileView> tiles = new ArrayList<>();
    private final List<LinearLayout> rows = new ArrayList<>();

    private FrameLayout root;
    private HeroBackgroundView hero;
    private GlassPillView clockPill;
    private TextView shelfTitle;
    private TextView shelfSubtitle;
    private LinearLayout grid;
    private ScrollView scroller;
    private View topShelf;
    private SettingsOverlay settingsOverlay;
    private FrameLayout overlayHost;
    private View choiceOverlay;
    private ScreensaverView screensaver;

    private long lastInteraction;
    private boolean suppressIdle;

    /** The decoded wallpaper, kept so a resume does not re-decode a full JPEG. */
    private Bitmap wallpaperBitmap;
    private String wallpaperKey;
    private Bitmap settingsIcon;
    private TextView emptyState;
    /** Every launchable app, before the hidden ones are filtered out. */
    private List<AppEntry> allApps = new ArrayList<>();

    private final Runnable idleWatcher = new Runnable() {
        @Override
        public void run() {
            int delay = prefs.screensaverDelaySec();
            if (delay > 0 && !suppressIdle && screensaver == null
                    && System.currentTimeMillis() - lastInteraction > delay * 1000L) {
                showScreensaver();
                return;
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        Ui.immersive(this);
        buildLayout();
        handler.post(idleWatcher);
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
        shelfTitle.setTextSize(30);
        shelfTitle.setTypeface(Ui.light());
        shelfTitle.setLetterSpacing(-0.01f);
        titles.addView(shelfTitle);
        shelfSubtitle = new TextView(this);
        shelfSubtitle.setTextColor(0xB3FFFFFF);
        shelfSubtitle.setTextSize(14);
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
        topShelf = shelf;

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
                allApps = loaded;
                appEntries = ordered.toArray(new AppEntry[0]);
                buildGrid();
            });
        }, "sonora-apps").start();
    }

    private void buildGrid() {
        grid.removeAllViews();
        tiles.clear();
        rows.clear();

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
            rows.add(row);
        }
        wireFocus(columns);
        if (!tiles.isEmpty()) {
            tiles.get(0).requestFocus();
        }
        updateShelf(appEntries.length > 0 ? appEntries[0] : null);
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
            emptyState.setTextSize(20);
            emptyState.setTypeface(Ui.light());
            emptyState.setGravity(Gravity.CENTER);
            emptyState.setLineSpacing(Ui.dpf(this, 6), 1f);
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

    /** Explicit left/right/up/down so the D-pad behaves like tvOS, not like a list. */
    private void wireFocus(int columns) {
        for (int i = 0; i < tiles.size(); i++) {
            AppTileView tile = tiles.get(i);
            int rowIndex = i / columns;
            int columnIndex = i % columns;
            int left = i - 1;
            int right = i + 1;
            if (left >= 0) {
                tile.setNextFocusLeftId(tiles.get(left).getId());
            }
            if (right < tiles.size()) {
                tile.setNextFocusRightId(tiles.get(right).getId());
            }
            if (rowIndex > 0) {
                int above = i - columns;
                tile.setNextFocusUpId(tiles.get(above).getId());
            } else {
                tile.setNextFocusUpId(clockPill.getId());
            }
            if (rowIndex + 1 < rows.size()) {
                int below = i + columns;
                if (below < tiles.size()) {
                    tile.setNextFocusDownId(tiles.get(below).getId());
                }
            }
        }
    }

    private void onTileFocused(AppTileView tile, AppEntry entry) {
        if (entry == null) {
            return;
        }
        hero.setEntry(entry);
        updateShelf(entry);
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
            if (previous instanceof AppTileView) {
                ((AppTileView) previous).sway(dx, dy);
            }
        }
        lastFocused = focused;
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
        if (settingsOverlay == null) {
            settingsOverlay = new SettingsOverlay(this, prefs, new SettingsOverlay.Callbacks() {
                @Override
                public void onSettingChanged(String key) {
                    handleSetting(key);
                }

                @Override
                public void onAction(String key) {
                    handleSetting(key);
                }

                @Override
                public void onClose() {
                }
            });
            root.addView(settingsOverlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        settingsOverlay.rebuild();
        settingsOverlay.setVisibility(View.VISIBLE);
        suppressIdle = true;
        settingsOverlay.focusFirst();
    }

    private void closeSettings() {
        if (settingsOverlay != null) {
            settingsOverlay.setVisibility(View.GONE);
        }
        suppressIdle = false;
        if (!tiles.isEmpty()) {
            tiles.get(0).requestFocus();
        }
    }

    private void handleSetting(String key) {
        switch (key) {
            case "wallpaper":
                pickWallpaper();
                break;
            case "columns":
                pickColumns();
                break;
            case "labels":
                pickLabels();
                break;
            case "clock24":
                prefs.setClock24(!prefs.clock24());
                clockPill.refresh();
                settingsOverlay.rebuild();
                break;
            case "show_date":
                prefs.setShowDate(!prefs.showDate());
                clockPill.updateDateVisibility();
                settingsOverlay.rebuild();
                break;
            case "show_seconds":
                prefs.setShowSeconds(!prefs.showSeconds());
                clockPill.refresh();
                settingsOverlay.rebuild();
                break;
            case "ss_delay":
                pickScreensaverDelay();
                break;
            case "ss_scene":
                pickSceneDuration();
                break;
            case "ss_clock":
                pickScreensaverClock();
                break;
            case "hidden":
                pickHiddenApps();
                break;
            case "reset_order":
                prefs.setOrder("");
                loadApps();
                settingsOverlay.rebuild();
                break;
            case "refresh":
                AppRepository.invalidate();
                loadApps();
                Ui.toast(this, "Apps reloaded");
                break;
            case "set_default":
                Ui.openDefaultLauncherSettings(this);
                break;
            case "about":
                showAbout();
                break;
            default:
                break;
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
        }
        suppressIdle = false;
    }

    private void pickWallpaper() {
        String[] names = new String[Scenes.ALL.size() + 1];
        for (int i = 0; i < Scenes.ALL.size(); i++) {
            names[i] = Scenes.ALL.get(i).name;
        }
        names[names.length - 1] = "Choose a photo…";
        ChoiceOverlay overlay = new ChoiceOverlay(this, "Wallpaper", names, prefs.wallpaper(),
                index -> {
                    dismissChoice();
                    if (index == names.length - 1) {
                        pickWallpaperPhoto();
                    } else {
                        prefs.setWallpaper(index, null);
                        applyWallpaper();
                        settingsOverlay.rebuild();
                    }
                    suppressIdle = false;
                });
        showOverlay(overlay);
        overlay.focusSelected();
    }

    private void pickWallpaperPhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_PICK_WALLPAPER);
        } catch (ActivityNotFoundException e) {
            Ui.toast(this, "No file picker available");
        }
    }

    private void pickColumns() {
        String[] options = {"Auto", "4", "5", "6", "7", "8"};
        int current = prefs.columns() == 0 ? 0 : prefs.columns() - 3;
        ChoiceOverlay overlay = new ChoiceOverlay(this, "Apps per row", options, current, index -> {
            dismissChoice();
            prefs.setColumns(index == 0 ? 0 : index + 3);
            buildGrid();
            settingsOverlay.rebuild();
            suppressIdle = false;
        });
        showOverlay(overlay);
        overlay.focusSelected();
    }

    private void pickLabels() {
        String[] options = {"Hidden", "Focused only", "Always"};
        ChoiceOverlay overlay = new ChoiceOverlay(this, "App names", options, prefs.labels(),
                index -> {
                    dismissChoice();
                    prefs.setLabels(index);
                    buildGrid();
                    settingsOverlay.rebuild();
                    suppressIdle = false;
                });
        showOverlay(overlay);
        overlay.focusSelected();
    }

    private void pickScreensaverDelay() {
        String[] options = {"Never", "1 min", "3 min", "5 min", "10 min", "30 min"};
        int[] seconds = {0, 60, 180, 300, 600, 1800};
        int current = 0;
        for (int i = 0; i < seconds.length; i++) {
            if (seconds[i] == prefs.screensaverDelaySec()) {
                current = i;
            }
        }
        ChoiceOverlay overlay = new ChoiceOverlay(this, "Start after", options, current, index -> {
            dismissChoice();
            prefs.setScreensaverDelaySec(seconds[index]);
            settingsOverlay.rebuild();
            suppressIdle = false;
        });
        showOverlay(overlay);
        overlay.focusSelected();
    }

    private void pickSceneDuration() {
        String[] options = {"30 s", "45 s", "60 s", "90 s", "2 min", "5 min"};
        int[] seconds = {30, 45, 60, 90, 120, 300};
        int current = 0;
        for (int i = 0; i < seconds.length; i++) {
            if (seconds[i] == prefs.screensaverSceneSec()) {
                current = i;
            }
        }
        ChoiceOverlay overlay = new ChoiceOverlay(this, "Each scene", options, current, index -> {
            dismissChoice();
            prefs.setScreensaverSceneSec(seconds[index]);
            settingsOverlay.rebuild();
            suppressIdle = false;
        });
        showOverlay(overlay);
        overlay.focusSelected();
    }

    private void pickScreensaverClock() {
        String[] options = {"On tap", "Always", "Never"};
        ChoiceOverlay overlay = new ChoiceOverlay(this, "Clock in screen saver", options,
                prefs.screensaverClock(), index -> {
                    dismissChoice();
                    prefs.setScreensaverClock(index);
                    settingsOverlay.rebuild();
                    suppressIdle = false;
                });
        showOverlay(overlay);
        overlay.focusSelected();
    }

    private void pickHiddenApps() {
        Set<String> hidden = prefs.hidden();
        if (hidden.isEmpty()) {
            Ui.toast(this, "No hidden apps");
            return;
        }
        List<String> names = new ArrayList<>();
        List<String> packages = new ArrayList<>();
        // The hidden apps are gone from the grid, so they have to be looked up
        // in the full list rather than in what is on screen.
        for (AppEntry entry : allApps) {
            if (hidden.contains(entry.packageName)) {
                names.add(entry.label);
                packages.add(entry.packageName);
            }
        }
        if (names.isEmpty()) {
            Ui.toast(this, "No hidden apps");
            return;
        }
        String[] options = names.toArray(new String[0]);
        ChoiceOverlay overlay = new ChoiceOverlay(this, "Unhide app", options, -1, index -> {
            dismissChoice();
            Set<String> current = prefs.hidden();
            current.remove(packages.get(index));
            prefs.setHidden(current);
            loadApps();
            settingsOverlay.rebuild();
            suppressIdle = false;
        });
        showOverlay(overlay);
        overlay.focusSelected();
    }

    private void showAbout() {
        String text = getString(R.string.about_text);
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setLineSpacing(Ui.dpf(this, 5), 1f);
        view.setPadding(Ui.dp(this, 30), Ui.dp(this, 30), Ui.dp(this, 30), Ui.dp(this, 30));
        view.setBackground(Ui.rounded(0xE61C1F26, Ui.dpf(this, 18)));
        FrameLayout host = new FrameLayout(this);
        host.setBackgroundColor(0xE6000000);
        host.setPadding(Ui.dp(this, 90), Ui.dp(this, 70), Ui.dp(this, 90), Ui.dp(this, 70));
        host.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        showOverlay(host);
        host.setFocusable(true);
        host.requestFocus();
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
        Bitmap bitmap = null;
        if (index < 0 && uri != null) {
            // A photo the user picked; fall back to a bundled scene if the
            // permission was refused or the file has gone.
            bitmap = Ui.decodeUri(getResources(), getContentResolver(),
                    Uri.parse(uri), width);
        }
        if (bitmap == null) {
            bitmap = Ui.decodeScene(getResources(), Scenes.ALL.get(
                    Scenes.safeIndex(index)).res, width, height);
        }
        wallpaperBitmap = bitmap;
        wallpaperKey = key;
        hero.setWallpaper(bitmap);
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
        if (!tiles.isEmpty()) {
            tiles.get(0).requestFocus();
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
                applyWallpaper();
                if (settingsOverlay != null) {
                    settingsOverlay.rebuild();
                }
            }
        }
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
        // Everything else must reach the focused view: swallowing it here is
        // what kills the D-pad inside the overlays.
        return super.dispatchKeyEvent(event);
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
        if (settingsOverlay != null && settingsOverlay.getVisibility() == View.VISIBLE) {
            closeSettings();
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
        finish();
        return true;
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
        applyWallpaper();
        clockPill.refresh();
        if (screensaver != null) {
            hideScreensaver();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The grid is sized from the live screen, so a resize or a density
        // change has to rebuild it.
        buildGrid();
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
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Ui.immersive(this);
        }
    }
}
