package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import com.mitun.sonora.Scenes;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint creditPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF dest = new RectF();

    private final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sonora-screensaver-decode");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean loadingNext;

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
    private final boolean reduced;

    public ScreensaverView(Context context) {
        super(context);
        // A full-screen drifting background is exactly what reduced motion is
        // meant to stop, so the stills hold still and only cross-fade.
        reduced = Motion.isReduced(context);
        Type.apply(titlePaint, Type.DISPLAY, context);
        Type.apply(creditPaint, Type.BODY, context);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setSceneDurationSec(int seconds) {
        sceneDurationMs = Math.max(10, seconds) * 1000;
    }

    public void setSceneDurationMs(int ms) {
        sceneDurationMs = Math.max(10000, ms);
    }

    public void setShowOverlay(boolean show) {
        showOverlay = show;
        invalidate();
    }

    public void start() {
        running = true;
        if (current == null) {
            currentIndex = -1;
            showNextScene(1);
        }
        postInvalidateOnAnimation();
    }

    public void stop() {
        running = false;
        current = null;
        next = null;
    }

    /** Called when the user asks for a different location. */
    public void nextScene() {
        showNextScene(1);
    }

    public void previousScene() {
        showNextScene(-1);
    }

    /**
     * Moves to the next scene, forwards or backwards.
     * Decodes the scene on a background thread so frame rate is never stalled.
     *
     * @param step 1 for the next scene, -1 for the one before this one.
     */
    private void showNextScene(int step) {
        int count = Scenes.ALL.size();
        if (count == 0 || loadingNext) {
            return;
        }
        final int targetIndex = wrap(currentIndex + step, count);
        final float direction = step < 0 ? -1f : 1f;
        loadingNext = true;
        decodeExecutor.execute(() -> {
            final Bitmap bitmap = load(targetIndex);
            post(() -> {
                loadingNext = false;
                if (!running) {
                    return;
                }
                panDirection = direction;
                if (bitmap == null) {
                    // A scene that will not decode must not be tried again on
                    // every frame: wait for the next turn instead.
                    sceneStartedAt = System.currentTimeMillis();
                    return;
                }
                if (current == null) {
                    current = bitmap;
                    currentIndex = targetIndex;
                    sceneStartedAt = System.currentTimeMillis();
                    progress = 0f;
                    fadeStart = 0;
                    if (listener != null) {
                        listener.onSceneChanged(targetIndex);
                    }
                } else {
                    nextIndex = targetIndex;
                    next = bitmap;
                    fadeStart = System.currentTimeMillis();
                }
                invalidate();
            });
        });
    }

    /** The index after the current one, wrapping round at both ends. */
    public static int wrap(int index, int count) {
        return ((index % count) + count) % count;
    }

    private Bitmap load(int index) {
        Scenes.Scene scene = Scenes.ALL.get(index);
        int width = Math.max(1280, getResources().getDisplayMetrics().widthPixels);
        int height = Math.max(720, getResources().getDisplayMetrics().heightPixels);
        try {
            return Ui.decodeScene(getResources(), scene.res, width, height);
        } catch (RuntimeException | OutOfMemoryError e) {
            // A TV with little memory can refuse the decode. The saver says so
            // rather than taking the whole app down with it.
            return null;
        }
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
            if (elapsed > sceneDurationMs && next == null && !loadingNext) {
                showNextScene(1);
            }
            postInvalidateDelayed(FRAME_MS);
        }
    }

    private void drawScene(Canvas canvas, Bitmap bitmap, float t) {
        float scale = reduced ? 1.04f : 1.04f + 0.06f * t;
        float dx = (bitmap.getWidth() - getWidth() * scale) * (0.5f + 0.5f * panDirection * t);
        float dy = (bitmap.getHeight() - getHeight() * scale) * 0.5f;
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        canvas.drawBitmap(bitmap, matrix, paint);
    }

    private void drawOverlay(Canvas canvas, int w, int h) {
        if (scrimPaint.getShader() == null) {
            // Built once: a new gradient every frame is garbage every frame.
            scrimPaint.setShader(new LinearGradient(0, h * 0.72f, 0, h,
                    new int[]{0x00000000, 0x8C000000},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        }
        canvas.drawRect(0, h * 0.72f, w, h, scrimPaint);
        if (!showOverlay || current == null) {
            // Nothing is loaded, so there is no scene to name.
            return;
        }
        Scenes.Scene scene = Scenes.ALL.get(Math.max(0, currentIndex));
        float density = getResources().getDisplayMetrics().density;
        canvas.drawText(scene.name, 48 * density, h - 56 * density, titlePaint);
        canvas.drawText(scene.credit, 48 * density, h - 26 * density, creditPaint);
    }

    public void showInfoOverlay() {
        showOverlay = !showOverlay;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        current = null;
        next = null;
        decodeExecutor.shutdownNow();
        super.onDetachedFromWindow();
    }
}
