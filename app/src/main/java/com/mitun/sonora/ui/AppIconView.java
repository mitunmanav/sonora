package com.mitun.sonora.ui;

import android.animation.ValueAnimator;
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
import android.view.animation.OvershootInterpolator;

import com.mitun.sonora.AppEntry;

/**
 * The app "icon": a tvOS-shaped 5:3 card. It uses the app's Android TV banner
 * when one exists, otherwise a crisp generated brand card. All effects are
 * canvas draws on one hardware-accelerated view.
 */
public final class AppIconView extends View {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int glowSize;
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private final RectF artRect = new RectF();
    private AppEntry entry;
    private float sheen;
    private float parallaxX;
    private float parallaxY;
    private ValueAnimator sheenAnimator;
    private LinearGradient backgroundGradient;

    public AppIconView(Context context) {
        super(context);
        setWillNotDraw(false);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(Math.max(1f, Ui.dpf(context, 0.8f)));
        labelPaint.setTypeface(Ui.medium());
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        highlightPaint.setStyle(Paint.Style.FILL);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                float radius = Math.min(v.getWidth(), v.getHeight()) * 0.185f;
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

    public void setParallax(float xPx, float yPx) {
        parallaxX = xPx;
        parallaxY = yPx;
        invalidate();
    }

    public void setFocused(boolean focused) {
        if (sheenAnimator != null) {
            sheenAnimator.cancel();
        }
        if (focused) {
            sheen = 0f;
            sheenAnimator = ValueAnimator.ofFloat(0f, 1f);
            sheenAnimator.setDuration(950);
            sheenAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));
            sheenAnimator.addUpdateListener(a -> {
                sheen = (float) a.getAnimatedValue();
                invalidate();
            });
            setElevation(Ui.dpf(getContext(), 22f));
            sheenAnimator.start();
        } else {
            sheen = 0f;
            setElevation(Ui.dpf(getContext(), 4f));
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        clipPath.reset();
        clipPath.addRoundRect(rect, h * 0.185f, h * 0.185f, Path.Direction.CW);
        float radius = h * 0.185f;
        highlightPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x22FFFFFF, 0x00FFFFFF, 0x05000000, 0x22000000},
                new float[]{0f, 0.28f, 0.82f, 1f}, Shader.TileMode.CLAMP));
        sheenPaint.setShader(new LinearGradient(0, 0, w * 0.55f, h,
                new int[]{0x00FFFFFF, 0x16FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        edgePaint.setColor(0x24FFFFFF);
        backgroundGradient = null;
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
        canvas.drawRoundRect(rect, h * 0.185f, h * 0.185f, highlightPaint);
        if (sheen > 0f) {
            float band = w * 0.55f;
            float x = -band + sheen * (w + band * 2f);
            canvas.save();
            canvas.translate(x, 0);
            canvas.drawRect(0, 0, band, h, sheenPaint);
            canvas.restore();
        }
        canvas.drawRoundRect(new RectF(edgePaint.getStrokeWidth() / 2f,
                        edgePaint.getStrokeWidth() / 2f,
                        w - edgePaint.getStrokeWidth() / 2f,
                        h - edgePaint.getStrokeWidth() / 2f),
                Math.max(1f, h * 0.185f - edgePaint.getStrokeWidth() / 2f),
                Math.max(1f, h * 0.185f - edgePaint.getStrokeWidth() / 2f), edgePaint);
        canvas.restore();
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
        if (sheenAnimator != null) {
            sheenAnimator.cancel();
            sheenAnimator = null;
        }
        super.onDetachedFromWindow();
    }
}
