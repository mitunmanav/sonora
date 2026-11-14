package com.mitun.sonora.ui;

import android.view.Choreographer;
import android.view.View;

/**
 * A spring that drives one property of a view.
 *
 * Android has no platform spring animation - {@code SpringAnimation} is
 * AndroidX and this app carries no dependencies - so the spring is solved
 * here: a damped harmonic oscillator integrated every frame.
 *
 * Solving it rather than drawing a curve is the whole point. The spring can
 * be re-targeted at any moment and keeps the value and the speed it already
 * has, so an animation the user interrupts carries on towards the new target
 * instead of stopping dead and starting again.
 */
public final class Spring {
    /** Fixed integration step, so a slow frame cannot make the spring explode. */
    private static final float STEP = 1f / 240f;

    private final View view;
    private final int kind;
    private final Choreographer.FrameCallback frame;

    private float value;
    private float velocity;
    private float target;
    private float stiffness = 100f;
    private float damping = 20f;
    private float travel;
    private float accumulator;
    private long lastNanos;
    private boolean running;
    private Runnable onSettled;

    Spring(View view, int kind) {
        this.view = view;
        this.kind = kind;
        this.frame = this::onFrame;
    }

    /**
     * Aims at a new target, keeping the value and the speed it has now.
     *
     * @param dampingRatio 1.0 settles without overshoot, below 1.0 lets it
     * @param response     how fast it gets there, in seconds; not a duration
     */
    void setTarget(float target, float dampingRatio, float response) {
        float omega = (float) (2 * Math.PI / Math.max(0.05f, response));
        stiffness = omega * omega;
        damping = 2f * dampingRatio * omega;
        travel = Math.abs(target - value);
        this.target = target;
    }

    float value() {
        return value;
    }

    /** Called once, when the spring comes to rest. */
    void setOnSettled(Runnable runnable) {
        onSettled = runnable;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        accumulator = 0f;
        lastNanos = 0L;
        Choreographer.getInstance().postFrameCallback(frame);
    }

    void cancel() {
        if (!running) {
            return;
        }
        running = false;
        Choreographer.getInstance().removeFrameCallback(frame);
    }

    private void onFrame(long frameTimeNanos) {
        if (!running) {
            return;
        }
        if (lastNanos == 0L) {
            lastNanos = frameTimeNanos;
        }
        float dt = (frameTimeNanos - lastNanos) / 1_000_000_000f;
        lastNanos = frameTimeNanos;
        if (dt > 0.1f) {
            // A long stall must not turn into a huge step.
            dt = 0.1f;
        }
        accumulator += dt;

        float velocityTolerance = Math.max(0.02f, travel * 0.02f);
        boolean settled = true;
        while (accumulator >= STEP) {
            accumulator -= STEP;
            float acceleration = -stiffness * (value - target) - damping * velocity;
            velocity += acceleration * STEP;
            value += velocity * STEP;
            if (Math.abs(value - target) > 0.0015f || Math.abs(velocity) > velocityTolerance) {
                settled = false;
            }
        }
        apply(value);
        if (settled) {
            apply(target);
            running = false;
            if (onSettled != null) {
                Runnable callback = onSettled;
                onSettled = null;
                callback.run();
            }
            return;
        }
        Choreographer.getInstance().postFrameCallback(frame);
    }

    private void apply(float v) {
        switch (kind) {
            case Motion.SCALE:
                view.setScaleX(v);
                view.setScaleY(v);
                break;
            case Motion.TRANSLATION_X:
                view.setTranslationX(v);
                break;
            case Motion.TRANSLATION_Y:
                view.setTranslationY(v);
                break;
            case Motion.ALPHA:
                view.setAlpha(v);
                break;
            default:
                break;
        }
    }
}
