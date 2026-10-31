package com.mitun.aerial;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mitun.aerial.ui.Ui;

/** Loads launchable apps and small, pre-scaled artwork off the main thread. */
public final class AppRepository {
    private static final int ICON_MAX = 192;
    private static final int BANNER_W = 480;
    private static final int BANNER_H = 288;

    private AppRepository() {
    }

    public static List<AppEntry> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, AppEntry> byPackage = new LinkedHashMap<>();
        collect(context, pm, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), byPackage);
        collect(context, pm, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), byPackage);
        return new ArrayList<>(byPackage.values());
    }

    private static void collect(Context context, PackageManager pm, Intent intent,
                                Map<String, AppEntry> out) {
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

            Bitmap icon = Ui.drawableToBitmap(iconDrawable, ICON_MAX, ICON_MAX, true);
            int iconW = iconDrawable == null ? 0 : iconDrawable.getIntrinsicWidth();
            int iconH = iconDrawable == null ? 0 : iconDrawable.getIntrinsicHeight();
            float iconRatio = iconH > 0 ? iconW / (float) iconH : 1f;

            Bitmap banner = null;
            if (validBannerRatio(bannerDrawable)) {
                banner = drawWide(bannerDrawable, BANNER_W, BANNER_H);
            } else if (icon != null && iconRatio > 1.25f && iconRatio < 2.4f) {
                banner = Ui.drawableToBitmap(iconDrawable, BANNER_W, BANNER_H, true);
            }

            int accent = Ui.averageColor(icon);
            if (banner != null && (accent == 0 || Color.alpha(accent) == 0)) {
                accent = Ui.averageColor(banner);
            }
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
