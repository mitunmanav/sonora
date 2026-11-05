package com.mitun.sonora.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Every mark in the app, drawn on a 24-unit grid. A path and a paint cost next to
 * nothing and stay sharp at any size, from a button to the mark beside a Settings
 * page, where a bitmap per mark and per size would not.
 */
public final class Glyph extends Drawable {
    public static final int SAVER = 0;
    public static final int WIFI = 1;
    public static final int BLUETOOTH = 2;
    public static final int SOUND = 3;
    public static final int DISPLAY = 4;
    public static final int SEARCH = 5;
    public static final int APPS = 6;
    public static final int SLIDERS = 7;
    public static final int GEAR = 8;
    /** An eye with a line through it: this aerial is left out. */
    public static final int HIDDEN = 9;
    public static final int CLOCK = 10;
    public static final int TOOLS = 11;
    public static final int STAR = 12;
    public static final int MIC = 13;
    public static final int DELETE = 14;
    public static final int HOME = 15;
    public static final int INFO = 16;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public Glyph(int kind) {
        boolean solid = kind == GEAR || kind == STAR || kind == TOOLS;
        paint.setStyle(solid ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(1.8f);
        build(kind);
    }

    /** The colour of the mark. It follows the surface as the surface lights up. */
    public void setInk(int colour) {
        paint.setColor(colour);
        invalidateSelf();
    }

    /** The weight of the line, in units of the 24-unit grid. */
    public void setWeight(float units) {
        paint.setStrokeWidth(units);
        invalidateSelf();
    }

    private void build(int kind) {
        switch (kind) {
            case SAVER:
                path.addCircle(8.5f, 7.5f, 2.1f, Path.Direction.CW);
                path.moveTo(3f, 19f);
                path.lineTo(9.5f, 11.5f);
                path.lineTo(13f, 15f);
                path.lineTo(16.5f, 9.5f);
                path.lineTo(21f, 19f);
                path.close();
                break;
            case WIFI:
                // Three concentric upper arcs sharing the dot's centre, like wifi.
                path.addCircle(12f, 19.6f, 0.9f, Path.Direction.CW);
                path.addArc(new RectF(-1.5f, 6.1f, 25.5f, 33.1f), -140f, 100f);
                path.addArc(new RectF(3f, 10.6f, 21f, 28.6f), -140f, 100f);
                path.addArc(new RectF(7.5f, 15.1f, 16.5f, 24.1f), -140f, 100f);
                break;
            case BLUETOOTH:
                path.moveTo(7f, 7.5f);
                path.lineTo(17f, 16f);
                path.lineTo(12f, 21f);
                path.lineTo(12f, 3f);
                path.lineTo(17f, 8f);
                path.lineTo(7f, 16.5f);
                break;
            case SOUND:
                path.moveTo(4f, 9.5f);
                path.lineTo(7.5f, 9.5f);
                path.lineTo(12f, 5.5f);
                path.lineTo(12f, 18.5f);
                path.lineTo(7.5f, 14.5f);
                path.lineTo(4f, 14.5f);
                path.close();
                path.addArc(new RectF(5f, 5f, 19f, 19f), -60f, 120f);
                path.addArc(new RectF(7.5f, 7.5f, 16.5f, 16.5f), -60f, 120f);
                break;
            case DISPLAY:
                path.addRoundRect(new RectF(3f, 4.5f, 21f, 16.5f), 2f, 2f, Path.Direction.CW);
                path.moveTo(12f, 16.5f);
                path.lineTo(12f, 20.5f);
                path.moveTo(8.5f, 20.5f);
                path.lineTo(15.5f, 20.5f);
                break;
            case SEARCH:
                path.addCircle(10.5f, 10.5f, 6.5f, Path.Direction.CW);
                path.moveTo(15.3f, 15.3f);
                path.lineTo(20.5f, 20.5f);
                break;
            case APPS:
                for (int y = 0; y < 2; y++) {
                    for (int x = 0; x < 2; x++) {
                        float left = 3.5f + x * 9.5f;
                        float top = 3.5f + y * 9.5f;
                        path.addRoundRect(new RectF(left, top, left + 8f, top + 8f), 2f, 2f,
                                Path.Direction.CW);
                    }
                }
                break;
            case SLIDERS:
                slider(7f, 15f);
                slider(12f, 8f);
                slider(17f, 14f);
                break;
            case HIDDEN:
                path.moveTo(2.5f, 12f);
                path.quadTo(12f, 5.2f, 21.5f, 12f);
                path.quadTo(12f, 18.8f, 2.5f, 12f);
                path.close();
                path.addCircle(12f, 12f, 2.4f, Path.Direction.CW);
                path.moveTo(4.5f, 19.5f);
                path.lineTo(19.5f, 4.5f);
                break;
            case CLOCK:
                path.addCircle(12f, 12f, 9f, Path.Direction.CW);
                path.moveTo(12f, 6.8f);
                path.lineTo(12f, 12f);
                path.lineTo(15.8f, 14.2f);
                break;
            case TOOLS:
                buildWrench();
                break;
            case STAR:
                star();
                break;
            case MIC:
                path.addRoundRect(new RectF(9f, 3f, 15f, 13.5f), 3f, 3f, Path.Direction.CW);
                path.addArc(new RectF(5.5f, 6f, 18.5f, 17.5f), 0f, 180f);
                path.moveTo(12f, 17.5f);
                path.lineTo(12f, 20.5f);
                path.moveTo(9.5f, 20.5f);
                path.lineTo(14.5f, 20.5f);
                break;
            case DELETE:
                path.moveTo(5.5f, 6.5f);
                path.lineTo(18.5f, 6.5f);
                path.moveTo(9.5f, 6.5f);
                path.lineTo(9.5f, 5f);
                path.lineTo(14.5f, 5f);
                path.lineTo(14.5f, 6.5f);
                path.moveTo(5.5f, 6.5f);
                path.lineTo(5.5f, 19f);
                path.quadTo(5.5f, 20.5f, 7f, 20.5f);
                path.lineTo(17f, 20.5f);
                path.quadTo(18.5f, 20.5f, 18.5f, 19f);
                path.lineTo(18.5f, 6.5f);
                path.moveTo(9.5f, 10.5f);
                path.lineTo(9.5f, 16.5f);
                path.moveTo(14.5f, 10.5f);
                path.lineTo(14.5f, 16.5f);
                break;
            case HOME:
                path.moveTo(4f, 11.5f);
                path.lineTo(12f, 4.5f);
                path.lineTo(20f, 11.5f);
                path.lineTo(20f, 20f);
                path.lineTo(4f, 20f);
                path.close();
                path.addRoundRect(new RectF(10.25f, 13f, 13.75f, 20f), 0.8f, 0.8f,
                        Path.Direction.CW);
                break;
            case INFO:
                path.addCircle(12f, 12f, 9f, Path.Direction.CW);
                path.moveTo(12f, 11f);
                path.lineTo(12f, 16.8f);
                path.addCircle(12f, 7.8f, 0.9f, Path.Direction.CW);
                break;
            case GEAR:
            default:
                buildGear();
                break;
        }
    }

    /** A solid wrench: a round head with its jaw cut out, on a diagonal handle. */
    private void buildWrench() {
        float cx = 16f;
        float cy = 8f;
        path.addCircle(cx, cy, 6f, Path.Direction.CW);
        Path handle = new Path();
        handle.addRoundRect(new RectF(cx, cy - 2.5f, cx + 17f, cy + 2.5f), 2.5f, 2.5f,
                Path.Direction.CW);
        Matrix turn = new Matrix();
        turn.setRotate(135f, cx, cy);
        handle.transform(turn);
        path.op(handle, Path.Op.UNION);
        Path jaw = new Path();
        jaw.addRect(new RectF(cx, cy - 2.4f, cx + 9f, cy + 2.4f), Path.Direction.CW);
        turn.setRotate(-45f, cx, cy);
        jaw.transform(turn);
        path.op(jaw, Path.Op.DIFFERENCE);
    }

    /** A solid five-point star, the favourites mark. */
    private void star() {
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 5 * i - Math.PI / 2;
            float r = i % 2 == 0 ? 10f : 4.3f;
            float x = 12f + (float) (Math.cos(a) * r);
            float y = 12f + (float) (Math.sin(a) * r);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
    }

    /**
     * A solid cog with a hole through it. The teeth are a smooth radius profile
     * sampled around the circle, so the fill has no corners to catch the light.
     */
    private void buildGear() {
        int teeth = 8;
        int samples = 160;
        float root = 8.3f;
        float outer = 11f;
        double step = Math.PI * 2 / teeth;
        for (int i = 0; i < samples; i++) {
            double a = Math.PI * 2 * i / samples;
            double t = (a / step) % 1.0;
            double tooth = Math.pow(0.5 - 0.5 * Math.cos(Math.PI * 2 * t), 1.5);
            float r = (float) (root + (outer - root) * tooth);
            float x = 12f + (float) (Math.cos(a) * r);
            float y = 12f + (float) (Math.sin(a) * r);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        path.addCircle(12f, 12f, 4.2f, Path.Direction.CCW);
    }

    private void slider(float x, float knob) {
        path.moveTo(x, 4f);
        path.lineTo(x, knob - 2.4f);
        path.addCircle(x, knob, 2.4f, Path.Direction.CW);
        path.moveTo(x, knob + 2.4f);
        path.lineTo(x, 20f);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int saved = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
        canvas.drawPath(path, paint);
        canvas.restoreToCount(saved);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
