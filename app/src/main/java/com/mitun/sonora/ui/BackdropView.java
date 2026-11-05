package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.view.View;

import com.mitun.sonora.Prefs;
import com.mitun.sonora.Scenes;

/**
 * The wallpaper behind the tools, blurred and darkened the way tvOS Settings
 * sits on the desktop. It is decoded at thumbnail size and stretched, which is
 * the cheapest blur there is: a few kilobytes instead of another screen-sized
 * bitmap.
 */
public final class BackdropView extends View {
    private static final int WIDTH = 96;
    private static final int HEIGHT = 54;

    /**
     * The last blur made, shared across screens. Opening Settings must not decode
     * a JPEG on the main thread when the home screen has already done the work.
     */
    private static String softKey;
    private static Bitmap softBitmap;

    private Bitmap bitmap;
    private final Paint scrim = new Paint();
    private final RectF destination = new RectF();

    public BackdropView(Context context) {
        super(context);
        Prefs prefs = new Prefs(context);
        String uri = prefs.wallpaperUri();
        int index = prefs.wallpaper();
        String key = key(index, uri);
        bitmap = remembered(key);
        if (bitmap == null) {
            Bitmap decoded;
            if (index < 0 && uri != null) {
                decoded = Ui.decodeUri(getResources(), context.getContentResolver(),
                        Uri.parse(uri), WIDTH);
            } else {
                decoded = Ui.decodeScene(getResources(),
                        Scenes.ALL.get(Scenes.safeIndex(index)).res, WIDTH, HEIGHT);
            }
            bitmap = soften(decoded);
            if (decoded != null) {
                decoded.recycle();
            }
            remember(key, bitmap);
        }
        scrim.setColor(Theme.backdropScrim());
    }

    /** What identifies a wallpaper in the shared blur. */
    public static String key(int wallpaper, String uri) {
        return wallpaper < 0 && uri != null ? "u:" + uri : "s:" + Scenes.safeIndex(wallpaper);
    }

    public static synchronized Bitmap remembered(String key) {
        return key.equals(softKey) && softBitmap != null && !softBitmap.isRecycled()
                ? softBitmap : null;
    }

    public static synchronized void remember(String key, Bitmap blurred) {
        softKey = key;
        softBitmap = blurred;
    }

    /** A backdrop that is handed its blur, so the home screen makes it only once. */
    public BackdropView(Context context, Bitmap blurred) {
        super(context);
        scrim.setColor(Theme.backdropScrim());
        bitmap = blurred;
    }

    /** The small blurred picture, for glass that frosts what is behind it. */
    public Bitmap softened() {
        return bitmap;
    }

    public void setSoftened(Bitmap blurred) {
        bitmap = blurred;
        invalidate();
    }

    /**
     * Its two draws never overlap in a way alpha could show, and without this a
     * fade costs an offscreen buffer the size of the screen on every frame.
     */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /**
     * Blurs the thumbnail before it is stretched. Stretching a sharp thumbnail
     * shows its pixels as a mosaic; a smooth one stretches into a smooth blur.
     */
    public static Bitmap soften(Bitmap decoded) {
        if (decoded == null) {
            return null;
        }
        // The decoder only samples by powers of two and hands back an immutable
        // bitmap, so the thumbnail is drawn at its real size first.
        int w = WIDTH;
        int h = HEIGHT;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Ui.drawCenterCrop(new Canvas(bitmap), decoded, new RectF(0f, 0f, w, h), 1f, 0f, 0f);
        int[] in = new int[w * h];
        int[] out = new int[w * h];
        bitmap.getPixels(in, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int r = 0;
                    int g = 0;
                    int b = 0;
                    for (int dy = -2; dy <= 2; dy++) {
                        int row = Math.max(0, Math.min(h - 1, y + dy)) * w;
                        for (int dx = -2; dx <= 2; dx++) {
                            int p = in[row + Math.max(0, Math.min(w - 1, x + dx))];
                            r += (p >> 16) & 0xFF;
                            g += (p >> 8) & 0xFF;
                            b += p & 0xFF;
                        }
                    }
                    out[y * w + x] = 0xFF000000 | (r / 25) << 16 | (g / 25) << 8 | b / 25;
                }
            }
            int[] swap = in;
            in = out;
            out = swap;
        }
        bitmap.setPixels(in, 0, w, 0, 0, w, h);
        return bitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (bitmap != null && !bitmap.isRecycled()) {
            destination.set(0f, 0f, width, height);
            Ui.drawCenterCrop(canvas, bitmap, destination, 1f, 0f, 0f);
        } else {
            canvas.drawColor(Color.BLACK);
        }
        canvas.drawRect(0f, 0f, width, height, scrim);
    }
}
