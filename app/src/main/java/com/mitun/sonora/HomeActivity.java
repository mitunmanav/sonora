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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mitun.sonora.ui.AppTileView;
import com.mitun.sonora.ui.ChoiceOverlay;
import com.mitun.sonora.ui.GlassDrawable;
import com.mitun.sonora.ui.GlassIconPill;
import com.mitun.sonora.ui.GlassPillView;
import com.mitun.sonora.ui.HeroBackgroundView;
import com.mitun.sonora.ui.Motion;
import com.mitun.sonora.ui.ScreensaverView;
import com.mitun.sonora.ui.SearchOverlay;
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
    /** Padding inside the glass dock, around the row of icons. */
    private static final int BAR_PAD_DP = 12;
    /** The gap between two rows of icons. */
    private static final int ROW_GAP_DP = 22;
    /** How much of the next row shows under the dock, cut by the screen edge. */
    private static final int PEEK_DP = 20;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private AppEntry[] appEntries = new AppEntry[0];
    private final List<AppTileView> tiles = new ArrayList<>();
    /** Set when the artwork was dropped to free memory, so it is read again. */
    private boolean artworkDropped;
    /** The apps the user pinned, in the order they pinned them. */
    private final List<String> favourites = new ArrayList<>();

    private FrameLayout root;
    private HeroBackgroundView hero;
    private GlassPillView clockPill;
    private GlassIconPill quickPill;
    private GlassIconPill searchPill;
    private TextView shelfTitle;
    private TextView shelfSubtitle;
    private LinearLayout grid;
    private FrameLayout dockPort;
    private View dockBar;
    private FrameLayout.LayoutParams dockBarParams;
    private FrameLayout.LayoutParams portParams;
    private int marginPx;
    private int barPadPx;
    private int rowGapPx;
    private int peekPx;
    private int rowHeightPx;
    private FrameLayout overlayHost;
    private View choiceOverlay;
    private SearchOverlay searchOverlay;
    private ScreensaverView screensaver;

    /** True while the user is arranging the apps with the D-pad. */
    private boolean editing;

    /** True when the search was opened from the search button, not the remote. */
    private boolean searchFromPill;

    private long lastInteraction;
    private boolean suppressIdle;
    private boolean initialFocusDone;
    /** True while the search and quick-settings buttons are showing. */
    private boolean shelfToolsShown = true;

    /** The decoded wallpaper, kept so a resume does not re-decode a full JPEG. */
    private Bitmap wallpaperBitmap;
    /** The key of the wallpaper that is on screen. */
    private String wallpaperKey;
    /** The key of the newest request, so a decode that is overtaken can be dropped. */
    private String wallpaperRequest = "";
    private Bitmap settingsIcon;

    /** The app that had the focus, so a rebuild can put the focus back on it. */
    private String focusedPackage;
    /** A fingerprint of the settings the grid was built from. */
    private String gridKey = "";
    private long appsLoadGeneration;

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
        if (!prefs.welcomeSeen()) {
            // The setup runs once, on the first launch. It is marked as seen
            // before it opens, so a restart in the middle of it does not bring
            // it back, and BACK walks out of it at any point.
            prefs.setWelcomeSeen(true);
            openSetup();
        }
    }

    private void openSetup() {
        try {
            startActivity(new Intent(this, com.mitun.sonora.tools.ToolActivity.class)
                    .putExtra(com.mitun.sonora.tools.ToolActivity.EXTRA_SCREEN, "wizard"));
        } catch (RuntimeException e) {
            Ui.toast(this, "Could not open the setup");
        }
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
        LinearLayout shelf = new LinearLayout(this);
        shelf.setOrientation(LinearLayout.HORIZONTAL);
        shelf.setGravity(Gravity.CENTER_VERTICAL);
        shelf.setClipChildren(false);
        shelf.setPadding(margin, Ui.dp(this, 34), margin, 0);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        shelfTitle = new TextView(this);
        shelfTitle.setTextColor(Color.WHITE);
        Type.apply(shelfTitle, Type.TITLE);
        shelfTitle.setSingleLine(true);
        shelfTitle.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(shelfTitle);
        shelfSubtitle = new TextView(this);
        shelfSubtitle.setTextColor(0xB3FFFFFF);
        Type.apply(shelfSubtitle, Type.CAPTION);
        shelfSubtitle.setSingleLine(true);
        shelfSubtitle.setEllipsize(TextUtils.TruncateAt.END);
        shelfSubtitle.setPadding(0, Ui.dp(this, 6), 0, 0);
        titles.addView(shelfSubtitle);
        shelf.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        clockPill = new GlassPillView(this, prefs);
        clockPill.setId(View.generateViewId());
        clockPill.setOnOpenSettings(this::openSettings);
        clockPill.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                setShelfTools(true);
            }
        });

        // Search comes first in the row. This TV's remote has no search key
        // and its voice key is swallowed by the system, so without a button
        // of its own the search could never be reached.
        searchPill = new GlassIconPill(this, Ui.magnifier(this, Ui.dp(this, 22)),
                "Search apps");
        searchPill.setId(View.generateViewId());
        searchPill.setOnOpen(this::openSearch);

        // The control centre sits to the left of the clock: the TV's own
        // settings, one tap away.
        quickPill = new GlassIconPill(this, Ui.sliders(this, Ui.dp(this, 22)),
                "Quick settings");
        quickPill.setId(View.generateViewId());
        quickPill.setOnOpen(this::openQuickSettings);
        // tvOS keeps only the clock and the profile in the top row. The extra
        // buttons materialise when the user reaches that row, so at rest the
        // screen matches the reference and the controls are still one press
        // away.
        setShelfTools(false);

        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // An end margin, so the gap is on the side the next button is on in
        // a right-to-left language too.
        searchParams.setMarginEnd(Ui.dp(this, 10));
        pills.addView(searchPill, searchParams);
        LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        quickParams.setMarginEnd(Ui.dp(this, 10));
        pills.addView(quickPill, quickParams);
        pills.addView(clockPill, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        shelf.addView(pills, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The shelf of apps lives in a strip at the bottom of the screen.
        //
        // tvOS 26 puts the focused row of icons inside a fixed pane of glass
        // and lets the next row peek out of the bottom of the screen. The
        // rows are one list: moving the focus down slides the list, so the
        // row that has the focus is always the one in the dock.
        marginPx = margin;
        barPadPx = Ui.dp(this, BAR_PAD_DP);
        rowGapPx = Ui.dp(this, ROW_GAP_DP);
        peekPx = Ui.dp(this, PEEK_DP);

        // The dock: a fixed pane of glass that the focused row of apps sits
        // in. The rows slide behind it, the way tvOS keeps the shelf still
        // while the next row of icons rises into it.
        dockBar = new View(this);
        GlassDrawable dockMaterial = new GlassDrawable(this, 26f, 1f);
        dockBar.setBackground(dockMaterial);
        // The glass sits behind the icons. Elevation would paint it over them.
        dockBar.setElevation(0f);
        final float barRadius = Ui.dpf(this, 26f);
        dockBar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), barRadius);
            }
        });
        dockBarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM);
        dockBarParams.leftMargin = marginPx - barPadPx;
        dockBarParams.rightMargin = marginPx - barPadPx;
        root.addView(dockBar, dockBarParams);

        // The strip the rows live in. It is as tall as the dock plus the
        // peek, so the row below the focus is cut off by the screen edge.
        dockPort = new FrameLayout(this);
        dockPort.setClipChildren(true);
        dockPort.setPadding(margin, 0, margin, 0);
        portParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM);
        root.addView(dockPort, portParams);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        dockPort.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        applyDockMetrics(labelSpacePx(), tileHeightPx());

        root.addView(shelf, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        overlayHost = new FrameLayout(this);
        root.addView(overlayHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    private void loadApps() {
        final long gen = ++appsLoadGeneration;
        final int tileWidthPx = Ui.dp(this, gridMetrics().tileWidthDp);
        new Thread(() -> {
            List<AppEntry> result;
            try {
                result = AppRepository.get(this, tileWidthPx);
            } catch (RuntimeException | OutOfMemoryError e) {
                result = new ArrayList<>();
            }
            final List<AppEntry> loaded = result;
            final List<AppEntry> ordered = AppOrdering.order(loaded, prefs.order(),
                    prefs.hidden(), prefs.favourites());
            runOnUiThread(() -> {
                if (isFinishing() || gen != appsLoadGeneration) {
                    return;
                }
                favourites.clear();
                favourites.addAll(prefs.favourites());
                appEntries = ordered.toArray(new AppEntry[0]);
                buildGrid();
            });
        }, "sonora-apps-" + gen).start();
    }

    /** Puts the apps back in order after the user changed the order or a pin. */
    private void applyOrder() {
        favourites.clear();
        favourites.addAll(prefs.favourites());
        appEntries = AppOrdering.order(Arrays.asList(appEntries), prefs.order(),
                prefs.hidden(), favourites).toArray(new AppEntry[0]);
        buildGrid();
    }

    private void buildGrid() {
        tiles.clear();

        int labelHeight = labelSpacePx();
        int tileHeight = tileHeightPx();
        applyDockMetrics(labelHeight, tileHeight);

        AppEntry[] all = new AppEntry[appEntries.length + 1];
        System.arraycopy(appEntries, 0, all, 0, appEntries.length);
        all[appEntries.length] = new AppEntry("Settings", getPackageName(), null,
                settingsIcon(), null, 0xFF4A505C, true);

        for (AppEntry entry : all) {
            AppTileView tile = new AppTileView(this, tileHeight, labelHeight, prefs);
            tile.setId(View.generateViewId());
            tile.setEntry(entry);
            tile.setFavourite(isFavourite(entry));
            tile.setFocusListener(this::onTileFocused);
            tile.setOnClickListener(v -> launch((AppTileView) v));
            tile.setOnLongClickListener(v -> {
                showAppMenu((AppTileView) v);
                return true;
            });
            tiles.add(tile);
        }

        layoutRows();

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
     * Puts the tiles into their rows.
     *
     * The tiles are reused rather than rebuilt, so arranging the apps does
     * not throw the artwork away or restart the animation that is running.
     *
     * The rows are bare. The glass dock is a fixed pane behind them, so the
     * focused row always sits in the dock and the row below it peeks out at
     * the bottom of the screen, the way tvOS shows it.
     */
    private void layoutRows() {
        grid.removeAllViews();
        Grid.Metrics metrics = gridMetrics();
        int columns = metrics.columns;
        int tileWidth = Ui.dp(this, metrics.tileWidthDp);
        int gap = Ui.dp(this, metrics.gapDp);

        for (int start = 0; start < tiles.size(); start += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            int end = Math.min(tiles.size(), start + columns);
            for (int i = start; i < end; i++) {
                AppTileView tile = tiles.get(i);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(tileWidth,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                // The gap goes on the end side, so the row is the same in a
                // right-to-left language.
                params.setMarginEnd(gap);
                row.addView(tile, params);
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = rowGapPx;
            grid.addView(row, rowParams);
        }
        // The focused row has to start inside the dock, so the list is
        // padded by the glass padding and slid by whole rows.
        grid.setPadding(0, barPadPx, 0, 0);
        // The strip is shorter than the list so the next row peeks out. The
        // list itself must keep its full height, or the rows below the fold
        // measure to zero and can never take focus.
        int rows = (tiles.size() + columns - 1) / columns;
        FrameLayout.LayoutParams gridParams = (FrameLayout.LayoutParams) grid.getLayoutParams();
        gridParams.height = barPadPx + rows * (rowHeightPx + rowGapPx);
        grid.setLayoutParams(gridParams);
        scrollToRow(focusedRow(), false);
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

    /** Grid metrics for the real screen, so a 720p stick and a 4K panel match. */
    private Grid.Metrics gridMetrics() {
        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        return Grid.resolve(widthDp, prefs.columns());
    }

    /** The room the app names take under an icon, if the names are shown. */
    private int labelSpacePx() {
        return prefs.labels() == Prefs.LABELS_OFF ? 0 : Ui.dp(this, 22);
    }

    /** The height of one card, without the name under it. */
    private int tileHeightPx() {
        return Ui.dp(this, gridMetrics().tileHeightDp);
    }

    /**
     * Sizes the dock and the strip the rows live in.
     *
     * The dock is as tall as a row of icons plus the padding around them,
     * and the strip is the dock plus the peek of the row below it. The two
     * share a top edge, so the focused row always sits inside the glass.
     */
    private void applyDockMetrics(int labelHeight, int tileHeight) {
        rowHeightPx = tileHeight + labelHeight;
        int barHeight = rowHeightPx + barPadPx * 2;
        int portHeight = barHeight + peekPx + rowGapPx - barPadPx;
        dockBarParams.height = barHeight;
        // The dock sits above the peek, so the next row shows under it.
        dockBarParams.bottomMargin = peekPx + rowGapPx - barPadPx;
        dockBar.setLayoutParams(dockBarParams);
        portParams.height = portHeight;
        dockPort.setLayoutParams(portParams);
    }

    /** The distance from one row of icons to the next. */
    private int rowStridePx() {
        return rowHeightPx + rowGapPx;
    }

    /**
     * Brings a row of icons into the dock.
     *
     * The rows are one list that slides behind the fixed glass, so the row
     * that has the focus is always the one inside the dock, and the row
     * below it peeks out at the bottom of the screen.
     */
    private void scrollToRow(int row, boolean animate) {
        if (grid == null) {
            return;
        }
        float target = -row * (float) rowStridePx();
        if (!animate || Motion.isReduced(this)) {
            Motion.set(grid, Motion.TRANSLATION_Y, target);
        } else {
            Motion.spring(grid, Motion.TRANSLATION_Y, target,
                    Motion.DAMPING_CRITICAL, Motion.RESPONSE_MOVE);
        }
    }

    /** The row of icons the focus is in. */
    private int focusedRow() {
        View focused = getCurrentFocus();
        int index = focused instanceof AppTileView ? tiles.indexOf(focused) : -1;
        if (index < 0) {
            return -1;
        }
        return index / Math.max(1, gridMetrics().columns);
    }

    private void onTileFocused(AppTileView tile, AppEntry entry) {
        if (entry == null) {
            return;
        }
        hero.setEntry(entry);
        updateShelf(entry);
        focusedPackage = entry.packageName;
        setShelfTools(false);
        // The row that has the focus rides in the dock; the rows slide to
        // put it there, the way tvOS lifts the row into its shelf.
        int index = tiles.indexOf(tile);
        if (index >= 0) {
            scrollToRow(index / Math.max(1, gridMetrics().columns), true);
        }
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
            setSubtitle("No apps yet — open Settings to add some");
            return;
        }
        shelfTitle.setText(entry.label);
        if (editing) {
            setSubtitle("Arranging — move with the arrows, BACK to finish");
        } else if (entry.settings) {
            setSubtitle("Press OK to open Sonora settings");
        } else if (isFavourite(entry)) {
            setSubtitle("Pinned to the top");
        } else {
            setSubtitle(null);
        }
    }

    /** The hint under the title. Hidden when there is nothing worth saying. */
    private void setSubtitle(String text) {
        shelfSubtitle.setText(text == null ? "" : text);
        shelfSubtitle.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    /**
     * Reveals the search and quick-settings buttons only while the top row has
     * the focus. tvOS keeps that corner to the clock and the profile; the
     * controls are still one UP press away.
     */
    private void setShelfTools(boolean show) {
        if (searchPill == null || quickPill == null || show == shelfToolsShown) {
            return;
        }
        shelfToolsShown = show;
        for (View view : new View[]{searchPill, quickPill}) {
            view.setFocusable(show);
            view.setClickable(show);
            if (!view.isAttachedToWindow()) {
                view.setAlpha(show ? 1f : 0f);
                continue;
            }
            Motion.spring(view, Motion.ALPHA, show ? 1f : 0f,
                    Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS);
        }
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
        boolean favourite = isFavourite(entry);
        // Google TV's verified order: Open · Move · Favourites · Info · Uninstall.
        // "Edit Home Screen" is merged into Move, which starts arrange mode on
        // this app; "Search apps" is redundant now that the search pill opens it.
        String[] options = {"Open " + entry.label,
                "Move",
                favourite ? "Remove from Favourites" : "Add to Favourites",
                "App info",
                "Uninstall"};
        ChoiceOverlay overlay = new ChoiceOverlay(this, entry.label, options, -1, index -> {
            dismissChoice();
            switch (index) {
                case 0:
                    if (!entry.launch(this)) {
                        Ui.toast(this, "Could not open " + entry.label);
                    }
                    break;
                case 1:
                    // Arrange mode starts on this app so it is already the one
                    // the user is moving.
                    focusedPackage = entry.packageName;
                    startEditing();
                    break;
                case 2:
                    toggleFavourite(entry);
                    break;
                case 3:
                    try {
                        Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + entry.packageName));
                        info.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(info);
                    } catch (ActivityNotFoundException e) {
                        Ui.toast(this, "No app info screen");
                    }
                    break;
                case 4:
                    uninstall(entry);
                    break;
                default:
                    break;
            }
            suppressIdle = false;
        });
        showOverlay(overlay);
        overlay.focusSelected();
    }

    /** Asks the system to uninstall the app, with the user's confirmation. */
    private void uninstall(AppEntry entry) {
        PackageActions.Result result = PackageActions.requestUninstall(this,
                entry.packageName, entry.label);
        Ui.toast(this, result.message);
        if (result.ok) {
            // The app may be gone when we come back; drop the cache so the
            // grid is rebuilt from the real list.
            AppRepository.invalidate();
            prefs.bumpAppsEpoch();
        }
    }

    /** True when the user pinned this app to the top of the home screen. */
    private boolean isFavourite(AppEntry entry) {
        return entry != null && entry.packageName != null
                && favourites.contains(entry.packageName);
    }

    /** Pins an app to the top of the home screen, or unpins it. */
    private void toggleFavourite(AppEntry entry) {
        boolean was = isFavourite(entry);
        List<String> list = new ArrayList<>(prefs.favourites());
        if (was) {
            list.remove(entry.packageName);
        } else {
            list.add(0, entry.packageName);
        }
        prefs.setFavourites(list);
        applyOrder();
        Ui.toast(this, was ? entry.label + " unpinned" : entry.label + " pinned to the top");
    }

    private void hide(AppEntry entry) {
        Set<String> hidden = prefs.hidden();
        hidden.add(entry.packageName);
        prefs.setHidden(hidden);
        loadApps();
        Ui.toast(this, entry.label + " hidden");
    }

    // --- Arranging the apps -------------------------------------------------

    /** Starts arranging the apps with the D-pad. */
    private void startEditing() {
        editing = true;
        updateShelf(focusedEntry());
    }

    private void endEditing() {
        editing = false;
        updateShelf(focusedEntry());
    }

    private AppEntry focusedEntry() {
        View focused = getCurrentFocus();
        if (focused instanceof AppTileView) {
            return ((AppTileView) focused).entry();
        }
        return appEntries.length > 0 ? appEntries[0] : null;
    }

    /**
     * Moves the focused app one step, while the user is arranging the apps.
     *
     * @return true when the key was used for arranging.
     */
    private boolean editMove(int key) {
        View focused = getCurrentFocus();
        int index = tiles.indexOf(focused);
        // The Settings tile is not an app, so it stays where it is.
        if (index < 0 || index >= appEntries.length) {
            return false;
        }
        int columns = gridMetrics().columns;
        int row = index / columns;
        int column = index % columns;
        int target;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                target = column > 0 ? index - 1 : -1;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                target = (column + 1 < columns && index + 1 < appEntries.length) ? index + 1 : -1;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                target = row > 0 ? index - columns : -1;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                target = (index + columns < appEntries.length) ? index + columns : -1;
                break;
            default:
                return false;
        }
        if (target < 0) {
            // At the edge of the grid: the key is used, nothing moves.
            return true;
        }
        moveTile(index, target);
        return true;
    }

    /** Moves one app to another place in the grid and remembers it. */
    private void moveTile(int from, int to) {
        if (from < 0 || from >= appEntries.length || to < 0 || to >= appEntries.length || from == to) {
            return;
        }
        if (from >= tiles.size() || to >= tiles.size()) {
            return;
        }
        List<AppEntry> list = new ArrayList<>(Arrays.asList(appEntries));
        AppEntry moved = list.remove(from);
        list.add(to, moved);
        appEntries = list.toArray(new AppEntry[0]);

        AppTileView tile = tiles.remove(from);
        tiles.add(to, tile);

        prefs.setOrder(AppOrdering.saveOrder(list));
        layoutRows();
        // The tile that moved keeps the focus, so the user can keep moving it.
        focusedPackage = moved.packageName;
        restoreFocus();
    }

    // --- Searching ----------------------------------------------------------

    /** Opens the local search: the names of the apps on this TV, nothing else. */
    private void openSearch() {
        if (searchOverlay != null) {
            return;
        }
        suppressIdle = true;
        searchFromPill = getCurrentFocus() == searchPill;
        Grid.Metrics metrics = gridMetrics();
        List<AppEntry> apps = new ArrayList<>(Arrays.asList(appEntries));
        searchOverlay = new SearchOverlay(this, apps, Ui.dp(this, metrics.tileWidthDp),
                Ui.dp(this, metrics.tileHeightDp), Ui.dp(this, metrics.marginDp),
                new SearchOverlay.Listener() {
            @Override
            public void onPicked(AppEntry entry) {
                // The app opens once the search has gone, so the two do not
                // fight over the screen.
                closeSearch(() -> {
                    if (entry != null && !entry.launch(HomeActivity.this)) {
                        Ui.toast(HomeActivity.this, "Could not open " + entry.label);
                    }
                });
            }

            @Override
            public void onClosed() {
                closeSearch();
            }
        });
        overlayHost.addView(searchOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        searchOverlay.start();
    }

    private void closeSearch() {
        closeSearch(null);
    }

    /** Takes the search away, and runs the work once it has gone. */
    private void closeSearch(Runnable after) {
        if (searchOverlay == null) {
            return;
        }
        final SearchOverlay overlay = searchOverlay;
        searchOverlay = null;
        suppressIdle = false;
        overlay.dismiss(() -> {
            overlayHost.removeView(overlay);
            // The focus goes back where it came from: the button that opened
            // the search, or the app that had it.
            if (searchFromPill) {
                searchPill.requestFocus();
            } else {
                restoreFocus();
            }
            if (after != null) {
                after.run();
            }
        });
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

    /** The control centre: the TV's own settings, one tap away. */
    private void openQuickSettings() {
        try {
            startActivity(new Intent(this, com.mitun.sonora.tools.ToolActivity.class)
                    .putExtra(com.mitun.sonora.tools.ToolActivity.EXTRA_SCREEN, "quick"));
        } catch (RuntimeException e) {
            Ui.toast(this, "Could not open the quick settings");
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
        suppressIdle = false;
        if (choiceOverlay == null) {
            return;
        }
        final View overlay = choiceOverlay;
        choiceOverlay = null;
        if (overlay instanceof ChoiceOverlay) {
            // The picker leaves the way it came, and the tile the user came
            // from gets the focus back when it is gone.
            ((ChoiceOverlay) overlay).dismiss(() -> {
                overlayHost.removeView(overlay);
                restoreFocus();
            });
        } else {
            overlayHost.removeView(overlay);
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
        wallpaperRequest = wanted;
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
                if (!wanted.equals(wallpaperRequest)) {
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
        // The wallpaper is invisible under the screen saver, which holds two
        // screen-sized scenes of its own. Letting it go keeps the peak at two
        // full-screen bitmaps instead of three.
        wallpaperRequest = "";
        wallpaperBitmap = null;
        wallpaperKey = null;
        hero.release();
        screensaver = new ScreensaverView(this);
        screensaver.setSceneDurationSec(prefs.screensaverSceneSec());
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
        // It was released when the screen saver came up.
        applyWallpaper();
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
            // The remote's search key opens Sonora's own search: the names of
            // the apps on this TV, with nothing sent anywhere.
            if (prefs.searchKey() && isSearchKey(event.getKeyCode())) {
                if (searchOverlay == null) {
                    openSearch();
                }
                return true;
            }
            // While the apps are being arranged, OK means "done".
            if (editing && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                    || event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                endEditing();
                return true;
            }
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
                    if (editing) {
                        // While arranging, the arrows move the app itself.
                        if (editMove(event.getKeyCode())) {
                            return true;
                        }
                        break;
                    }
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

    /** The keys a remote sends when the user asks for a search. */
    private static boolean isSearchKey(int code) {
        return code == KeyEvent.KEYCODE_SEARCH
                || code == KeyEvent.KEYCODE_ASSIST
                || code == KeyEvent.KEYCODE_VOICE_ASSIST;
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
            int target = Grid.moveTarget(index, columns, tiles.size(), key);
            if (target >= 0) {
                tiles.get(target).requestFocus();
            } else if (key == KeyEvent.KEYCODE_DPAD_UP && row == 0) {
                // Leaving the grid for the top shelf. The clock is the only
                // control there at rest; it reveals the rest of the row.
                clockPill.requestFocus();
            }
            return true;
        }
        if (focused == searchPill) {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && !tiles.isEmpty()) {
                tiles.get(0).requestFocus();
            } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                quickPill.requestFocus();
            }
            return true;
        }
        if (focused == quickPill) {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && !tiles.isEmpty()) {
                tiles.get(0).requestFocus();
            } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                clockPill.requestFocus();
            } else if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                searchPill.requestFocus();
            }
            return true;
        }
        if (focused == clockPill) {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && !tiles.isEmpty()) {
                tiles.get(0).requestFocus();
            } else if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                quickPill.requestFocus();
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
        if (searchOverlay != null) {
            closeSearch();
            return true;
        }
        if (choiceOverlay != null) {
            dismissChoice();
            return true;
        }
        if (editing) {
            endEditing();
            return true;
        }
        int focusedIndex = -1;
        View focused = getCurrentFocus();
        if (focused instanceof AppTileView) {
            focusedIndex = tiles.indexOf(focused);
        }
        // How far the rows have been slid up out of the dock. It stands in
        // for the scroll position the grid used to keep.
        int slid = grid == null ? 0 : Math.abs((int) grid.getTranslationY());
        if (BackPolicy.atTopLevel(slid, focusedIndex,
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
        scrollToRow(0, true);
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
        if (gridChanged() || artworkDropped) {
            // The artwork was dropped to free memory, so the list has to be
            // read again even when nothing about the grid has changed.
            artworkDropped = false;
            loadApps();
        }
        applyWallpaper();
        clockPill.refresh();
        SystemInfo.invalidate();
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
        SystemInfo.invalidate();
        Motion.refresh(this);
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
            // The artwork of every app is the other thing worth holding on
            // to. It is dropped and read again, so the old pixels are gone
            // before the new ones are made.
            AppRepository.invalidate();
            artworkDropped = true;
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
