package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import com.mitun.sonora.AppEntry;

/**
 * The full-bleed hero: a still aerial with a wash of the focused app's own
 * colour over it.
 *
 * The focused app's banner is deliberately not painted here: tvOS shows the
 * app's own content in that space, which a launcher does not have, so a
 * stretched low-resolution banner would only look like a mistake.
 */
public final class HeroBackgroundView extends View {
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Bitmap wallpaper;
    private int accent = 0xFF3A6B8C;

    public HeroBackgroundView(Context context) {
        super(context);
    }

    public void setWallpaper(Bitmap bitmap) {
        wallpaper = bitmap;
        invalidate();
    }

    /** Drops the artwork this view holds. The wallpaper belongs to the caller. */
    public void release() {
        wallpaper = null;
        invalidate();
    }

    public void setEntry(AppEntry entry) {
        accent = entry == null ? 0xFF3A6B8C : entry.accent;
        updateTint();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        // Darkness at the top for the title and the buttons, and a deep floor
        // for the dock.
        //
        // The top band is strong because the chrome has to read over any
        // wallpaper: Google TV puts a gradient scrim under its system bar
        // for the same reason, and a white mark over a bright sky does not
        // survive without one.
        scrimPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xDB000000, 0xA6000000, 0x52000000, 0x1A000000, 0x30000000, 0xD9000000},
                new float[]{0f, 0.05f, 0.13f, 0.30f, 0.58f, 1f}, Shader.TileMode.CLAMP));
        fallbackPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF0E1825, 0xFF243748, 0xFF070A0F},
                null, Shader.TileMode.CLAMP));
        updateTint();
    }

    /** The wash of colour changes with the focused app, so it is rebuilt there. */
    private void updateTint() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        tintPaint.setShader(new RadialGradient(w * 0.34f, h * 0.36f, w * 0.62f,
                new int[]{Ui.alpha(accent, 0.13f), 0x00000000},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawColor(Color.BLACK);
        if (wallpaper != null && !wallpaper.isRecycled()) {
            Ui.drawCenterCrop(canvas, wallpaper, rect, 1.015f, 0, 0);
        } else {
            canvas.drawRect(rect, fallbackPaint);
        }
        canvas.drawRect(rect, tintPaint);
        canvas.drawRect(rect, scrimPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        wallpaper = null;
        super.onDetachedFromWindow();
    }
}
