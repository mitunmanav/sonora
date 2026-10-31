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

import com.mitun.sonora.ui.Ui;

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

    private AppRepository() {
    }

    /** The cached list, loading it on a background thread if it is missing. */
    public static List<AppEntry> get(Context context, int tileWidthPx) {
        List<AppEntry> local = cached;
        return local != null ? local : load(context, tileWidthPx);
    }

    /** Rebuilds the list and the artwork. Call off the main thread. */
    public static List<AppEntry> load(Context context, int tileWidthPx) {
        PackageManager pm = context.getPackageManager();
        Map<String, AppEntry> byPackage = new LinkedHashMap<>();
        collect(context, pm, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), byPackage, tileWidthPx);
        collect(context, pm, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), byPackage, tileWidthPx);
        List<AppEntry> out = new ArrayList<>(byPackage.values());
        cached = out;
        return out;
    }

    public static void invalidate() {
        cached = null;
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
            if (pm.getLeanbackLaunchIntentForPackage(pkg) == null
                    && pm.getLaunchIntentForPackage(pkg) == null) {
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

            Drawable iconDrawable = null;
            Drawable bannerDrawable = null;
            try {
                iconDrawable = ai.loadIcon(pm);
                if (iconDrawable == null) {
                    iconDrawable = app.loadIcon(pm);
                }
            } catch (RuntimeException ignored) {
            }
            try {
                bannerDrawable = ai.loadBanner(pm);
                if (bannerDrawable == null) {
                    bannerDrawable = app.loadBanner(pm);
                }
            } catch (RuntimeException ignored) {
            }

            // Artwork is stored at the size the tile actually draws at, so a
            // 1080p stick does not carry 4K pictures around in memory.
            boolean lowRam = SystemInfo.get(context).lowRam;
            int bannerW = Math.max(160, Math.min(BANNER_MAX_W, tileWidthPx));
            if (lowRam) {
                bannerW = Math.max(120, bannerW / 2);
            }
            int bannerH = Math.max(1, Math.round(bannerW * 0.6f));
            int iconMax = lowRam ? ICON_MAX / 2 : ICON_MAX;

            Bitmap icon = Ui.drawableToBitmap(iconDrawable, iconMax, iconMax, true);
            int iconW = iconDrawable == null ? 0 : iconDrawable.getIntrinsicWidth();
            int iconH = iconDrawable == null ? 0 : iconDrawable.getIntrinsicHeight();
            float iconRatio = iconH > 0 ? iconW / (float) iconH : 1f;

            Bitmap banner = null;
            if (validBannerRatio(bannerDrawable)) {
                banner = drawWide(bannerDrawable, bannerW, bannerH);
            } else if (icon != null && iconRatio > 1.25f && iconRatio < 2.4f) {
                banner = Ui.drawableToBitmap(iconDrawable, bannerW, bannerH, true);
            }

            int accent = icon != null ? Ui.averageColor(icon) : 0;
            if (accent == 0 && banner != null) {
                accent = Ui.averageColor(banner);
            }
            if (accent == 0) {
                accent = 0xFF3A3F4A;
            }
            // A card draws its banner when there is one and the icon only
            // otherwise, so keeping both doubles the memory for nothing.
            Bitmap keepIcon = banner == null ? icon : null;
            out.put(pkg, new AppEntry(loadedLabel.toString(), pkg,
                    new ComponentName(ai.packageName, ai.name),
                    keepIcon, banner, accent, false));
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

    private static Bitmap drawWide(Drawable drawable, int maxW, int maxH) {
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        if (w <= 0 || h <= 0) {
            return Ui.drawableToBitmap(drawable, maxW, maxH, true);
        }
        float scale = Math.min(maxW / (float) w, maxH / (float) h);
        int tw = Math.max(1, Math.round(w * scale));
        int th = Math.max(1, Math.round(h * scale));
        try {
            Bitmap bitmap = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, tw, th);
            drawable.draw(canvas);
            return bitmap;
        } catch (RuntimeException | OutOfMemoryError ignored) {
            return null;
        }
    }
}
