package com.mitun.sonora.ui;

import android.content.Context;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.View;

import com.mitun.sonora.Prefs;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Motion for the launcher and the tools.
 *
 * Springs, not tweens. A spring starts from whatever is on screen and can be
 * re-targeted at any moment, so a focus change that is interrupted halfway
 * continues from where it was instead of snapping. A fixed-duration animation
 * cannot do that, and the difference is what makes an interface feel alive
 * rather than scripted.
 *
 * Damping is Apple's: 1.0 means no overshoot and is the default for anything
 * the user did not fling. Bounce is reserved for movement that carried
 * momentum of its own.
 *
 * The physics lives in {@link Spring}, which is pure maths; this class owns
 * the frame clock and the views.
 */
public final class Motion {
    /** No overshoot. The default. */
    public static final float DAMPING_CRITICAL = 1f;
    /** A slight settle, for movement that was pushed. */
    public static final float DAMPING_SPRINGY = 0.85f;

    /** Move and reposition. */
    public static final float RESPONSE_MOVE = 0.40f;
    /** Panels sliding in. */
    public static final float RESPONSE_PANEL = 0.32f;
    /** Focus settling on a card. */
    public static final float RESPONSE_FOCUS = 0.30f;
    /** A control yielding under OK. Faster than focus: a press has to answer now. */
    public static final float RESPONSE_PRESS = 0.16f;

    public static final int SCALE = 0;
    public static final int TRANSLATION_X = 1;
    public static final int TRANSLATION_Y = 2;
    public static final int ALPHA = 3;
    public static final int ROTATION_X = 4;
    public static final int ROTATION_Y = 5;

    private static final int KINDS = 6;

    /**
     * A float that a spring can drive: a view property, or something the view
     * draws itself, like the sheen on a card.
     */
    public interface FloatProperty {
        float get();

        void set(float value);
    }

    private static final class Track {
        final FloatProperty property;
        final Spring spring;
        Runnable onSettled;

        Track(FloatProperty property, float start) {
            this.property = property;
            this.spring = new Spring(start);
        }
    }

    private static final class Runner {
        final Track[] tracks = new Track[KINDS + 8];
        final Choreographer.FrameCallback frame = this::onFrame;
        long lastNanos;
        boolean running;

        void onFrame(long frameTimeNanos) {
            if (lastNanos == 0L) {
                lastNanos = frameTimeNanos;
            }
            float dt = (frameTimeNanos - lastNanos) / 1_000_000_000f;
            lastNanos = frameTimeNanos;
            if (dt <= 0f) {
                dt = 0.016f;
            } else if (dt > 0.064f) {
                dt = 0.064f;
            }
            boolean busy = false;
            for (int i = 0; i < tracks.length; i++) {
                Track track = tracks[i];
                if (track == null) {
                    continue;
                }
                track.spring.step(dt);
                track.property.set(track.spring.value());
                if (track.spring.atRest()) {
                    track.property.set(track.spring.target());
                    tracks[i] = null;
                    Runnable callback = track.onSettled;
                    track.onSettled = null;
                    if (callback != null) {
                        callback.run();
                    }
                } else {
                    busy = true;
                }
            }
            if (busy) {
                Choreographer.getInstance().postFrameCallback(frame);
            } else {
                running = false;
                lastNanos = 0L;
            }
        }
    }

    private static final Map<View, Runner> runners = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Boolean reduced;

    private Motion() {
    }

    /**
     * True when the system or the user has asked for less motion. The system
     * animation scale is the honest signal: it is what the accessibility
     * setting actually changes.
     */
    public static boolean isReduced(Context context) {
        Boolean cached = reduced;
        if (cached != null) {
            return cached;
        }
        refresh(context);
        return reduced;
    }

    /** Re-reads the reduced-motion setting, e.g. after the user changes it. */
    public static void refresh(Context context) {
        reduced = systemScaleIsZero(context) || new Prefs(context).reduceMotion();
    }

    private static boolean systemScaleIsZero(Context context) {
        try {
            return Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Animates a property towards a target with a spring.
     *
     * @param dampingRatio 1.0 for no overshoot, below 1.0 to let it settle
     * @param response     how fast it gets there, in seconds; not a duration
     */
    public static void spring(View view, int kind, float target,
                              float dampingRatio, float response) {
        spring(view, kind, target, dampingRatio, response, null);
    }

    /** As above, and runs a callback once the spring comes to rest. */
    public static void spring(View view, int kind, float target,
                              float dampingRatio, float response, Runnable onSettled) {
        if (isReduced(view.getContext())) {
            // Reduced motion is a gentler equivalent, not no feedback: the
            // value still changes, it just does not travel.
            set(view, kind, target);
            if (onSettled != null) {
                onSettled.run();
            }
            return;
        }
        Runner runner = runner(view);
        Track track = runner.tracks[kind];
        if (track == null) {
            // Start from the value that is on screen right now, never from
            // zero: an interrupted animation has to carry on from where it is.
            track = new Track(viewProperty(view, kind), read(view, kind));
            runner.tracks[kind] = track;
        }
        // A re-target must not throw away a callback that is already
        // waiting: those callbacks take the view out of its parent, and a
        // lost one leaves the view on the screen for good.
        final Runnable previous = track.onSettled;
        if (previous == null) {
            track.onSettled = onSettled;
        } else if (onSettled != null && onSettled != previous) {
            track.onSettled = () -> {
                previous.run();
                onSettled.run();
            };
        }
        track.spring.setTarget(target, dampingRatio, response);
        start(runner);
    }

    /**
     * Drives a float the view draws itself, such as a sheen.
     *
     * @param slot a small number that tells two floats on the same view apart
     */
    public static void springCustom(View owner, int slot, float target,
                                     float dampingRatio, float response,
                                     FloatProperty property) {
        if (isReduced(owner.getContext())) {
            property.set(target);
            return;
        }
        Runner runner = runner(owner);
        int index = KINDS + slot;
        Track track = runner.tracks[index];
        if (track == null) {
            track = new Track(property, property.get());
            runner.tracks[index] = track;
        }
        track.onSettled = null;
        track.spring.setTarget(target, dampingRatio, response);
        start(runner);
    }

    /** Scales a view on both axes. */
    public static void scale(View view, float target, float dampingRatio, float response) {
        spring(view, SCALE, target, dampingRatio, response);
    }

    /** Slides a view along X. */
    public static void translateX(View view, float target, float dampingRatio, float response) {
        spring(view, TRANSLATION_X, target, dampingRatio, response);
    }

    /** Sets a property without animating it and stops any spring on it. */
    public static void set(View view, int kind, float value) {
        Runner runner = runners.get(view);
        if (runner != null) {
            runner.tracks[kind] = null;
        }
        write(view, kind, value);
    }

    /** Stops every animation on a view, for a view that is going away. */
    public static void cancel(View view) {
        Runner runner = runners.remove(view);
        if (runner != null) {
            runner.running = false;
            Choreographer.getInstance().removeFrameCallback(runner.frame);
        }
    }

    private static Runner runner(View view) {
        Runner runner = runners.get(view);
        if (runner == null) {
            runner = new Runner();
            runners.put(view, runner);
        }
        return runner;
    }

    private static void start(Runner runner) {
        if (runner.running) {
            return;
        }
        runner.running = true;
        runner.lastNanos = 0L;
        Choreographer.getInstance().postFrameCallback(runner.frame);
    }

    private static FloatProperty viewProperty(View view, final int kind) {
        // Weak, because the runner is the value of a WeakHashMap keyed by the
        // view. A strong reference here would pin the key and the entry would
        // never be collected.
        final WeakReference<View> ref = new WeakReference<>(view);
        return new FloatProperty() {
            @Override
            public float get() {
                View target = ref.get();
                return target == null ? 0f : read(target, kind);
            }

            @Override
            public void set(float value) {
                View target = ref.get();
                if (target != null) {
                    write(target, kind, value);
                }
            }
        };
    }

    private static float read(View view, int kind) {
        switch (kind) {
            case SCALE:
                return view.getScaleX();
            case TRANSLATION_X:
                return view.getTranslationX();
            case TRANSLATION_Y:
                return view.getTranslationY();
            case ALPHA:
                return view.getAlpha();
            case ROTATION_X:
                return view.getRotationX();
            case ROTATION_Y:
                return view.getRotationY();
            default:
                return 0f;
        }
    }

    private static void write(View view, int kind, float value) {
        switch (kind) {
            case SCALE:
                view.setScaleX(value);
                view.setScaleY(value);
                break;
            case TRANSLATION_X:
                view.setTranslationX(value);
                break;
            case TRANSLATION_Y:
                view.setTranslationY(value);
                break;
            case ALPHA:
                view.setAlpha(value);
                break;
            case ROTATION_X:
                view.setRotationX(value);
                break;
            case ROTATION_Y:
                view.setRotationY(value);
                break;
            default:
                break;
        }
    }
}
