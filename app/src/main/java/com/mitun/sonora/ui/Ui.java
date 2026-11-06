package com.mitun.sonora.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.InputStream;

/** Drawing and platform helpers shared by the launcher and the screen saver. */
public final class Ui {
    public static final int INK = 0xFFF5F6F8;
    // The least prominent ink still has to be readable over the wallpaper, so
    // it is dimmer than the ink but not faint.
    public static final int INK_TERTIARY = 0xB3F5F6F8;
    /** A mark that sits on the glass while it is at rest. */
    public static final int INK_ON_GLASS = 0xE6F5F6F8;
    /** A mark that sits on the glass once it has caught the light. */
    public static final int INK_ON_LIT = 0xFF0A0D12;
    public static final int NIGHT = 0xFF05070B;

    private Ui() {
    }

    public static float dpf(Context context, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    public static int dp(Context context, float dp) {
        return Math.round(dpf(context, dp));
    }

    public static float spf(Context context, float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                context.getResources().getDisplayMetrics());
    }

    public static int alpha(int color, float factor) {
        int a = Math.round(Color.alpha(color) * Math.max(0f, Math.min(1f, factor)));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    /**
     * Blends two colours. A mark that changes with the state of the surface
     * under it is one colour at a time, not one at a time and another after.
     */
    public static int mix(int from, int to, float amount) {
        float t = amount < 0f ? 0f : (amount > 1f ? 1f : amount);
        return Color.argb(
                Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t),
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    /**
     * The highlight on the row a list has selected.
     *
     * A list on a TV is driven by the D-pad, and the list itself holds the
     * focus, so the selection has to be drawn for it.
     */
    public static android.graphics.drawable.Drawable listSelector(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dpf(context, 12));
        // Light at the top, fading down: the same material as the rest of the
        // app, so the row reads as a surface and not as a flat rectangle.
        drawable.setColors(new int[]{0x26FFFFFF, 0x0CFFFFFF});
        drawable.setStroke(Math.max(1, Math.round(dpf(context, 1))), 0x33FFFFFF);
        return drawable;
    }

    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    public static void immersive(Activity activity) {
        immersive(activity.getWindow());
    }

    public static void immersive(Window window) {
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    public static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static boolean isDefaultLauncher(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo info = context.getPackageManager()
                    .resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            return info != null && info.activityInfo != null
                    && context.getPackageName().equals(info.activityInfo.packageName);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void openDefaultLauncherSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
        if (!start(context, intent)) {
            if (!start(context, new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) {
                if (!start(context, new Intent(Settings.ACTION_SETTINGS))) {
                    toast(context, "No launcher settings available");
                }
            }
        }
    }

    /** What the TV's own screen saver setting points at. */
    public enum DreamState { SELECTED, OTHER, STALE, UNKNOWN }

    /**
     * Reads the system screen saver setting. SELECTED means Sonora's dream is
     * the one the TV shows; STALE means it names a package that is not
     * installed, which leaves the TV with a blank screen saver.
     */
    public static DreamState dreamState(Context context) {
        String components;
        try {
            components = Settings.Secure.getString(
                    context.getContentResolver(), "screensaver_components");
        } catch (RuntimeException e) {
            return DreamState.UNKNOWN;
        }
        if (components == null || components.trim().isEmpty()) {
            return DreamState.UNKNOWN;
        }
        // A package that merely contains our name is not us: compare the
        // package token of each component, the part before the '/'.
        for (String component : components.split(",")) {
            int slash = component.indexOf('/');
            String name = (slash >= 0 ? component.substring(0, slash) : component).trim();
            if (context.getPackageName().equals(name)) {
                return DreamState.SELECTED;
            }
        }
        int slash = components.indexOf('/');
        String pkg = (slash >= 0 ? components.substring(0, slash) : components).trim();
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return DreamState.OTHER;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return DreamState.STALE;
        }
    }

    public static void openUsageAccessSettings(Context context) {
        if (!start(context, new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))) {
            toast(context, "No usage access settings available");
        }
    }

    public static void openDreamSettings(Context context) {
        if (!start(context, new Intent(Settings.ACTION_DREAM_SETTINGS))) {
            toast(context, "No screen saver settings available");
        }
    }

    private static boolean start(Context context, Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static Bitmap decodeScene(Resources resources, int resId, int reqWidth, int reqHeight) {
        return decodeScene(resources, resId, reqWidth, reqHeight, Bitmap.Config.ARGB_8888);
    }

    /** As above. RGB_565 halves a photograph at the cost of some banding in a clear sky. */
    public static Bitmap decodeScene(Resources resources, int resId, int reqWidth, int reqHeight,
                                     Bitmap.Config config) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(resources, resId, bounds);
        int sample = 1;
        int reqW = Math.max(1, reqWidth);
        int reqH = Math.max(1, reqHeight);
        while ((bounds.outWidth / (sample * 2)) >= reqW
                && (bounds.outHeight / (sample * 2)) >= reqH) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = config;
        return BitmapFactory.decodeResource(resources, resId, options);
    }

    public static Bitmap decodeUri(Resources resources, android.content.ContentResolver resolver,
                                   Uri uri, int maxDimension) {
        if (uri == null) {
            return null;
        }
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                android.graphics.ImageDecoder.Source source =
                        android.graphics.ImageDecoder.createSource(resolver, uri);
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = resolver.openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    // Bounds failed: decoding at full size is the worst case.
                    return null;
                }
                int max = Math.max(bounds.outWidth, bounds.outHeight);
                int dim = Math.max(1, maxDimension);
                int sample = 1;
                while (max / (sample * 2) >= dim) {
                    sample *= 2;
                }
                final int target = sample;
                return android.graphics.ImageDecoder.decodeBitmap(source,
                        (decoder, info, src) -> {
                            decoder.setTargetSampleSize(target);
                            decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                        });
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    // Bounds failed: decoding at full size is the worst case.
                    return null;
                }
                int max = Math.max(bounds.outWidth, bounds.outHeight);
                int dim = Math.max(1, maxDimension);
                int sample = 1;
                while (max / (sample * 2) >= dim) {
                    sample *= 2;
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                try (InputStream in2 = resolver.openInputStream(uri)) {
                    return BitmapFactory.decodeStream(in2, null, options);
                }
            }
        } catch (Exception | OutOfMemoryError ignored) {
            return null;
        }
    }

    public static Bitmap drawableToBitmap(Drawable drawable, int width, int height, boolean fit) {
        if (drawable == null || width <= 0 || height <= 0) {
            return null;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            int iw = drawable.getIntrinsicWidth();
            int ih = drawable.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) {
                drawable.setBounds(0, 0, width, height);
            } else if (fit) {
                float scale = Math.min(width / (float) iw, height / (float) ih);
                int w = Math.max(1, Math.round(iw * scale));
                int h = Math.max(1, Math.round(ih * scale));
                int left = (width - w) / 2;
                int top = (height - h) / 2;
                drawable.setBounds(left, top, left + w, top + h);
            } else {
                drawable.setBounds(0, 0, width, height);
            }
            drawable.draw(canvas);
            return bitmap;
        } catch (RuntimeException | OutOfMemoryError ignored) {
            return null;
        }
    }

    public static int averageColor(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return 0xFF3A3F4A;
        }
        long r = 0, g = 0, b = 0, n = 0;
        int stepX = Math.max(1, bitmap.getWidth() / 24);
        int stepY = Math.max(1, bitmap.getHeight() / 24);
        for (int y = 0; y < bitmap.getHeight(); y += stepY) {
            for (int x = 0; x < bitmap.getWidth(); x += stepX) {
                int color = bitmap.getPixel(x, y);
                int alpha = Color.alpha(color);
                if (alpha < 96) {
                    continue;
                }
                r += Color.red(color);
                g += Color.green(color);
                b += Color.blue(color);
                n++;
            }
        }
        if (n == 0) {
            return 0xFF3A3F4A;
        }
        return Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }

    public static int tileColor(int accent) {
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.05f);
        hsv[2] = Math.max(0.16f, Math.min(0.42f, hsv[2] * 0.55f));
        return Color.HSVToColor(hsv);
    }

    public static void drawCenterCrop(Canvas canvas, Bitmap bitmap, RectF destination, float zoom,
                                      float offsetX, float offsetY) {
        drawCenterCrop(canvas, bitmap, destination, zoom, offsetX, offsetY, 255);
    }

    /**
     * Allocates on purpose: this runs on the decode threads as well as in onDraw,
     * and a shared rectangle or paint would be overwritten mid-draw. Nothing calls
     * it per frame, since a card rounds its artwork with a shader instead.
     */
    public static void drawCenterCrop(Canvas canvas, Bitmap bitmap, RectF destination, float zoom,
                                      float offsetX, float offsetY, int alpha) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        float dw = destination.width();
        float dh = destination.height();
        float scale = Math.max(dw / bitmap.getWidth(), dh / bitmap.getHeight()) * zoom;
        float sw = bitmap.getWidth() * scale;
        float sh = bitmap.getHeight() * scale;
        float left = destination.left - (sw - dw) / 2f + offsetX;
        float top = destination.top - (sh - dh) / 2f + offsetY;
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setAlpha(alpha);
        canvas.drawBitmap(bitmap, null, new RectF(left, top, left + sw, top + sh), paint);
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.argb(
                Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /**
     * Moves the focus one row up or down inside a vertical list.
     *
     * The panels do this themselves instead of letting the framework search
     * for the next focusable view: a framework search would walk out of the
     * panel and land on the launcher grid behind it, where OK would launch an
     * app the user never picked.
     */
    public static boolean moveListFocus(LinearLayout list, boolean up) {
        if (list == null) {
            return false;
        }
        // getFocusedChild, not findFocus: findFocus asks the parent whether it
        // is focused, which it never is, and always answers no.
        View focused = list.getFocusedChild();
        int index = focused == null ? -1 : list.indexOfChild(focused);
        int count = list.getChildCount();
        for (int i = index + (up ? -1 : 1); i >= 0 && i < count; i += (up ? -1 : 1)) {
            View child = list.getChildAt(i);
            if (child.isFocusable() && child.getVisibility() == View.VISIBLE) {
                child.requestFocus();
                return true;
            }
        }
        return false;
    }
}
