package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
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
    private final Paint litPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backdropPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint veilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap backdrop;
    private float lens = 1f;
    private final RectF rect = new RectF();
    private final RectF inner = new RectF();
    /** The veil and the sheen are already in the backdrop, so they cost no draw. */
    private boolean bakedOver;
    /** Maps the backdrop onto this pane when it is a slice of a bigger picture. */
    private Matrix sliceMatrix;
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

    /**
     * Shows the wallpaper through the glass instead of the dark lens.
     *
     * @param lens what {@link GlassLens} baked for this pane, or null for the
     *             dark lens
     * @param veil the shade over it, so a mark on the glass stays readable
     */
    public void setBackdrop(Bitmap lens, int veil) {
        backdrop = lens;
        sliceMatrix = null;
        veilPaint.setColor(veil);
        bakedOver = false;
        if (lens != null && lens.isMutable() && !rect.isEmpty()) {
            bakeOver(lens, veil);
        }
        applyBackdropShader();
        applyAlphas();
        invalidateSelf();
    }

    /**
     * Frosts the pane with its share of a picture the size of the screen, usually
     * the small blurred one. It samples one shared texture, so a pane costs no
     * bitmap of its own.
     *
     * @param region where this pane sits on the screen
     */
    public void setFrost(Bitmap picture, int screenWidth, int screenHeight, float zoom,
                         android.graphics.Rect region, int veil) {
        backdrop = picture;
        veilPaint.setColor(veil);
        bakedOver = false;
        sliceMatrix = null;
        if (picture != null) {
            float scale = Math.max(screenWidth / (float) picture.getWidth(),
                    screenHeight / (float) picture.getHeight()) * zoom;
            sliceMatrix = new Matrix();
            sliceMatrix.setScale(scale, scale);
            sliceMatrix.postTranslate(
                    (screenWidth - picture.getWidth() * scale) / 2f - region.left,
                    (screenHeight - picture.getHeight() * scale) / 2f - region.top);
        }
        applyBackdropShader();
        applyAlphas();
        invalidateSelf();
    }

    /**
     * Paints the veil and the sheen into the pane's own bitmap, so at rest the
     * whole glass is one textured quad and a stroke rather than four blends over
     * a fifth of the screen.
     */
    private void bakeOver(Bitmap lens, int veil) {
        Canvas canvas = new Canvas(lens);
        canvas.drawColor(veil);
        canvas.scale(lens.getWidth() / rect.width(), lens.getHeight() / rect.height());
        canvas.translate(-rect.left, -rect.top);
        highlightPaint.setAlpha(255);
        canvas.drawRect(rect, highlightPaint);
        bakedOver = true;
    }

    /**
     * How much of the bent wallpaper shows, from none to all. The dock lets it
     * go as the grid opens over the blurred wallpaper. Only an alpha changes,
     * so this is a redraw and nothing else.
     */
    public void setLens(float lens) {
        float clamped = Math.max(0f, Math.min(1f, lens));
        if (clamped == this.lens) {
            return;
        }
        this.lens = clamped;
        applyAlphas();
        invalidateSelf();
    }

    private void applyBackdropShader() {
        if (backdrop == null || rect.isEmpty()) {
            backdropPaint.setShader(null);
            return;
        }
        BitmapShader shader = new BitmapShader(backdrop, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP);
        Matrix matrix = sliceMatrix;
        if (matrix == null) {
            matrix = new Matrix();
            matrix.setScale(rect.width() / backdrop.getWidth(),
                    rect.height() / backdrop.getHeight());
            matrix.postTranslate(rect.left, rect.top);
        }
        shader.setLocalMatrix(matrix);
        backdropPaint.setShader(shader);
    }

    @Override
    protected void onBoundsChange(android.graphics.Rect bounds) {
        super.onBoundsChange(bounds);
        rect.set(bounds);
        applyBackdropShader();
        float stroke = edgePaint.getStrokeWidth();
        inner.set(bounds.left + stroke, bounds.top + stroke,
                bounds.right - stroke, bounds.bottom - stroke);
        // The lens at rest: darker than what is behind it, so a mark on it
        // has a smooth ground to stand on.
        fillPaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[]{adjust(Color.BLACK, 0.56f * tint), adjust(Color.BLACK, 0.44f * tint),
                        adjust(Color.BLACK, 0.54f * tint)},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        // The light that catches the top of the material runs the whole width, one
        // even wash. The same gradient ends in the underside, which catches no light
        // and gives the material its edge, so there is no clip and no extra pass.
        float underAt = Math.max(0.62f,
                1f - Math.max(2f, stroke * 3f) / Math.max(1, bounds.height()));
        highlightPaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[]{adjust(Color.WHITE, 0.13f * tint), adjust(Color.WHITE, 0.035f * tint),
                        Color.TRANSPARENT, Color.TRANSPARENT, adjust(Color.BLACK, 0.40f * tint)},
                new float[]{0f, 0.16f, 0.62f, underAt, 1f}, Shader.TileMode.CLAMP));
        // The light catches the top edge, so that is where the rim is
        // brightest. It fades out towards the bottom of the material.
        edgePaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[]{adjust(Color.WHITE, 0.72f * tint), adjust(Color.WHITE, 0.28f * tint),
                        adjust(Color.WHITE, 0.24f * tint)},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        // The same lens with the light on it. The fill is almost opaque white:
        // Apple's focused control is a white surface with dark ink, not a grey
        // gradient.
        litPaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[]{adjust(Color.WHITE, 0.99f), adjust(Color.WHITE, 0.97f),
                        adjust(Color.WHITE, 0.93f)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        applyAlphas();
    }

    private void applyAlphas() {
        fillPaint.setAlpha(alpha);
        backdropPaint.setAlpha(Math.round(alpha * lens));
        // The rim of a lit lens is no longer a bright edge: the lens itself is
        // the bright thing now.
        edgePaint.setAlpha(Math.round(alpha * (1f - 0.78f * focus)));
        // The top sheen stays on a lit lens too: it is what keeps the surface
        // reading as glass once it has turned white.
        float sheen = alpha * (1f - 0.30f * focus);
        // Once the sheen is baked in, it only has to be drawn as the backdrop fades.
        highlightPaint.setAlpha(Math.round(bakedOver ? sheen * (1f - lens) : sheen));
        // The lit layer fades in over the dark one.
        litPaint.setAlpha(Math.round(alpha * 0.96f * focus));
    }

    private static int adjust(int color, float factor) {
        int a = Math.min(255, Math.round(Color.alpha(color) * factor));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    public void draw(Canvas canvas) {
        if (alpha == 0) {
            return;
        }
        float r = circle ? Math.min(rect.width(), rect.height()) / 2f : radius;
        float innerRadius = Math.max(0, r - edgePaint.getStrokeWidth() / 2f);
        boolean showsBackdrop = backdrop != null && !backdrop.isRecycled()
                && backdropPaint.getShader() != null;
        if (!showsBackdrop) {
            canvas.drawRoundRect(rect, r, r, fillPaint);
        } else {
            if (lens > 0f) {
                canvas.drawRoundRect(rect, r, r, backdropPaint);
            }
            if (!bakedOver) {
                canvas.drawRoundRect(rect, r, r, veilPaint);
            }
        }
        if (focus > 0f) {
            canvas.drawRoundRect(rect, r, r, litPaint);
        }
        if (highlightPaint.getAlpha() > 0) {
            canvas.drawRoundRect(inner, innerRadius, innerRadius, highlightPaint);
        }
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
