package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * A cheap Liquid Glass imitation: a translucent slab with a light top edge, an
 * even top sheen and a shadowed underside. It uses a handful of canvas draws,
 * no blur, no bitmap sampling.
 *
 * The material has two states, and the difference between them is the point.
 *
 * <ul>
 *   <li>At rest it is a <b>dark lens</b>. Glass gathers the light around it
 *       and dims what is behind it, so the inside of the lens is smooth and
 *       darker than the wallpaper. That smooth ground is what lets a white
 *       mark on it read over any picture, and the bright rim around it is
 *       what makes the shape visible.</li>
 *   <li>When it is focused the lens <b>catches the light</b>: a soft white
 *       layer comes over the dark one, the rim fades into it, and the mark
 *       on it turns dark so it never fights the surface it sits on. Google
 *       TV's launcher does exactly this, which is why the focus state there
 *       cannot be missed.</li>
 * </ul>
 *
 * The colours are set once, with the bounds. Only the alpha changes while
 * the focus moves, so focusing a button costs a redraw and nothing else.
 */
public final class GlassDrawable extends Drawable {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint underPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint litPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF inner = new RectF();
    private final RectF under = new RectF();
    private final Path shape = new Path();
    private final float radius;
    private final float tint;
    private final boolean circle;
    private int alpha = 255;
    private float focus;

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
     * How much light the material has caught: nothing at rest, all of it when
     * the control has the focus.
     *
     * Only the alpha of each layer changes, so this is a redraw rather than a
     * new shader.
     */
    public void setFocus(float focus) {
        float f = focus < 0f ? 0f : (focus > 1f ? 1f : focus);
        if (f == this.focus) {
            return;
        }
        this.focus = f;
        applyAlphas();
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(android.graphics.Rect bounds) {
        super.onBoundsChange(bounds);
        rect.set(bounds);
        float stroke = edgePaint.getStrokeWidth();
        inner.set(bounds.left + stroke, bounds.top + stroke,
                bounds.right - stroke, bounds.bottom - stroke);
        // The lens at rest: darker than what is behind it, so a mark on it
        // has a smooth ground to stand on.
        fillPaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[]{adjust(Color.BLACK, 0.56f * tint), adjust(Color.BLACK, 0.44f * tint),
                        adjust(Color.BLACK, 0.54f * tint)},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        // The light that catches the top of the material. It runs the whole
        // width: one even wash, not a spotlight, so the dock reads as a slab
        // of glass rather than a lit patch.
        highlightPaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[]{adjust(Color.WHITE, 0.13f * tint), adjust(Color.WHITE, 0.035f * tint),
                        Color.TRANSPARENT},
                new float[]{0f, 0.16f, 0.62f}, Shader.TileMode.CLAMP));
        // The light catches the top edge, so that is where the rim is
        // brightest. It fades out towards the bottom of the material.
        edgePaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[]{adjust(Color.WHITE, 0.82f * tint), adjust(Color.WHITE, 0.24f * tint),
                        adjust(Color.WHITE, 0.08f * tint)},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        // The underside catches no light at all, which is what gives the
        // material its edge.
        under.set(bounds.left, bounds.bottom - Math.max(2f, stroke * 3f),
                bounds.right, bounds.bottom);
        underPaint.setShader(new LinearGradient(0, under.top, 0, under.bottom,
                new int[]{Color.TRANSPARENT, adjust(Color.BLACK, 0.40f * tint)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        // The same lens with the light on it.
        litPaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[]{adjust(Color.WHITE, 0.95f), adjust(Color.WHITE, 0.86f),
                        adjust(Color.WHITE, 0.66f)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        applyAlphas();
    }

    private void applyAlphas() {
        fillPaint.setAlpha(alpha);
        // The rim of a lit lens is no longer a bright edge: the lens itself is
        // the bright thing now.
        edgePaint.setAlpha(Math.round(alpha * (1f - 0.78f * focus)));
        // The top sheen stays on a lit lens too: it is what keeps the surface
        // reading as glass once it has turned white.
        highlightPaint.setAlpha(Math.round(alpha * (1f - 0.30f * focus)));
        underPaint.setAlpha(alpha);
        // The lit layer fades in over the dark one.
        litPaint.setAlpha(Math.round(alpha * 0.96f * focus));
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
        if (focus > 0f) {
            canvas.drawRoundRect(rect, r, r, litPaint);
        }
        // The shadowed underside, cut to the shape so it does not stick out
        // past the corners.
        shape.rewind();
        shape.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(shape);
        canvas.drawRect(under, underPaint);
        canvas.restore();
        canvas.drawRoundRect(inner, innerRadius, innerRadius, highlightPaint);
        canvas.drawRoundRect(inner, innerRadius, innerRadius, edgePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        applyAlphas();
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
