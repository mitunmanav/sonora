package com.mitun.sonora;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mitun.sonora.tools.QuickPanel;
import com.mitun.sonora.ui.AppTileView;
import com.mitun.sonora.ui.BackdropView;
import com.mitun.sonora.ui.ChoiceOverlay;
import com.mitun.sonora.ui.ControlCenter;
import com.mitun.sonora.ui.GlassDrawable;
import com.mitun.sonora.ui.GlassLens;
import com.mitun.sonora.ui.GlassPillView;
import com.mitun.sonora.ui.HeroBackgroundView;
import com.mitun.sonora.ui.Motion;
import com.mitun.sonora.ui.ScreensaverView;
import com.mitun.sonora.ui.SearchOverlay;
import com.mitun.sonora.ui.Theme;
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
    private static final int BAR_PAD_DP = 20;
    /** The gap between two rows of icons. */
    private static final int ROW_GAP_DP = 41;
    /** How much of the next row shows under the dock, cut by the screen edge. */
    private static final int PEEK_DP = 13;
    /** The gap between two rows once the grid is open, which is wider. */
    private static final int OPEN_ROW_GAP_DP = 55;
    /** Where the dock's top edge rides to when the grid opens. */
    private static final float OPEN_DOCK_TOP = 0.056f;
    /** The corner of the glass dock. */
    private static final float DOCK_RADIUS_DP = 29f;
    /** The dock is baked at half size: the bend hides it, and it costs a quarter. */
    private static final int DOCK_LENS_SCALE = 2;

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
    private TextView shelfTitle;
    private TextView shelfSubtitle;
    private LinearLayout shelf;
    private BackdropView gridBackdrop;
    /** The name under the focused card, once the grid is open. */
    private TextView gridLabel;
    private int gridLabelBase;
    private int gridLabelRow;
    private int dockTravelPx;
    private int openExtraPx;
    private float gridScroll;
    private final Motion.FloatProperty gridScrollValue = new Motion.FloatProperty() {
        @Override
        public float get() {
            return gridScroll;
        }

        @Override
        public void set(float value) {
            applyGridScroll(value);
        }
    };
    private ControlCenter controlCenter;
    private LinearLayout grid;
    private FrameLayout dockPort;
    private View dockBar;
    private GlassDrawable dockMaterial;
    private View chevron;
    private FrameLayout.LayoutParams chevronParams;
    /** What the glass was last baked from, so a resume does not bake it again. */
    private String glassKey = "";
    private FrameLayout.LayoutParams dockBarParams;
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

    private long lastInteraction;
    private boolean suppressIdle;
    private boolean initialFocusDone;

    /** The decoded wallpaper, kept so a resume does not re-decode a full JPEG. */
    private Bitmap wallpaperBitmap;
    /**
     * A half-size copy the glass is bent from. The wallpaper itself lives on the
     * GPU where the platform allows it, and cannot be read back.
     */
    private Bitmap lensSource;
    /** The small blurred wallpaper: the open grid, frosted glass, the first frame. */
    private Bitmap softened;
    /** Bumped for each bake, so a slow one that was overtaken is dropped. */
    private int glassGeneration;
    /** The instance on screen. HOME and a launcher tile each make one, in their own task. */
    private static java.lang.ref.WeakReference<HomeActivity> latest;
    private boolean resumed;
    private boolean builtLight;
    private int builtTier;
    /** The key of the wallpaper that is on screen. */
    private String wallpaperKey;
    /** The key of the newest request, so a decode that is overtaken can be dropped. */
    private String wallpaperRequest = "";

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
        Theme.load(this);
        // A launcher tile and the HOME key start separate tasks, and each would keep
        // a full home screen alive. Only the newest one is worth its memory.
        HomeActivity older = latest == null ? null : latest.get();
        // A recreate brings the same screen back, and the old one is already destroyed
        // by now. Finishing that would close this new one, which shares its record.
        if (older != null && older != this && !older.isFinishing() && !older.isDestroyed()) {
            older.finish();
        }
        latest = new java.lang.ref.WeakReference<>(this);
        builtLight = Theme.isLight();
        builtTier = DeviceTier.get(this);
        Ui.immersive(this);
        buildLayout();
        // The hero below paints every pixel, so the window has no need to first.
        getWindow().setBackgroundDrawable(null);
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
        root.setClipChildren(false);
        root.setClipToPadding(false);

        Grid.Metrics metrics = gridMetrics();
        int margin = Ui.dp(this, metrics.marginDp);

        hero = new HeroBackgroundView(this);
        root.addView(hero, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Top shelf: the focused app's title, plus the clock pill on the right.
        shelf = new LinearLayout(this) {
            // Fading this row as the grid opens would otherwise take an offscreen
            // buffer as wide as the screen. Nothing in it overlaps enough to show.
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        shelf.setOrientation(LinearLayout.HORIZONTAL);
        shelf.setGravity(Gravity.TOP);
        shelf.setClipChildren(false);
        // Measured from tvOS 26: the title starts 3.6% in, the pill ends 2.8%
        // from the edge and 4.7% from the top.
        shelf.setPadding(Ui.dp(this, 34), Ui.dp(this, 25), Ui.dp(this, 27), 0);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(0, Ui.dp(this, 7), 0, 0);
        shelfTitle = new TextView(this);
        shelfTitle.setTextColor(Color.WHITE);
        Type.apply(shelfTitle, Type.TITLE);
        // It stands where tvOS puts the show's wordmark, so it has to carry.
        shelfTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f);
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
        // The clock is a readout, as on tvOS. The controls live in Control
        // Center, one press up from the first row.
        clockPill.setFocusable(false);
        clockPill.setClickable(false);
        shelf.addView(clockPill, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The apps are one list, and the dock is a pane of glass around its
        // first row. At rest they sit at the bottom of the screen with the next
        // row peeking out. Moving the focus down carries them up together into
        // a full-screen grid over the blurred wallpaper, as tvOS 26 does.
        marginPx = margin;
        barPadPx = Ui.dp(this, BAR_PAD_DP);
        rowGapPx = Ui.dp(this, ROW_GAP_DP);
        peekPx = Ui.dp(this, PEEK_DP);

        gridBackdrop = new BackdropView(this, softened);
        gridBackdrop.setVisibility(View.GONE);
        root.addView(gridBackdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dockBar = new View(this);
        dockMaterial = new GlassDrawable(this, DOCK_RADIUS_DP, 1f);
        dockBar.setBackground(dockMaterial);
        // The glass sits behind the icons. Elevation would paint it over them.
        dockBar.setElevation(0f);
        final float barRadius = Ui.dpf(this, DOCK_RADIUS_DP);
        dockBar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), barRadius);
            }
        });
        dockBarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM);
        int dockMargin = Math.round(getResources().getDisplayMetrics().widthPixels
                * Grid.DOCK_MARGIN_RATIO);
        dockBarParams.leftMargin = dockMargin;
        dockBarParams.rightMargin = dockMargin;
        root.addView(dockBar, dockBarParams);

        // The mark above the dock that says there is more up there.
        chevron = new ChevronView(this);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        chevronParams = new FrameLayout.LayoutParams(Ui.dp(this, 27), Ui.dp(this, 8),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        root.addView(chevron, chevronParams);

        // The strip the rows live in: the whole screen, so the list can ride up
        // it. The next row at rest is cut off by the screen edge.
        dockPort = new FrameLayout(this);
        dockPort.setClipChildren(true);
        root.addView(dockPort, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        dockPort.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        gridLabel = new TextView(this);
        gridLabel.setTextColor(Color.WHITE);
        Type.apply(gridLabel, Type.BODY);
        gridLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        gridLabel.setSingleLine(true);
        gridLabel.setEllipsize(TextUtils.TruncateAt.END);
        gridLabel.setAlpha(0f);
        gridLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dockPort.addView(gridLabel, new FrameLayout.LayoutParams(
                Ui.dp(this, metrics.tileWidthDp + 40), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));
        openExtraPx = Ui.dp(this, OPEN_ROW_GAP_DP - ROW_GAP_DP);
        applyDockMetrics(labelSpacePx(), tileHeightPx());

        View.OnLayoutChangeListener rebake = (v, l, t, r, b, ol, ot, orr, ob) -> {
            if (l != ol || t != ot || r != orr || b != ob) {
                bakeGlass();
            }
        };
        dockBar.addOnLayoutChangeListener(rebake);
        clockPill.addOnLayoutChangeListener(rebake);

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
        // Stored at the size the focused card draws at, so it stays sharp.
        final int tileWidthPx = DeviceTier.artworkWidth(DeviceTier.get(this),
                Ui.dp(this, gridMetrics().tileWidthDp), AppTileView.FOCUS_SCALE);
        new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
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
                null, null, 0xFF4A505C, true);

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
        // padded by the glass padding and slid by whole rows. The side margin
        // is padding on the list too: the strip clips the list to the list's
        // own bounds, so a narrower list would cut a focused card's outer edge.
        grid.setPadding(marginPx, barPadPx, marginPx, 0);
        // The strip is shorter than the list so the next row peeks out. The
        // list itself must keep its full height, or the rows below the fold
        // measure to zero and can never take focus.
        int rows = (tiles.size() + columns - 1) / columns;
        FrameLayout.LayoutParams gridParams = (FrameLayout.LayoutParams) grid.getLayoutParams();
        // Room for the wider gaps of the open grid: the list is clipped to it.
        gridParams.height = barPadPx + rows * (rowHeightPx + rowGapPx + openExtraPx);
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
        dockBarParams.height = barHeight;
        // The dock sits above the peek, so the next row shows under it.
        dockBarParams.bottomMargin = peekPx + rowGapPx - barPadPx;
        dockBar.setLayoutParams(dockBarParams);
        chevronParams.bottomMargin = dockBarParams.bottomMargin + barHeight + Ui.dp(this, 11);
        chevron.setLayoutParams(chevronParams);
        int height = getResources().getDisplayMetrics().heightPixels;
        int dockTop = height - dockBarParams.bottomMargin - barHeight;
        dockTravelPx = dockTop - Math.round(height * OPEN_DOCK_TOP);
        FrameLayout.LayoutParams gridParams = (FrameLayout.LayoutParams) grid.getLayoutParams();
        gridParams.topMargin = dockTop;
        grid.setLayoutParams(gridParams);
    }

    /** The distance from one row of icons to the next. */
    private int rowStridePx() {
        return rowHeightPx + rowGapPx;
    }

    /**
     * Brings a row of icons into view: the first row in the dock at the bottom
     * of the screen, any other in the open grid.
     */
    private void scrollToRow(int row, boolean animate) {
        if (grid == null) {
            return;
        }
        float target = Grid.scrollFor(row, dockTravelPx, rowStridePx() + openExtraPx);
        if (!animate || Motion.isReduced(this)) {
            Motion.cancel(grid);
            applyGridScroll(target);
        } else {
            Motion.springCustom(grid, 0, target, Motion.DAMPING_CRITICAL, Motion.RESPONSE_MOVE,
                    gridScrollValue);
        }
    }

    /**
     * One position drives the whole move, so the dock, the rows, the wallpaper
     * and the clock can never fall out of step, even when it is interrupted.
     */
    private void applyGridScroll(float value) {
        gridScroll = value;
        float open = Grid.openProgress(value, dockTravelPx);
        grid.setTranslationY(value);
        dockBar.setTranslationY(value);
        for (int i = 1; i < grid.getChildCount(); i++) {
            grid.getChildAt(i).setTranslationY(i * openExtraPx * open);
        }
        gridLabel.setTranslationY(gridLabelBase + value + gridLabelRow * openExtraPx * open);
        // The top row is gone by the time the dock has come halfway up to it.
        shelf.setAlpha(Math.max(0f, 1f - open * 2f));
        chevron.setTranslationY(value);
        gridBackdrop.setAlpha(open);
        gridBackdrop.setVisibility(open > 0f ? View.VISIBLE : View.GONE);
        // Once the blur covers it, drawing the wallpaper is a screen of wasted pixels.
        hero.setVisibility(open >= 1f ? View.INVISIBLE : View.VISIBLE);
        dockMaterial.setLens(1f - open);
    }

    /** Puts the app's name under its card, once the grid is open. */
    private void placeGridLabel(AppTileView tile, AppEntry entry, int row) {
        if (row == 0 || labelSpacePx() > 0) {
            gridLabelRow = 0;
            Motion.spring(gridLabel, Motion.ALPHA, 0f, Motion.DAMPING_CRITICAL,
                    Motion.RESPONSE_FOCUS);
            return;
        }
        // The name fades in under the new card rather than jumping there from the
        // old one, while the card it belongs to is still growing.
        if (!entry.label.contentEquals(gridLabel.getText())) {
            gridLabel.setAlpha(0f);
            gridLabel.setText(entry.label);
        }
        View rowView = (View) tile.getParent();
        gridLabel.setTranslationX(marginPx + rowView.getLeft() - grid.getPaddingLeft()
                + tile.getLeft() + (tile.getWidth() - gridLabel.getLayoutParams().width) / 2f);
        // Below the card as it stands under the focus.
        float grown = rowHeightPx * (AppTileView.FOCUS_SCALE - 1f) / 2f;
        gridLabelRow = row;
        gridLabelBase = Math.round(((FrameLayout.LayoutParams) grid.getLayoutParams()).topMargin
                + barPadPx + row * rowStridePx() + rowHeightPx + grown + Ui.dp(this, 5));
        applyGridScroll(gridScroll);
        Motion.spring(gridLabel, Motion.ALPHA, 1f, Motion.DAMPING_CRITICAL,
                Motion.RESPONSE_FOCUS);
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
        updateShelf(entry);
        focusedPackage = entry.packageName;
        int index = tiles.indexOf(tile);
        if (index >= 0) {
            int row = index / Math.max(1, gridMetrics().columns);
            scrollToRow(row, true);
            showChevron(row == 0);
            placeGridLabel(tile, entry, row);
        }
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

    private void showChevron(boolean show) {
        Motion.spring(chevron, Motion.ALPHA, show ? 1f : 0f,
                Motion.DAMPING_CRITICAL, Motion.RESPONSE_FOCUS);
    }

    // --- Control Center -----------------------------------------------------

    private void openControlCenter() {
        if (controlCenter != null) {
            return;
        }
        suppressIdle = true;
        showChevron(false);
        clockPill.refresh();
        controlCenter = new ControlCenter(this, clockPill.clockText(), QuickPanel.wifiName(this),
                gridBackdrop.softened(), new ControlCenter.Listener() {
                    @Override
                    public void onPick(int action) {
                        closeControlCenter(() -> runControl(action));
                    }

                    @Override
                    public void onDismiss() {
                        closeControlCenter(null);
                    }
                });
        overlayHost.addView(controlCenter, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Control Center repeats the clock in its own top row.
        Motion.spring(clockPill, Motion.ALPHA, 0f, Motion.DAMPING_CRITICAL,
                Motion.RESPONSE_FOCUS);
        controlCenter.open();
    }

    private void closeControlCenter(Runnable after) {
        if (controlCenter == null) {
            return;
        }
        final ControlCenter closing = controlCenter;
        controlCenter = null;
        suppressIdle = false;
        Motion.spring(clockPill, Motion.ALPHA, 1f, Motion.DAMPING_CRITICAL,
                Motion.RESPONSE_FOCUS);
        restoreFocus();
        showChevron(true);
        closing.close(() -> overlayHost.removeView(closing));
        if (after != null) {
            after.run();
        }
    }

    private void runControl(int action) {
        switch (action) {
            case ControlCenter.SAVER:
                showScreensaver();
                break;
            case ControlCenter.WIFI:
                QuickPanel.openWifi(this);
                break;
            case ControlCenter.BLUETOOTH:
                QuickPanel.openBluetooth(this);
                break;
            case ControlCenter.SOUND:
                QuickPanel.openSound(this);
                break;
            case ControlCenter.DISPLAY:
                QuickPanel.openDisplay(this);
                break;
            case ControlCenter.SEARCH:
                openSearch();
                break;
            case ControlCenter.APPS:
                openTool("apps");
                break;
            case ControlCenter.QUICK:
                openQuickSettings();
                break;
            case ControlCenter.SETTINGS:
            default:
                openSettings();
                break;
        }
    }

    private void openTool(String screen) {
        try {
            startActivity(new Intent(this, com.mitun.sonora.tools.ToolActivity.class)
                    .putExtra(com.mitun.sonora.tools.ToolActivity.EXTRA_SCREEN, screen));
        } catch (RuntimeException e) {
            Ui.toast(this, "Could not open that screen");
        }
    }

    /** The chevron tvOS draws above the dock, as a shallow caret. */
    private static final class ChevronView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        ChevronView(android.content.Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(Ui.dpf(context, 1.75f));
            paint.setColor(0xB3FFFFFF);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            float inset = paint.getStrokeWidth();
            path.reset();
            path.moveTo(inset, h - inset);
            path.lineTo(w / 2f, inset);
            path.lineTo(w - inset, h - inset);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawPath(path, paint);
        }
    }

    /**
     * Gives every pane of glass on the home screen its share of the wallpaper. It
     * runs only when the wallpaper or the layout changes; after that each pane is
     * one textured quad. Bending takes a mesh per pane, so it runs off the main
     * thread. The lightest tier frosts from the blur instead, which makes no bitmap.
     */
    private void bakeGlass() {
        final int width = root.getWidth();
        final int height = root.getHeight();
        if (width == 0 || height == 0) {
            return;
        }
        final Rect dock = screenRect(dockBar);
        final Rect clock = screenRect(clockPill);
        final Bitmap source = lensSource;
        boolean bend = source != null && !source.isRecycled()
                && DeviceTier.bentGlass(DeviceTier.get(this));
        Bitmap frost = softened;
        String key = (bend ? System.identityHashCode(source) : frost == null ? 0
                : System.identityHashCode(frost)) + "@" + width + "x" + height + dock + clock;
        if (key.equals(glassKey)) {
            return;
        }
        glassKey = key;
        final int generation = ++glassGeneration;
        if (!bend) {
            dockMaterial.setFrost(frost, width, height, HeroBackgroundView.ZOOM, dock, 0x1F000000);
            clockPill.setFrost(frost, width, height, HeroBackgroundView.ZOOM, clock);
            return;
        }
        final float dockRadius = Ui.dpf(this, DOCK_RADIUS_DP);
        new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            Bitmap dockLens = null;
            Bitmap clockLens = null;
            try {
                dockLens = GlassLens.bake(source, width, height, HeroBackgroundView.ZOOM, dock,
                        dockRadius, DOCK_LENS_SCALE);
                clockLens = GlassLens.bake(source, width, height, HeroBackgroundView.ZOOM, clock,
                        clock.height() / 2f, 1);
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // The glass stays dark, which still reads as glass.
            }
            final Bitmap bakedDock = dockLens;
            final Bitmap bakedClock = clockLens;
            runOnUiThread(() -> {
                if (generation == glassGeneration && !isDestroyed()) {
                    dockMaterial.setBackdrop(bakedDock, 0x1F000000);
                    clockPill.setBackdrop(bakedClock);
                }
            });
        }, "sonora-glass").start();
    }

    /** Where a view is laid out on the screen, ignoring its focus scale. */
    private Rect screenRect(View view) {
        int left = 0;
        int top = 0;
        View walk = view;
        while (walk != null && walk != root) {
            left += walk.getLeft();
            top += walk.getTop();
            walk = walk.getParent() instanceof View ? (View) walk.getParent() : null;
        }
        return new Rect(left, top, left + view.getWidth(), top + view.getHeight());
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
        // The hint on the shelf is out of sight once the grid is open.
        Ui.toast(this, "Move with the arrows, BACK to finish");
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
        Grid.Metrics metrics = gridMetrics();
        List<AppEntry> apps = new ArrayList<>(Arrays.asList(appEntries));
        searchOverlay = new SearchOverlay(this, apps, Ui.dp(this, metrics.tileWidthDp),
                Ui.dp(this, metrics.tileHeightDp), Ui.dp(this, metrics.gapDp), metrics.columns,
                Ui.dp(this, metrics.marginDp), softened,
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
            restoreFocus();
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
        final int index = prefs.wallpaper();
        final String uri = prefs.wallpaperUri();
        final int tier = DeviceTier.get(this);
        final int divisor = DeviceTier.wallpaperDivisor(tier);
        final int width = getResources().getDisplayMetrics().widthPixels;
        final int height = getResources().getDisplayMetrics().heightPixels;
        final String blurKey = BackdropView.key(index, uri);
        String key = blurKey + "@" + width + "x" + height + "/" + tier;
        if (key.equals(wallpaperKey) && wallpaperBitmap != null && !wallpaperBitmap.isRecycled()) {
            hero.setWallpaper(wallpaperBitmap);
            bakeGlass();
            return;
        }
        // The blur from the last decode covers the screen until this one lands, so
        // a return to the launcher never shows an empty frame.
        Bitmap blur = BackdropView.remembered(blurKey);
        if (blur != null) {
            useSoftened(blur);
        }
        final String wanted = key;
        wallpaperRequest = wanted;
        new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            Bitmap picture = null;
            Bitmap thumbnail = BackdropView.remembered(blurKey);
            Bitmap half = null;
            try {
                if (index < 0 && uri != null) {
                    // A photo the user picked; fall back to a bundled scene if
                    // the permission was refused or the file has gone.
                    picture = Ui.decodeUri(getResources(), getContentResolver(),
                            Uri.parse(uri), width / divisor);
                }
                if (picture == null) {
                    picture = Ui.decodeScene(getResources(), Scenes.ALL.get(
                            Scenes.safeIndex(index)).res, width / divisor, height / divisor,
                            tier == DeviceTier.LOW ? Bitmap.Config.RGB_565
                                    : Bitmap.Config.ARGB_8888);
                }
                if (picture != null && thumbnail == null) {
                    thumbnail = BackdropView.soften(picture);
                    BackdropView.remember(blurKey, thumbnail);
                }
                if (picture != null && DeviceTier.bentGlass(tier)) {
                    half = halfSize(picture);
                    picture = moveToGpu(picture);
                }
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // The blur or the plain gradient stays up, which beats a crash.
            }
            final Bitmap decoded = picture;
            final Bitmap source = half;
            final Bitmap blurred = thumbnail;
            runOnUiThread(() -> {
                // A newer wallpaper was asked for while this one was decoding, or
                // the screen is gone: this one's memory is better given back.
                if (!wanted.equals(wallpaperRequest) || isFinishing() || isDestroyed()) {
                    return;
                }
                wallpaperBitmap = decoded;
                wallpaperKey = wanted;
                lensSource = source;
                useSoftened(blurred);
                hero.setWallpaper(decoded);
                bakeGlass();
            });
        }, "sonora-wallpaper").start();
    }

    private void useSoftened(Bitmap blur) {
        softened = blur;
        hero.setPlaceholder(blur);
        gridBackdrop.setSoftened(blur);
    }

    private static Bitmap halfSize(Bitmap picture) {
        Bitmap half = Bitmap.createBitmap(Math.max(1, picture.getWidth() / 2),
                Math.max(1, picture.getHeight() / 2), Bitmap.Config.RGB_565);
        new Canvas(half).drawBitmap(picture, null,
                new Rect(0, 0, half.getWidth(), half.getHeight()),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        return half;
    }

    /**
     * Keeps the wallpaper in one place. An ordinary bitmap is held twice, once in
     * memory and once as the texture made from it.
     */
    private static Bitmap moveToGpu(Bitmap picture) {
        if (android.os.Build.VERSION.SDK_INT < 26) {
            return picture;
        }
        Bitmap onGpu = picture.copy(Bitmap.Config.HARDWARE, false);
        if (onGpu == null) {
            return picture;
        }
        picture.recycle();
        return onGpu;
    }

    /** Lets the screen-sized pictures go. The blur stays: it is a few kilobytes. */
    private void releaseWallpaper() {
        wallpaperRequest = "";
        wallpaperBitmap = null;
        wallpaperKey = null;
        lensSource = null;
        glassKey = "";
        glassGeneration++;
        hero.release();
        if (dockMaterial != null) {
            dockMaterial.setBackdrop(null, 0x1F000000);
            clockPill.setBackdrop(null);
        }
    }

    private void showScreensaver() {
        if (screensaver != null) {
            return;
        }
        // The wallpaper is invisible under the screen saver, which holds two
        // screen-sized scenes of its own. Letting it go keeps the peak at two
        // full-screen bitmaps instead of three.
        releaseWallpaper();
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
                openControlCenter();
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
        if (controlCenter != null) {
            closeControlCenter(null);
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
        resumed = true;
        Theme.load(this);
        if (Theme.isLight() != builtLight || DeviceTier.get(this) != builtTier) {
            // The search and Control Center build from the theme, and the tier set
            // the window and the cards, so a change in Settings needs a fresh screen.
            recreate();
            return;
        }
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
        releaseWallpaper();
        applyWallpaper();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (resumed) {
            // On screen, the picture is what the user is looking at, so it stays.
            // Only a real emergency changes it, for the lightest one there is.
            if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
                    && DeviceTier.get(this) != DeviceTier.LOW) {
                DeviceTier.degrade();
                applyWallpaper();
            }
            return;
        }
        // Hidden, this memory belongs to the app the user is watching.
        boolean low = DeviceTier.get(this) == DeviceTier.LOW;
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
                || low && level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            releaseWallpaper();
        }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            // The tiles hold the artwork too, so they go with the cache. The system
            // is close to killing the process and a rebuild costs less than a restart.
            tiles.clear();
            grid.removeAllViews();
            AppRepository.invalidate();
            artworkDropped = true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Settings opens over this screen on a TV that has nothing to spare.
        if (DeviceTier.get(this) == DeviceTier.LOW) {
            releaseWallpaper();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
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
