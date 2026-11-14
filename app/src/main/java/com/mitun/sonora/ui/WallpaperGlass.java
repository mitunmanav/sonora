package com.mitun.sonora.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.view.View;

import com.mitun.sonora.DeviceTier;
import com.mitun.sonora.Prefs;
import com.mitun.sonora.Scenes;
import com.mitun.sonora.Workers;

/**
 * The wallpaper pipeline behind the home screen: the full-size picture for the
 * hero, the small blur the open grid and the first frame frost from, and the
 * half-size copy the glass panes bend their lenses out of.
 *
 * The panes' share of the wallpaper is baked off the main thread on a worker
 * that belongs to this object; a bake that was overtaken is dropped, so a slow
 * phone never draws a stale layout's glass.
 */
public final class WallpaperGlass {
    /** The home screen's end of the pipeline: what to draw, on the main thread. */
    public interface Listener {
        /** The decoded wallpaper for the hero; a blur may precede it. */
        void onWallpaper(Bitmap picture);

        /** The small blurred wallpaper: the placeholder and the open grid. */
        void onSoftened(Bitmap softened);

        /** The wallpaper's memory was let go. */
        void onReleased();
    }

    private static final int VEIL = 0x1F000000;
    /** The dock's lens is baked at half size: the bend hides it, and it costs a quarter. */
    private static final int DOCK_LENS_SCALE = 2;

    private final View root;
    private final View dockBar;
    private final GlassDrawable dockMaterial;
    private final GlassPillView clockPill;
    private final GlyphPillView searchPill;
    private final float dockRadiusPx;
    private final Listener listener;

    /** The decoded wallpaper, kept so a resume does not re-decode a full JPEG. */
    private Bitmap wallpaperBitmap;
    private String wallpaperKey;
    /** The wallpaper key a decode thread is running for; "" while nothing flies. */
    private String wallpaperRequest = "";
    /**
     * A half-size copy the glass is bent from. The wallpaper itself lives on the
     * GPU where the platform allows it, and cannot be read back.
     */
    private Bitmap lensSource;
    /** The small blurred wallpaper: the open grid, frosted glass, the first frame. */
    private Bitmap softened;
    /** What the glass was last baked from, so a resume does not bake it again. */
    private String glassKey = "";
    /** Bumped for each bake, so a slow one that was overtaken is dropped. */
    private int glassGeneration;
    /** One thread for the bakes, so rapid layout churn cannot flood the CPU. */
    private final java.util.concurrent.ExecutorService glassBaker =
            Workers.single("sonora-glass");

    public WallpaperGlass(View root, View dockBar, GlassDrawable dockMaterial,
                          GlassPillView clockPill, GlyphPillView searchPill,
                          float dockRadiusPx, Listener listener) {
        this.root = root;
        this.dockBar = dockBar;
        this.dockMaterial = dockMaterial;
        this.clockPill = clockPill;
        this.searchPill = searchPill;
        this.dockRadiusPx = dockRadiusPx;
        this.listener = listener;
    }

    /** The small blurred wallpaper, for a freshly built backdrop. */
    public Bitmap softened() {
        return softened;
    }

    /**
     * Decodes the wallpaper the settings name, off the main thread, and hands it
     * to the listener on the way back. The shared blur covers the screen while
     * the full-size picture is still being read.
     */
    public void apply(Prefs prefs, int tier) {
        final int index = prefs.wallpaper();
        final String uri = prefs.wallpaperUri();
        final int divisor = DeviceTier.wallpaperDivisor(tier);
        final int width = root.getResources().getDisplayMetrics().widthPixels;
        final int height = root.getResources().getDisplayMetrics().heightPixels;
        final String blurKey = BackdropView.key(index, uri);
        String key = blurKey + "@" + width + "x" + height + "/" + tier;
        if (key.equals(wallpaperKey) && wallpaperBitmap != null && !wallpaperBitmap.isRecycled()) {
            listener.onWallpaper(wallpaperBitmap);
            bake();
            return;
        }
        if (key.equals(wallpaperRequest)) {
            // A decode for this wallpaper is already in flight; a second thread
            // would read the same full-size image twice under memory pressure.
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
        Thread decoder = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            Bitmap picture = null;
            Bitmap thumbnail = BackdropView.remembered(blurKey);
            Bitmap half = null;
            try {
                if (index < 0 && uri != null) {
                    // A photo the user picked; fall back to a bundled scene if
                    // the permission was refused or the file has gone.
                    picture = Bitmaps.decodeUri(root.getResources(),
                            root.getContext().getContentResolver(),
                            Uri.parse(uri), width / divisor);
                }
                if (picture == null) {
                    picture = Bitmaps.decodeScene(root.getResources(), Scenes.ALL.get(
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
            root.post(() -> {
                // A newer wallpaper was asked for while this one was decoding, or
                // the screen is gone: this one's memory is better given back.
                if (!wanted.equals(wallpaperRequest) || !root.isAttachedToWindow()) {
                    return;
                }
                wallpaperBitmap = decoded;
                wallpaperKey = wanted;
                // The request has landed, so it is no longer in flight. A decode
                // that came back empty may be asked for again on the next resume.
                wallpaperRequest = "";
                lensSource = source;
                useSoftened(blurred);
                listener.onWallpaper(decoded);
                bake();
            });
        }, "sonora-wallpaper");
        try {
            decoder.start();
        } catch (RuntimeException | OutOfMemoryError e) {
            // The request must not stay marked in flight, or nothing retries it.
            wallpaperRequest = "";
        }
    }

    /**
     * Gives every pane of glass its share of the wallpaper. It runs only when
     * the wallpaper or the layout changes; after that each pane is one textured
     * quad. Bending takes a mesh per pane, so it runs off the main thread. The
     * lightest tier frosts from the blur instead, which makes no bitmap.
     */
    public void bake() {
        final int width = root.getWidth();
        final int height = root.getHeight();
        if (width == 0 || height == 0) {
            return;
        }
        final Rect dock = screenRect(dockBar);
        final Rect clock = screenRect(clockPill);
        final Rect search = screenRect(searchPill);
        final Bitmap source = lensSource;
        boolean bend = source != null && !source.isRecycled()
                && DeviceTier.bentGlass(DeviceTier.get(root.getContext()));
        Bitmap frost = softened;
        String key = (bend ? System.identityHashCode(source) : frost == null ? 0
                : System.identityHashCode(frost)) + "@" + width + "x" + height + dock + clock
                + search;
        if (key.equals(glassKey)) {
            return;
        }
        glassKey = key;
        final int generation = ++glassGeneration;
        if (!bend) {
            dockMaterial.setFrost(frost, width, height, HeroBackgroundView.ZOOM, dock, VEIL);
            clockPill.setFrost(frost, width, height, HeroBackgroundView.ZOOM, clock);
            searchPill.setFrost(frost, width, height, HeroBackgroundView.ZOOM, search);
            return;
        }
        glassBaker.execute(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            Bitmap dockLens = null;
            Bitmap clockLens = null;
            Bitmap searchLens = null;
            try {
                dockLens = GlassLens.bake(source, width, height, HeroBackgroundView.ZOOM, dock,
                        dockRadiusPx, DOCK_LENS_SCALE);
                clockLens = GlassLens.bake(source, width, height, HeroBackgroundView.ZOOM, clock,
                        clock.height() / 2f, 1);
                searchLens = GlassLens.bake(source, width, height, HeroBackgroundView.ZOOM, search,
                        search.height() / 2f, 1);
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // The glass stays dark, which still reads as glass.
            }
            final Bitmap bakedDock = dockLens;
            final Bitmap bakedClock = clockLens;
            final Bitmap bakedSearch = searchLens;
            root.post(() -> {
                if (generation == glassGeneration && root.isAttachedToWindow()) {
                    dockMaterial.setBackdrop(bakedDock, VEIL);
                    clockPill.setBackdrop(bakedClock);
                    searchPill.setBackdrop(bakedSearch);
                }
            });
        });
    }

    /** Lets the screen-sized pictures go. The blur stays: it is a few kilobytes. */
    public void release() {
        wallpaperRequest = "";
        wallpaperBitmap = null;
        wallpaperKey = null;
        lensSource = null;
        glassKey = "";
        glassGeneration++;
        dockMaterial.setBackdrop(null, VEIL);
        clockPill.setBackdrop(null);
        searchPill.setBackdrop(null);
        listener.onReleased();
    }

    /** The worker belongs to this object; it dies with the screen. */
    public void shutdown() {
        glassBaker.shutdownNow();
    }

    private void useSoftened(Bitmap blur) {
        softened = blur;
        listener.onSoftened(blur);
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
}
