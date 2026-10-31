package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * A cheap Liquid Glass imitation: translucent white fill, a light top edge and
 * a soft specular pool. It uses three canvas draws, no blur, no bitmap sampling.
 *
 * The edge is brightest at the top, where the light catches the material, and
 * fades towards the bottom. That single gradient is what makes a flat shape
 * read as a surface with a thickness rather than a rectangle.
 */
public final class GlassDrawable extends Drawable {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF inner = new RectF();
    private final float radius;
    private final float tint;
    private int alpha = 255;

    /**
     * @param tint how heavy the material is. A big surface reads thicker than
     *             a small one, and a heavier material separates a structural
     *             region from the content it holds.
     */
    public GlassDrawable(Context context, float radiusDp, float tint) {
        radius = Ui.dpf(context, radiusDp);
        this.tint = tint;
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(Math.max(1f, Ui.dpf(context, 0.8f)));
    }

    @Override
    protected void onBoundsChange(android.graphics.Rect bounds) {
        super.onBoundsChange(bounds);
        rect.set(bounds);
        inner.set(bounds.left + edgePaint.getStrokeWidth(), bounds.top + edgePaint.getStrokeWidth(),
                bounds.right - edgePaint.getStrokeWidth(), bounds.bottom - edgePaint.getStrokeWidth());
        fillPaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[]{adjust(Color.WHITE, 0.11f * tint), adjust(Color.WHITE, 0.035f * tint),
                        adjust(Color.BLACK, 0.05f * tint)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        highlightPaint.setShader(new RadialGradient(bounds.width() * 0.25f, bounds.height() * 0.05f,
                bounds.width() * 0.8f, new int[]{adjust(Color.WHITE, 0.10f * tint), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        // The light catches the top edge, so that is where the edge is
        // brightest. It fades out towards the bottom of the material.
        edgePaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[]{adjust(Color.WHITE, 0.34f * tint), adjust(Color.WHITE, 0.10f * tint)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
    }

    private static int adjust(int color, float factor) {
        int a = Math.min(255, Math.round(Color.alpha(color) * factor));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawRoundRect(rect, radius, radius, fillPaint);
        canvas.drawRoundRect(inner, Math.max(0, radius - edgePaint.getStrokeWidth() / 2f),
                Math.max(0, radius - edgePaint.getStrokeWidth() / 2f), highlightPaint);
        canvas.drawRoundRect(inner, Math.max(0, radius - edgePaint.getStrokeWidth() / 2f),
                Math.max(0, radius - edgePaint.getStrokeWidth() / 2f), edgePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        fillPaint.setAlpha(alpha);
        edgePaint.setAlpha(alpha);
        highlightPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}
