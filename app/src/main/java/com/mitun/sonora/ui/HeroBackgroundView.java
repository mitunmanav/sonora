package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * The full-bleed hero: a still aerial, shown as it is. tvOS puts no scrim or
 * colour wash over its Top Shelf, only a breath of shade behind the top row.
 *
 * The focused app's banner is deliberately not painted here: tvOS shows the
 * app's own content in that space, which a launcher does not have, so a
 * stretched low-resolution banner would only look like a mistake.
 */
public final class HeroBackgroundView extends View {
    /** How far the wallpaper is zoomed, so the glass can bend the same picture. */
    public static final float ZOOM = 1.015f;

    private static final float SCRIM_END = 0.30f;

    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Bitmap wallpaper;
    /** The small blurred picture, shown until the real one has been decoded. */
    private Bitmap placeholder;

    public HeroBackgroundView(Context context) {
        super(context);
    }

    public void setWallpaper(Bitmap bitmap) {
        wallpaper = bitmap;
        invalidate();
    }

    public void setPlaceholder(Bitmap bitmap) {
        placeholder = bitmap;
        if (wallpaper == null) {
            invalidate();
        }
    }

    /** Drops the artwork this view holds. The wallpaper belongs to the caller. */
    public void release() {
        wallpaper = null;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        // Just enough for a white title to survive a bright sky.
        scrimPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x47000000, 0x1F000000, 0x00000000},
                new float[]{0f, 0.14f, SCRIM_END}, Shader.TileMode.CLAMP));
        fallbackPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF0E1825, 0xFF243748, 0xFF070A0F},
                null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (wallpaper != null && !wallpaper.isRecycled()) {
            Ui.drawCenterCrop(canvas, wallpaper, rect, ZOOM, 0, 0);
        } else if (placeholder != null && !placeholder.isRecycled()) {
            Ui.drawCenterCrop(canvas, placeholder, rect, ZOOM, 0, 0);
        } else {
            canvas.drawRect(rect, fallbackPaint);
        }
        // Only where the shade has any strength: the rest of the screen would
        // blend a fully transparent colour for nothing.
        canvas.drawRect(0f, 0f, rect.right, rect.bottom * SCRIM_END, scrimPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        wallpaper = null;
        super.onDetachedFromWindow();
    }
}
