package com.mitun.sonora.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;

import java.io.InputStream;

/** Decoding, measuring and drawing of bitmaps, shared by every screen. */
public final class Bitmaps {

    private Bitmaps() {
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
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            // Bounds failed: decoding at full size is the worst case.
            return null;
        }
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
}
