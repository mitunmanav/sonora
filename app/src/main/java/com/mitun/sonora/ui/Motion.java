package com.mitun.sonora.ui;

import android.content.Context;
import android.provider.Settings;
import android.view.View;

import com.mitun.sonora.Prefs;

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

    public static final int SCALE = 0;
    public static final int TRANSLATION_X = 1;
    public static final int TRANSLATION_Y = 2;
    public static final int ALPHA = 3;

    private static final int KINDS = 4;
    private static final Map<View, Spring[]> springs = new WeakHashMap<>();

    private static Boolean reduced;

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
        boolean value = systemScaleIsZero(context);
        if (!value) {
            value = new Prefs(context).reduceMotion();
        }
        reduced = value;
        return value;
    }

    /** Lets the settings override the reduced-motion decision. */
    public static void setReduced(Context context, boolean value) {
        reduced = value || systemScaleIsZero(context);
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
            Spring existing = springFor(view, kind);
            if (existing != null) {
                existing.cancel();
            }
            set(view, kind, target);
            if (onSettled != null) {
                onSettled.run();
            }
            return;
        }
        Spring spring = springFor(view, kind);
        if (spring == null) {
            spring = new Spring(view, kind);
            Spring[] byKind = springs.get(view);
            if (byKind == null) {
                byKind = new Spring[KINDS];
                springs.put(view, byKind);
            }
            byKind[kind] = spring;
        }
        spring.setOnSettled(onSettled);
        spring.setTarget(target, dampingRatio, response);
        spring.start();
    }

    /** Scales a view on both axes. */
    public static void scale(View view, float target, float dampingRatio, float response) {
        spring(view, SCALE, target, dampingRatio, response);
    }

    /** Slides a view along X. */
    public static void translateX(View view, float target, float dampingRatio, float response) {
        spring(view, TRANSLATION_X, target, dampingRatio, response);
    }

    /** Sets a float property without animating it. */
    public static void set(View view, int kind, float value) {
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
            default:
                break;
        }
    }

    private static Spring springFor(View view, int kind) {
        Spring[] byKind = springs.get(view);
        return byKind == null ? null : byKind[kind];
    }
}
