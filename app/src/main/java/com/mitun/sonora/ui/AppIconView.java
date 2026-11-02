package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import com.mitun.sonora.AppEntry;

/**
 * The app "icon": a tvOS-shaped 5:3 card. It uses the app's Android TV banner
 * when one exists, otherwise a crisp generated brand card. All effects are
 * canvas draws on one hardware-accelerated view.
 */
public final class AppIconView extends View {
    /** The corner radius as a fraction of the card height, measured from tvOS. */
    private static final float RADIUS = 0.17f;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path starPath = new Path();
    private int glowSize;
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private final RectF artRect = new RectF();
    private final RectF edgeRect = new RectF();
    private final RectF shadowRect = new RectF();
    private AppEntry entry;
    private float sheen;
    private float parallaxX;
    private float parallaxY;
    private boolean favourite;
    private LinearGradient backgroundGradient;

    public AppIconView(Context context) {
        super(context);
        setWillNotDraw(false);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(Math.max(1f, Ui.dpf(context, 0.8f)));
        starPaint.setColor(Color.WHITE);
        starShadowPaint.setColor(0x66000000);
        labelPaint.setTypeface(Ui.medium());
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        highlightPaint.setStyle(Paint.Style.FILL);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                float radius = Math.min(v.getWidth(), v.getHeight()) * RADIUS;
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        setClipToOutline(false);
        setElevation(Ui.dpf(context, 4f));
    }

    public void setEntry(AppEntry entry) {
        this.entry = entry;
        backgroundGradient = null;
        glowSize = 0;
        invalidate();
    }

    /** Marks the card as one the user pinned to the top of the home screen. */
    public void setFavourite(boolean favourite) {
        if (this.favourite != favourite) {
            this.favourite = favourite;
            invalidate();
        }
    }

    /**
     * Slides the artwork inside the card without moving the card itself.
     *
     * The shift is a kick: the artwork jumps towards the new position and
     * springs back to rest, so the card looks like it has depth while it is
     * being pushed.
     */
    public void nudgeParallax(float xPx, float yPx) {
        Motion.FloatProperty x = parallaxX();
        Motion.FloatProperty y = parallaxY();
        Motion.nudgeCustom(this, 0, xPx, x);
        Motion.nudgeCustom(this, 1, yPx, y);
        Motion.springCustom(this, 0, 0f, Motion.DAMPING_CRITICAL,
                Motion.RESPONSE_FOCUS, x);
        Motion.springCustom(this, 1, 0f, Motion.DAMPING_CRITICAL,
                Motion.RESPONSE_FOCUS, y);
    }

    private Motion.FloatProperty parallaxX() {
        return new Motion.FloatProperty() {
            @Override
            public float get() {
                return parallaxX;
            }

            @Override
            public void set(float value) {
                parallaxX = value;
                invalidate();
            }
        };
    }

    private Motion.FloatProperty parallaxY() {
        return new Motion.FloatProperty() {
            @Override
            public float get() {
                return parallaxY;
            }

            @Override
            public void set(float value) {
                parallaxY = value;
                invalidate();
            }
        };
    }

    private Motion.FloatProperty sheenValue() {
        return new Motion.FloatProperty() {
            @Override
            public float get() {
                return sheen;
            }

            @Override
            public void set(float value) {
                sheen = value;
                // The rim brightens with the light, so a focused card separates
                // from its neighbours even on artwork with a busy edge.
                edgePaint.setColor(Ui.alpha(Color.WHITE, 0.16f + 0.42f * value));
                invalidate();
            }
        };
    }

    public void setFocused(boolean focused) {
        if (focused) {
            // The sheen is a spring too, so a focus that lands on a card
            // which is already lit carries on from where the light is.
            Motion.springCustom(this, 2, 1f, Motion.DAMPING_CRITICAL, 0.55f,
                    sheenValue());
            setElevation(Ui.dpf(getContext(), 14f));
        } else {
            Motion.springCustom(this, 2, 0f, Motion.DAMPING_CRITICAL, 0.35f,
                    sheenValue());
            setElevation(Ui.dpf(getContext(), 4f));
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        clipPath.reset();
        clipPath.addRoundRect(rect, h * RADIUS, h * RADIUS, Path.Direction.CW);
        highlightPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x22FFFFFF, 0x00FFFFFF, 0x05000000, 0x22FFFFFF},
                new float[]{0f, 0.28f, 0.82f, 1f}, Shader.TileMode.CLAMP));
        sheenPaint.setShader(new LinearGradient(0, 0, w * 0.55f, h,
                new int[]{0x00FFFFFF, 0x10FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        edgePaint.setColor(Ui.alpha(Color.WHITE, 0.16f));
        // The star that marks a pinned app, drawn as a path so it never
        // depends on a font having the character.
        float radius = h * 0.085f;
        buildStar(w - radius - h * 0.10f, radius + h * 0.10f, radius);
        backgroundGradient = null;
    }

    /** A five-pointed star, drawn from the outside in. */
    private void buildStar(float cx, float cy, float radius) {
        starPath.reset();
        for (int i = 0; i < 10; i++) {
            double angle = Math.PI / 5 * i - Math.PI / 2;
            float r = (i % 2 == 0) ? radius : radius * 0.44f;
            float x = cx + (float) (Math.cos(angle) * r);
            float y = cy + (float) (Math.sin(angle) * r);
            if (i == 0) {
                starPath.moveTo(x, y);
            } else {
                starPath.lineTo(x, y);
            }
        }
        starPath.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (entry == null) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        // A broad, soft shadow under the lifted card. Elevation alone draws a
        // tight contact shadow that disappears against a dark dock; this is
        // what makes the focused icon look like it is floating above the bar.
        drawFocusShadow(canvas, w, h);
        canvas.save();
        canvas.clipPath(clipPath);

        if (entry.banner != null) {
            artRect.set(0, 0, w, h);
            Ui.drawCenterCrop(canvas, entry.banner, artRect, 1.015f,
                    parallaxX, parallaxY);
        } else {
            drawGeneratedCard(canvas, w, h);
        }

        // Glass finish: a soft highlight, a hairline edge and a focus-only sheen.
        canvas.drawRoundRect(rect, h * RADIUS, h * RADIUS, highlightPaint);
        if (sheen > 0f) {
            float band = w * 0.55f;
            float x = -band + sheen * (w + band * 2f);
            canvas.save();
            canvas.translate(x, 0);
            canvas.drawRect(0, 0, band, h, sheenPaint);
            canvas.restore();
        }
        float inset = edgePaint.getStrokeWidth() / 2f;
        edgeRect.set(inset, inset, w - inset, h - inset);
        float edgeRadius = Math.max(1f, h * RADIUS - inset);
        canvas.drawRoundRect(edgeRect, edgeRadius, edgeRadius, edgePaint);
        canvas.restore();

        if (favourite) {
            // A soft shadow under the star, so it reads on light artwork too.
            canvas.save();
            canvas.translate(0, Ui.dpf(getContext(), 1.5f));
            canvas.drawPath(starPath, starShadowPaint);
            canvas.restore();
            canvas.drawPath(starPath, starPaint);
        }
    }

    private void drawFocusShadow(Canvas canvas, int w, int h) {
        if (sheen <= 0.01f) {
            return;
        }
        float radius = h * RADIUS;
        // Four layers, widest first: a soft falloff for a card lifted off a
        // dark surface, at the cost of a few draws while focus settles.
        for (int i = 3; i >= 0; i--) {
            float spread = Ui.dpf(getContext(), 1.5f + i * 5f);
            float drop = Ui.dpf(getContext(), 2f + i * 2f);
            shadowPaint.setColor(Ui.alpha(Color.BLACK, 0.11f * (1f - i * 0.22f) * sheen));
            shadowRect.set(-spread, -spread + drop, w + spread, h + spread + drop);
            canvas.drawRoundRect(shadowRect, radius + spread, radius + spread, shadowPaint);
        }
    }

    private void drawGeneratedCard(Canvas canvas, int w, int h) {
        if (entry.settings) {
            fillPaint.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{0xFF4D535E, 0xFF2B303A, 0xFF171A21},
                    new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        } else if (backgroundGradient == null) {
            int base = Ui.tileColor(entry.accent);
            backgroundGradient = new LinearGradient(0, 0, w, h,
                    new int[]{lighten(base, 0.16f), base, darken(base, 0.32f)},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            fillPaint.setShader(backgroundGradient);
        }
        fillPaint.setAlpha(255);
        canvas.drawRect(0, 0, w, h, fillPaint);
        // A quiet pool of the icon's own color, so generated tiles don't feel flat.
        if (!entry.settings) {
            if (glowSize != h) {
                glowSize = h;
                glowPaint.setShader(new android.graphics.RadialGradient(
                        w * 0.76f, h * 0.08f, h * 0.46f,
                        new int[]{Ui.alpha(entry.accent, 0.26f), 0x00FFFFFF},
                        new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            }
            canvas.drawCircle(w * 0.76f, h * 0.08f, h * 0.46f, glowPaint);
        }

        Bitmap art = entry.icon;
        if (art == null || art.isRecycled()) {
            return;
        }
        float artSize = h * (entry.settings ? 0.50f : 0.46f);
        float cx = w / 2f + parallaxX;
        float cy = h * (entry.settings ? 0.38f : 0.35f) + parallaxY;
        RectF dst = new RectF(cx - artSize / 2f, cy - artSize / 2f,
                cx + artSize / 2f, cy + artSize / 2f);
        canvas.drawBitmap(art, null, dst, bitmapPaint);

        labelPaint.setTextSize(h * 0.145f);
        CharSequence label = TextUtils.ellipsize(entry.label, labelPaint, w * 0.84f,
                TextUtils.TruncateAt.END);
        float baseline = h * 0.80f;
        labelPaint.setColor(0xFFF5F6F8);
        canvas.drawText(label, 0, label.length(), w / 2f, baseline, labelPaint);
    }

    private static int lighten(int color, float amount) {
        return Ui.lerpColor(color, Color.WHITE, amount);
    }

    private static int darken(int color, float amount) {
        return Ui.lerpColor(color, Color.BLACK, amount);
    }

    @Override
    protected void onDetachedFromWindow() {
        Motion.cancel(this);
        super.onDetachedFromWindow();
    }
}
