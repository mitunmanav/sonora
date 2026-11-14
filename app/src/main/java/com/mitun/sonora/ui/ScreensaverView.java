package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import com.mitun.sonora.Scenes;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The screen saver: full-bleed aerial stills with a very slow drift, crossfaded
 * into each other. Two bitmaps are alive at a time, so the memory cost stays
 * around two screen-sized images regardless of how many scenes exist.
 */
public final class ScreensaverView extends View {
    private static final long FRAME_MS = 33; // ~30fps is plenty for a slow drift.

    public interface Listener {
        void onSceneChanged(int index);

        void onDismiss();
    }

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF dest = new RectF();

    private Bitmap current;
    private Bitmap next;
    private int currentIndex = -1;
    private int nextIndex = -1;
    private long sceneStartedAt;
    private long fadeStart = 0;
    private float progress;
    private boolean running;
    private boolean showOverlay = true;
    private int sceneDurationMs = 60000;
    private float panDirection = 1f;
    private Listener listener;

    public ScreensaverView(Context context) {
        super(context);
        textPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        textPaint.setColor(Color.WHITE);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setSceneDurationMs(int seconds) {
        sceneDurationMs = Math.max(10, seconds) * 1000;
    }

    public void setShowOverlay(boolean show) {
        showOverlay = show;
        invalidate();
    }

    public void start() {
        running = true;
        if (current == null) {
            currentIndex = -1;
            showNextScene();
        }
        postInvalidateOnAnimation();
    }

    public void stop() {
        running = false;
    }

    /** Called when the user asks for a different location. */
    public void nextScene() {
        showNextScene();
    }

    public void previousScene() {
        panDirection = -1f;
        showNextScene();
    }

    private void showNextScene() {
        if (current == null) {
            int index = (currentIndex + 1) % Scenes.ALL.size();
            Bitmap bitmap = load(index);
            if (bitmap == null) {
                return;
            }
            current = bitmap;
            currentIndex = index;
            sceneStartedAt = System.currentTimeMillis();
            progress = 0f;
            fadeStart = 0;
            if (listener != null) {
                listener.onSceneChanged(index);
            }
        } else {
            nextIndex = (currentIndex + 1) % Scenes.ALL.size();
            Bitmap bitmap = load(nextIndex);
            if (bitmap == null) {
                return;
            }
            next = bitmap;
            fadeStart = System.currentTimeMillis();
        }
        invalidate();
    }

    private Bitmap load(int index) {
        Scenes.Scene scene = Scenes.ALL.get(index);
        int width = Math.max(1280, getResources().getDisplayMetrics().widthPixels);
        int height = Math.max(720, getResources().getDisplayMetrics().heightPixels);
        return Ui.decodeScene(getResources(), scene.res, width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        canvas.drawColor(Color.BLACK);
        dest.set(0, 0, w, h);
        if (current != null) {
            drawScene(canvas, current, progress);
        }
        if (next != null && fadeStart > 0) {
            float fade = Math.min(1f, (System.currentTimeMillis() - fadeStart) / 2200f);
            paint.setAlpha((int) (fade * 255));
            drawScene(canvas, next, 0f);
            paint.setAlpha(255);
            if (fade >= 1f) {
                current = next;
                next = null;
                currentIndex = nextIndex;
                nextIndex = -1;
                fadeStart = 0;
                sceneStartedAt = System.currentTimeMillis();
                progress = 0f;
                if (listener != null) {
                    listener.onSceneChanged(currentIndex);
                }
            }
        }
        drawOverlay(canvas, w, h);
        if (running) {
            long elapsed = System.currentTimeMillis() - sceneStartedAt;
            progress = Math.min(1f, elapsed / (float) sceneDurationMs);
            if (elapsed > sceneDurationMs && next == null) {
                showNextScene();
            }
            postInvalidateDelayed(FRAME_MS);
        }
    }

    private void drawScene(Canvas canvas, Bitmap bitmap, float t) {
        float scale = 1.04f + 0.06f * t;
        float dx = (bitmap.getWidth() - getWidth() * scale) * (0.5f + 0.5f * panDirection * t);
        float dy = (bitmap.getHeight() - getHeight() * scale) * 0.5f;
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        canvas.drawBitmap(bitmap, matrix, paint);
    }

    private void drawOverlay(Canvas canvas, int w, int h) {
        scrimPaint.setShader(Ui.verticalScrim(0x00000000, 0x00000000, 0x8C000000));
        canvas.drawRect(0, h * 0.72f, w, h, scrimPaint);
        if (!showOverlay) {
            return;
        }
        Scenes.Scene scene = Scenes.ALL.get(Math.max(0, currentIndex));
        float density = getResources().getDisplayMetrics().density;
        textPaint.setTextSize(30 * density);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        canvas.drawText(scene.name, 48 * density, h - 56 * density, textPaint);
        textPaint.setTextSize(22 * density);
        textPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        textPaint.setColor(0xCCFFFFFF);
        canvas.drawText(scene.credit, 48 * density, h - 26 * density, textPaint);
        textPaint.setColor(Color.WHITE);
    }

    public void showInfoOverlay() {
        showOverlay = !showOverlay;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }
}
