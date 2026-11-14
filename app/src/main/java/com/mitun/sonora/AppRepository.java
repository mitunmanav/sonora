package com.mitun.sonora;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mitun.sonora.ui.Bitmaps;

/**
 * Loads launchable apps and small, pre-scaled artwork off the main thread.
 *
 * The result is cached: TVs kill background processes and the transition to the
 * launcher is slow if every resume re-queries PackageManager and re-decodes
 * every icon. Call {@link #invalidate()} when the set of apps may have changed.
 */
public final class AppRepository {
    private static final int ICON_MAX = 192;
    /** Artwork wider than this is upscaled rather than stored, to bound memory. */
    private static final int BANNER_MAX_W = 480;

    private static volatile List<AppEntry> cached;
    /** The tile width the cached artwork was stored at. */
    private static volatile int cachedWidth = -1;

    private AppRepository() {
    }

    private static final Object LOAD_LOCK = new Object();

    /**
     * The cached list, loading it on a background thread if it is missing.
     *
     * @param tileWidthPx the width the artwork should be stored at, or 0 when
     *                    the caller only wants the names and does not care
     *                    what size the artwork is
     */
    public static List<AppEntry> get(Context context, int tileWidthPx) {
        watch(context);
        List<AppEntry> local = cached;
        if (local == null) {
            return load(context, tileWidthPx);
        }
        if (tileWidthPx > 0 && cachedWidth != tileWidthPx) {
            // The artwork was stored for a different tile, so it is the wrong
            // size now. Reload rather than draw it stretched.
            return load(context, tileWidthPx);
        }
        return local;
    }

    /** Rebuilds the list and the artwork. Call off the main thread. */
    public static List<AppEntry> load(Context context, int tileWidthPx) {
        synchronized (LOAD_LOCK) {
            if (cached != null && (tileWidthPx <= 0 || cachedWidth == tileWidthPx)) {
                return cached;
            }
            PackageManager pm = context.getPackageManager();
            Map<String, AppEntry> byPackage = new LinkedHashMap<>();
            collect(context, pm, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), byPackage, tileWidthPx);
            collect(context, pm, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), byPackage, tileWidthPx);
            List<AppEntry> out = new ArrayList<>(byPackage.values());
            cached = out;
            cachedWidth = tileWidthPx;
            return out;
        }
    }

    public static void invalidate() {
        synchronized (LOAD_LOCK) {
            cached = null;
            cachedWidth = -1;
        }
    }

    private static volatile boolean watching;

    /**
     * The list drops itself when the epoch is bumped or the tier choice moves
     * the stored-artwork size, so callers only bump the epoch.
     */
    private static void watch(Context context) {
        if (!watching) {
            synchronized (AppRepository.class) {
                if (!watching) {
                    watching = true;
                    Prefs.listen(context, (prefs, key) -> {
                        if (key == null || Prefs.KEY_APPS_EPOCH.equals(key) || Prefs.KEY_PERFORMANCE.equals(key)) {
                            invalidate();
                        }
                    });
                }
            }
        }
    }

    private static void collect(Context context, PackageManager pm, Intent intent,
                                Map<String, AppEntry> out, int tileWidthPx) {
        List<ResolveInfo> resolved;
        try {
            resolved = pm.queryIntentActivities(intent, 0);
        } catch (RuntimeException e) {
            return;
        }
        for (ResolveInfo ri : resolved) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || ai.applicationInfo == null) {
                continue;
            }
            String pkg = ai.packageName;
            if (context.getPackageName().equals(pkg) || out.containsKey(pkg)) {
                continue;
            }
            ApplicationInfo app = ai.applicationInfo;
            boolean launchable;
            try {
                launchable = pm.getLeanbackLaunchIntentForPackage(pkg) != null
                        || pm.getLaunchIntentForPackage(pkg) != null;
            } catch (RuntimeException e) {
                // One package the system will not answer for must not cost
                // the user the whole list.
                continue;
            }
            if (!launchable) {
                continue;
            }
            CharSequence loadedLabel = null;
            try {
                loadedLabel = ai.loadLabel(pm);
            } catch (RuntimeException ignored) {
            }
            if (loadedLabel == null || loadedLabel.length() == 0) {
                loadedLabel = pkg;
            }

            // Callers that only want names (the settings lists) ask with a
            // zero tile width. Decoding artwork for them would be pure waste.
            boolean wantArtwork = tileWidthPx > 0;
            Bitmap icon = null;
            Bitmap banner = null;
            if (wantArtwork) {
                Drawable bannerDrawable = null;
                try {
                    bannerDrawable = ai.loadBanner(pm);
                    if (bannerDrawable == null) {
                        bannerDrawable = app.loadBanner(pm);
                    }
                } catch (RuntimeException ignored) {
                }

                // Artwork is stored at the size the tile actually draws at, so
                // a 1080p stick does not carry 4K pictures around in memory.
                boolean low = DeviceTier.get(context) == DeviceTier.LOW;
                int bannerW = Math.max(120, Math.min(BANNER_MAX_W, tileWidthPx));
                int bannerH = Math.max(1, Math.round(bannerW * 0.6f));
                int iconMax = low ? ICON_MAX / 2 : ICON_MAX;

                if (validBannerRatio(bannerDrawable)) {
                    banner = drawWide(bannerDrawable, bannerW, bannerH, low);
                }
                if (banner == null) {
                    Drawable iconDrawable = null;
                    try {
                        iconDrawable = ai.loadIcon(pm);
                        if (iconDrawable == null) {
                            iconDrawable = app.loadIcon(pm);
                        }
                    } catch (RuntimeException ignored) {
                    }
                    icon = Bitmaps.drawableToBitmap(iconDrawable, iconMax, iconMax, true);
                    int iconW = iconDrawable == null ? 0 : iconDrawable.getIntrinsicWidth();
                    int iconH = iconDrawable == null ? 0 : iconDrawable.getIntrinsicHeight();
                    float iconRatio = iconH > 0 ? iconW / (float) iconH : 1f;
                    if (icon != null && iconRatio > 1.25f && iconRatio < 2.4f) {
                        // A wide icon is the tile's artwork; keeping the square
                        // copy as well would double the memory for nothing.
                        banner = Bitmaps.drawableToBitmap(iconDrawable, bannerW, bannerH, true);
                        icon = null;
                    }
                }
            }

            int accent = banner != null ? Bitmaps.averageColor(banner)
                    : icon != null ? Bitmaps.averageColor(icon) : 0;
            if (accent == 0) {
                accent = 0xFF3A3F4A;
            }
            out.put(pkg, new AppEntry(loadedLabel.toString(), pkg,
                    new ComponentName(ai.packageName, ai.name),
                    icon, banner, accent, false));
        }
    }

    private static boolean validBannerRatio(Drawable drawable) {
        if (drawable == null) {
            return false;
        }
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        if (w <= 0 || h <= 0) {
            return false;
        }
        float ratio = w / (float) h;
        return ratio >= 1.25f && ratio <= 2.6f;
    }

    private static Bitmap drawWide(Drawable drawable, int maxW, int maxH, boolean low) {
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        if (w <= 0 || h <= 0) {
            return Bitmaps.drawableToBitmap(drawable, maxW, maxH, true);
        }
        float scale = Math.min(maxW / (float) w, maxH / (float) h);
        int tw = Math.max(1, Math.round(w * scale));
        int th = Math.max(1, Math.round(h * scale));
        try {
            // TV banners are opaque photographs, so 16 bits a pixel is enough
            // and halves the biggest cache in the app. A banner with real
            // transparency keeps its alpha.
            Bitmap.Config config = drawable.getOpacity() == android.graphics.PixelFormat.OPAQUE
                    ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
            Bitmap bitmap = Bitmap.createBitmap(tw, th, config);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, tw, th);
            drawable.draw(canvas);
            // Most banners say they may be transparent and then are not. On the
            // lightest tier that is worth checking, for half the memory.
            if (low && config == Bitmap.Config.ARGB_8888 && fullyOpaque(bitmap)) {
                Bitmap small = bitmap.copy(Bitmap.Config.RGB_565, false);
                if (small != null) {
                    bitmap.recycle();
                    return small;
                }
            }
            return bitmap;
        } catch (RuntimeException | OutOfMemoryError ignored) {
            return null;
        }
    }

    private static boolean fullyOpaque(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int pixel : pixels) {
            if (pixel >>> 24 != 0xFF) {
                return false;
            }
        }
        return true;
    }
}
