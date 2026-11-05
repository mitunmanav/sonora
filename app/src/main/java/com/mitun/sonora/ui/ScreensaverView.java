package com.mitun.sonora.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import com.mitun.sonora.DeviceTier;
import com.mitun.sonora.Prefs;
import com.mitun.sonora.PhotoLoader;
import com.mitun.sonora.SceneChoice;
import com.mitun.sonora.Scenes;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The screen saver: full-bleed aerial stills with a very slow drift, crossfaded
 * into each other. Two bitmaps are alive at a time, so the memory cost stays
 * around two screen-sized images regardless of how many scenes exist.
 *
 * Nothing here invalidates per frame. The drift is a scale and a translation on
 * each scene view — RenderNode properties the GPU applies without re-recording
 * a draw — and the crossfade is view alpha. Only a scene change or an overlay
 * toggle ever asks a view to redraw.
 */
public final class ScreensaverView extends FrameLayout {
    private static final long FRAME_MS = 33; // ~30fps is plenty for a slow drift.
    /** The lightest tier draws a little less often, since the drift hardly shows it. */
    private static final long FRAME_MS_LOW = 50;
    /** How long one scene takes to fade into the next. */
    private static final long FADE_MS = 2200;
    /** Zoom at the start and end of a scene's drift. */
    private static final float ZOOM_START = 1.04f;
    private static final float ZOOM_END = 1.10f;

    public interface Listener {
        void onSceneChanged(int index);

        void onDismiss();
    }

    /**
     * One aerial, drawn once to cover its bounds and then only ever moved by
     * RenderNode properties. {@link #applyProgress} turns the scene clock into
     * the same zoom-and-pan the old per-frame draw produced.
     */
    private static final class SceneView extends View {
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private Bitmap bitmap;
        private float fit = 1f;

        SceneView(Context context) {
            super(context);
        }

        void setSceneBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            measureFit();
            invalidate();
        }

        Bitmap getSceneBitmap() {
            return bitmap;
        }

        private void measureFit() {
            if (bitmap == null || getWidth() == 0 || getHeight() == 0) {
                return;
            }
            fit = Math.max(getWidth() / (float) bitmap.getWidth(),
                    getHeight() / (float) bitmap.getHeight());
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            setPivotX(w / 2f);
            setPivotY(h / 2f);
            measureFit();
        }

        /** Back to the start-of-scene framing, for a view being reused. */
        void resetDrift() {
            setScaleX(ZOOM_START);
            setScaleY(ZOOM_START);
            setTranslationX(0f);
        }

        /**
         * Drives the Ken Burns drift for scene progress {@code t} in [0,1].
         * The bitmap is already cover-fit inside this view, so the view scale
         * is the zoom factor and the translation is the horizontal pan.
         */
        void applyProgress(float t, float panDirection, boolean reduced) {
            if (bitmap == null) {
                return;
            }
            float zoom = reduced ? ZOOM_START : ZOOM_START + (ZOOM_END - ZOOM_START) * t;
            setScaleX(zoom);
            setScaleY(zoom);
            float drawnWidth = bitmap.getWidth() * fit * zoom;
            setTranslationX((getWidth() - drawnWidth) * 0.5f * panDirection * t);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Bitmap b = bitmap;
            if (b == null) {
                return;
            }
            float scale = fit;
            float dx = (getWidth() - b.getWidth() * scale) * 0.5f;
            float dy = (getHeight() - b.getHeight() * scale) * 0.5f;
            canvas.drawBitmap(b, dx, dy, paint);
        }
    }

    /**
     * The scrim and the location credit. It redraws only when the scene or the
     * overlay setting changes — never on the drift clock.
     */
    private static final class OverlayView extends View {
        private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint creditPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int sceneIndex = -1;
        private boolean show;

        OverlayView(Context context) {
            super(context);
            Type.apply(titlePaint, Type.DISPLAY, context);
            Type.apply(creditPaint, Type.BODY, context);
            titlePaint.setColor(Color.WHITE);
            creditPaint.setColor(Ui.alpha(Color.WHITE, 0.72f));
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setInfo(int index, boolean showOverlay) {
            if (index == sceneIndex && showOverlay == show) {
                return;
            }
            sceneIndex = index;
            show = showOverlay;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) {
                return;
            }
            if (scrimPaint.getShader() == null) {
                // Built once: a new gradient every draw is garbage every draw.
                scrimPaint.setShader(new LinearGradient(0, h * 0.72f, 0, h,
                        new int[]{0x00000000, 0x8C000000},
                        new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            }
            canvas.drawRect(0, h * 0.72f, w, h, scrimPaint);
            if (!show || sceneIndex < 0) {
                return;
            }
            java.util.List<Scenes.Scene> all = Scenes.all(getContext());
            Scenes.Scene scene = all.get(Math.max(0, Math.min(all.size() - 1, sceneIndex)));
            float density = getResources().getDisplayMetrics().density;
            canvas.drawText(scene.name, 48 * density, h - 56 * density, titlePaint);
            if (scene.credit != null) {
                canvas.drawText(scene.credit, 48 * density, h - 26 * density, creditPaint);
            }
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sonora-screensaver-decode");
        t.setDaemon(true);
        return t;
    });
    private final SceneView sceneA;
    private final SceneView sceneB;
    private final OverlayView overlay;
    private volatile boolean loadingNext;

    private SceneView current;
    private SceneView incoming;
    private int currentIndex = -1;
    private int incomingIndex = -1;
    private long sceneStartedAt;
    private long fadeStart;
    private boolean running;
    private boolean tickerPosted;
    private boolean showOverlay = true;
    private int sceneDurationMs = 60000;
    private float panDirection = 1f;
    private Listener listener;
    private final boolean reduced;
    private final int tier;
    /** The aerials the user left out in Choose Aerials. */
    private final boolean[] hidden;

    public ScreensaverView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        // A full-screen drifting background is exactly what reduced motion is
        // meant to stop, so the stills hold still and only cross-fade.
        reduced = Motion.isReduced(context);
        tier = DeviceTier.get(context);
        hidden = Scenes.hiddenFlags(new Prefs(context), context);
        sceneA = new SceneView(context);
        sceneB = new SceneView(context);
        overlay = new OverlayView(context);
        addView(sceneA, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(sceneB, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(overlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
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
        overlay.setInfo(currentIndex, show);
    }

    public void showInfoOverlay() {
        setShowOverlay(!showOverlay);
    }

    public void start() {
        running = true;
        if (current == null || current.getSceneBitmap() == null) {
            currentIndex = -1;
            showNextScene(1);
        }
        if (!tickerPosted) {
            tickerPosted = true;
            handler.post(tick);
        }
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
        tickerPosted = false;
        if (current != null) {
            current.setSceneBitmap(null);
        }
        if (incoming != null) {
            incoming.setSceneBitmap(null);
        }
        current = null;
        incoming = null;
        fadeStart = 0;
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
        if (loadingNext) {
            return;
        }
        final int targetIndex = SceneChoice.next(currentIndex, step, hidden);
        if (targetIndex < 0) {
            return;
        }
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
                if (current == null || current.getSceneBitmap() == null) {
                    // The first scene has nothing to fade out of.
                    current = sceneA;
                    incoming = sceneB;
                    current.setSceneBitmap(bitmap);
                    current.resetDrift();
                    incoming.resetDrift();
                    currentIndex = targetIndex;
                    sceneStartedAt = System.currentTimeMillis();
                    fadeStart = 0;
                    overlay.setInfo(currentIndex, showOverlay);
                    if (listener != null) {
                        listener.onSceneChanged(currentIndex);
                    }
                } else {
                    incoming.setSceneBitmap(bitmap);
                    incoming.resetDrift();
                    incomingIndex = targetIndex;
                    incoming.setAlpha(0f);
                    fadeStart = System.currentTimeMillis();
                    // The incoming scene has to draw above the outgoing one,
                    // but under the overlay.
                    incoming.bringToFront();
                    overlay.bringToFront();
                }
            });
        });
    }

    /** The index after the current one, wrapping round at both ends. */
    public static int wrap(int index, int count) {
        return ((index % count) + count) % count;
    }

    private Bitmap load(int index) {
        java.util.List<Scenes.Scene> all = Scenes.all(getContext());
        if (index < 0 || index >= all.size()) {
            return null;
        }
        Scenes.Scene scene = all.get(index);
        int divisor = DeviceTier.wallpaperDivisor(tier);
        int width = getResources().getDisplayMetrics().widthPixels / divisor;
        int height = getResources().getDisplayMetrics().heightPixels / divisor;
        try {
            if (scene.res != null) {
                return Ui.decodeScene(getResources(), scene.res, width, height,
                        tier == DeviceTier.LOW ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888);
            } else if (scene.path != null) {
                return PhotoLoader.decode(scene.path, width, height);
            }
        } catch (RuntimeException | OutOfMemoryError e) {
            // A TV with little memory can refuse the decode. The saver says so
            // rather than taking the whole app down with it.
            return null;
        }
        return null;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                tickerPosted = false;
                return;
            }
            step(System.currentTimeMillis());
            handler.postDelayed(this, tier == DeviceTier.LOW ? FRAME_MS_LOW : FRAME_MS);
        }
    };

    private void step(long now) {
        if (fadeStart > 0 && incoming != null) {
            float fade = Math.min(1f, (now - fadeStart) / (float) FADE_MS);
            incoming.setAlpha(fade);
            if (fade >= 1f) {
                // The incoming scene takes over; the outgoing one goes back to
                // being the spare and drops its bitmap.
                SceneView outgoing = current;
                current = incoming;
                incoming = outgoing;
                currentIndex = incomingIndex;
                incomingIndex = -1;
                current.setAlpha(1f);
                incoming.setSceneBitmap(null);
                incoming.setAlpha(1f);
                fadeStart = 0;
                sceneStartedAt = now;
                overlay.setInfo(currentIndex, showOverlay);
                if (listener != null) {
                    listener.onSceneChanged(currentIndex);
                }
            }
        }
        if (current != null && current.getSceneBitmap() != null) {
            long elapsed = now - sceneStartedAt;
            float t = Math.min(1f, elapsed / (float) sceneDurationMs);
            current.applyProgress(t, panDirection, reduced);
            if (elapsed > sceneDurationMs && fadeStart == 0 && !loadingNext) {
                showNextScene(1);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        decodeExecutor.shutdownNow();
        super.onDetachedFromWindow();
    }
}
