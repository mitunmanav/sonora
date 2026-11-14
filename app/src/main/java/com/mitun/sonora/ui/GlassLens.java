package com.mitun.sonora.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Bends a slice of the wallpaper the way a slab of glass would.
 *
 * tvOS 26 refracts what is behind its dock. Android has no backdrop filter
 * before API 31, and a live one would cost a pass every frame, so the bend is
 * baked once into a small bitmap: the wallpaper does not move, so neither does
 * what is seen through the glass. Drawing it afterwards is one textured quad.
 */
public final class GlassLens {
    /** How wide the bent band along the rim is, as a fraction of the short side. */
    private static final float BAND = 0.45f;
    /** How far the rim pushes the picture, as a fraction of the band. */
    private static final float PUSH = 0.42f;
    /** The size of the ripple in the glass, as a fraction of the short side. */
    private static final float RIPPLE = 0.028f;
    /** The distance between two mesh points, in pixels of the baked bitmap. */
    private static final int CELL = 8;

    private GlassLens() {
    }

    /**
     * Pure: how far a point is pushed towards the rim, given its distance from
     * the rim. Greatest at the rim and gone at the inner edge of the band.
     */
    static float rimOffset(float distance, float band, float push) {
        if (band <= 0f || distance >= band) {
            return 0f;
        }
        float t = 1f - Math.max(0f, distance) / band;
        return push * t * t;
    }

    /**
     * @param wallpaper the picture on screen
     * @param region    the part of the screen the glass covers, in screen pixels
     * @param scale     how many screen pixels one baked pixel stands for
     * @return the bent slice, or null when there is nothing to bake
     */
    public static Bitmap bake(Bitmap wallpaper, int screenWidth, int screenHeight, float zoom,
                              Rect region, float radiusPx, int scale) {
        if (wallpaper == null || wallpaper.isRecycled() || region.isEmpty()) {
            return null;
        }
        scale = Math.max(1, scale);
        int w = Math.max(1, region.width() / scale);
        int h = Math.max(1, region.height() / scale);
        Bitmap flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas flatCanvas = new Canvas(flat);
        flatCanvas.scale(1f / scale, 1f / scale);
        flatCanvas.translate(-region.left, -region.top);
        Bitmaps.drawCenterCrop(flatCanvas, wallpaper, new RectF(0, 0, screenWidth, screenHeight),
                zoom, 0f, 0f);

        Bitmap bent = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bent);
        // The flat slice goes underneath, so a ripple that pulls the mesh away
        // from the edge shows the picture rather than a hole.
        Paint smooth = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(flat, 0f, 0f, smooth);
        int columns = Math.max(4, Math.min(64, w / CELL));
        int rows = Math.max(4, Math.min(16, h / CELL));
        canvas.drawBitmapMesh(flat, columns, rows,
                mesh(w, h, columns, rows, radiusPx / scale), 0, null, 0, smooth);
        flat.recycle();
        return bent;
    }

    private static float[] mesh(int w, int h, int columns, int rows, float radius) {
        float shortSide = Math.min(w, h);
        float band = shortSide * BAND;
        float push = band * PUSH;
        float ripple = shortSide * RIPPLE;
        float wave = (float) (Math.PI * 2f) / (shortSide * 1.3f);
        float r = Math.min(radius, shortSide / 2f);
        float halfW = w / 2f;
        float halfH = h / 2f;
        float[] points = new float[(columns + 1) * (rows + 1) * 2];
        int i = 0;
        for (int row = 0; row <= rows; row++) {
            float y = h * row / (float) rows;
            for (int column = 0; column <= columns; column++) {
                float x = w * column / (float) columns;
                // The distance to the rim of a rounded rectangle, and which way
                // the rim lies from here.
                float qx = Math.abs(x - halfW) - (halfW - r);
                float qy = Math.abs(y - halfH) - (halfH - r);
                float outX = Math.max(qx, 0f);
                float outY = Math.max(qy, 0f);
                float corner = (float) Math.hypot(outX, outY);
                float distance = r - corner - Math.min(Math.max(qx, qy), 0f);
                float nx;
                float ny;
                if (corner > 0f) {
                    nx = outX / corner;
                    ny = outY / corner;
                } else if (qx > qy) {
                    nx = 1f;
                    ny = 0f;
                } else {
                    nx = 0f;
                    ny = 1f;
                }
                float offset = rimOffset(distance, band, push);
                points[i++] = x + Math.signum(x - halfW) * nx * offset
                        + ripple * (float) Math.sin(y * wave * 1.7f + x * wave * 0.5f);
                points[i++] = y + Math.signum(y - halfH) * ny * offset
                        + ripple * (float) Math.sin(x * wave + y * wave * 0.4f);
            }
        }
        return points;
    }
}
