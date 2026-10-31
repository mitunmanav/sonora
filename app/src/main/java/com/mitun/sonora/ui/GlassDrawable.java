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
 * A cheap Liquid Glass imitation: a translucent slab with a light top edge, a
 * soft specular pool and a shadowed underside. It uses four canvas draws, no
 * blur, no bitmap sampling.
 *
 * The edge is brightest at the top, where the light catches the material, and
 * fades towards the bottom. That single gradient is what makes a flat shape
 * read as a surface with a thickness rather than a rectangle.
 *
 * Glass only reads as glass when there is something behind it to bend, so the
 * fill is a gradient rather than a flat colour: light where the light falls,
 * dark where the material turns away. Over a photograph that is the whole
 * difference between a grey box and a pane of glass.
 */
public final class GlassDrawable extends Drawable {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint underPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF inner = new RectF();
    private final RectF under = new RectF();
    private final float radius;
    private final float tint;
    private final boolean circle;
    private int alpha = 255;
    private int glow;

    /**
     * @param tint how heavy the material is. A big surface reads thicker than
     *             a small one, and a heavier material separates a structural
     *             region from the content it holds.
     */
    public GlassDrawable(Context context, float radiusDp, float tint) {
        this(context, radiusDp, tint, false);
    }

    private GlassDrawable(Context context, float radiusDp, float tint, boolean circle) {
        radius = Ui.dpf(context, radiusDp);
        this.tint = tint;
        this.circle = circle;
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(Math.max(1f, Ui.dpf(context, 1f)));
    }

    /**
     * A round pane of glass.
     *
     * The shape tvOS and Google TV both use for the small buttons in the top
     * row: a circle, not a pill.
     */
    public static GlassDrawable circle(Context context, float tint) {
        return new GlassDrawable(context, 0f, tint, true);
    }

    /**
     * The light the material catches when it is focused.
     *
     * Only the alpha of the glow changes, so focusing a button costs nothing
     * but a redraw: the gradient itself is built once, with the bounds.
     */
    public void setGlow(int glow) {
        if (this.glow != glow) {
            this.glow = glow;
            glowPaint.setAlpha(Math.round(alpha * (glow / 255f)));
            invalidateSelf();
        }
    }

    @Override
    protected void onBoundsChange(android.graphics.Rect bounds) {
        super.onBoundsChange(bounds);
        rect.set(bounds);
        float stroke = edgePaint.getStrokeWidth();
        inner.set(bounds.left + stroke, bounds.top + stroke,
                bounds.right - stroke, bounds.bottom - stroke);
        // The slab of material: light where the light falls on it, and a little
        // darker where it turns away from the light at the bottom.
        fillPaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[]{adjust(Color.WHITE, 0.20f * tint), adjust(Color.WHITE, 0.07f * tint),
                        adjust(Color.BLACK, 0.13f * tint)},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        // The specular pool: where the sky would be reflected.
        highlightPaint.setShader(new RadialGradient(bounds.width() * 0.28f, bounds.height() * 0.02f,
                bounds.width() * 0.85f, new int[]{adjust(Color.WHITE, 0.20f * tint),
                        adjust(Color.WHITE, 0.04f * tint), Color.TRANSPARENT},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        // The light catches the top edge, so that is where the edge is
        // brightest. It fades out towards the bottom of the material.
        edgePaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[]{adjust(Color.WHITE, 0.72f * tint), adjust(Color.WHITE, 0.26f * tint),
                        adjust(Color.WHITE, 0.12f * tint)},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        // The underside catches no light at all, which is what gives the
        // material its edge.
        under.set(bounds.left, bounds.bottom - Math.max(2f, stroke * 3f),
                bounds.right, bounds.bottom);
        underPaint.setShader(new LinearGradient(0, under.top, 0, under.bottom,
                new int[]{Color.TRANSPARENT, adjust(Color.BLACK, 0.22f * tint)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        // The pool of light the material catches when it is focused. It is
        // drawn over the fill, from the same corner the highlight comes from.
        glowPaint.setShader(new RadialGradient(bounds.width() * 0.32f, bounds.height() * 0.10f,
                bounds.width() * 0.95f, new int[]{0x8CFFFFFF, 0x2EFFFFFF, Color.TRANSPARENT},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        glowPaint.setAlpha(Math.round(alpha * (glow / 255f)));
    }

    private static int adjust(int color, float factor) {
        int a = Math.min(255, Math.round(Color.alpha(color) * factor));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    public void draw(Canvas canvas) {
        float r = circle ? Math.min(rect.width(), rect.height()) / 2f : radius;
        float innerRadius = Math.max(0, r - edgePaint.getStrokeWidth() / 2f);
        canvas.drawRoundRect(rect, r, r, fillPaint);
        canvas.drawRoundRect(inner, innerRadius, innerRadius, highlightPaint);
        if (!circle) {
            // A circle has no flat underside to shade, and the strip would
            // stick out past its corners.
            canvas.drawRect(under, underPaint);
        }
        if (glow > 0) {
            canvas.drawRoundRect(rect, r, r, glowPaint);
        }
        canvas.drawRoundRect(inner, innerRadius, innerRadius, edgePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        fillPaint.setAlpha(alpha);
        edgePaint.setAlpha(alpha);
        highlightPaint.setAlpha(alpha);
        underPaint.setAlpha(alpha);
        glowPaint.setAlpha(Math.round(alpha * (glow / 255f)));
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
