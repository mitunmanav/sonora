package com.mitun.aerial.ui;

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

import com.mitun.aerial.AppEntry;

/**
 * The full-bleed hero. A still aerial is the base; the focused app's banner is
 * reduced to a tiny bitmap and stretched back up, which gives a soft cinematic
 * wash for practically zero runtime cost.
 */
public final class HeroBackgroundView extends View {
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Bitmap wallpaper;
    private Bitmap bannerBlur;
    private int accent = 0xFF3A6B8C;

    public HeroBackgroundView(Context context) {
        super(context);
    }

    public void setWallpaper(Bitmap bitmap) {
        wallpaper = bitmap;
        invalidate();
    }

    public void setEntry(AppEntry entry) {
        if (bannerBlur != null) {
            bannerBlur.recycle();
            bannerBlur = null;
        }
        accent = entry == null ? 0xFF3A6B8C : entry.accent;
        updateTint();
        if (entry != null && entry.banner != null && !entry.banner.isRecycled()) {
            try {
                bannerBlur = Bitmap.createScaledBitmap(entry.banner, 28,
                        Math.max(16, Math.round(28 * entry.banner.getHeight()
                                / (float) entry.banner.getWidth())), true);
            } catch (RuntimeException | OutOfMemoryError ignored) {
                bannerBlur = null;
            }
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        // Darkness at the top for the title and time, and a deep floor for the dock.
        scrimPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x8A000000, 0x1A000000, 0x30000000, 0xD9000000},
                new float[]{0f, 0.30f, 0.58f, 1f}, Shader.TileMode.CLAMP));
        updateTint();
    }

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
            Paint gradient = new Paint();
            gradient.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{0xFF0E1825, 0xFF243748, 0xFF070A0F}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(rect, gradient);
        }
        canvas.drawRect(rect, tintPaint);
        canvas.drawRect(rect, scrimPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (bannerBlur != null) {
            bannerBlur.recycle();
            bannerBlur = null;
        }
        super.onDetachedFromWindow();
    }
}
