package com.mitun.sonora.ui;

/**
 * A spring: a damped harmonic oscillator, solved rather than drawn.
 *
 * Android has no platform spring animation - {@code SpringAnimation} is
 * AndroidX and this app carries no dependencies - so the spring is solved
 * here: a damped harmonic oscillator integrated every frame.
 *
 * Solving it rather than drawing a curve is the whole point. The spring can
 * be re-targeted at any moment and keeps the value and the speed it already
 * has, so an animation the user interrupts carries on towards the new target
 * instead of stopping dead and starting again.
 *
 * This class is pure maths - no Android types - so the physics can be
 * unit-tested. {@link Motion} owns the frame clock and the views.
 */
public final class Spring {
    /** Fixed integration step, so a slow frame cannot make the spring explode. */
    private static final float STEP = 1f / 240f;

    private float value;
    private float velocity;
    private float target;
    private float stiffness = 100f;
    private float damping = 20f;
    private float travel;
    private float accumulator;

    /**
     * @param value where the spring starts, in the same units it will animate
     *              - the value that is on screen right now, never zero
     */
    public Spring(float value) {
        this.value = value;
        // A spring that has not been aimed at anything yet has arrived.
        this.target = value;
    }

    public float value() {
        return value;
    }

    public float velocity() {
        return velocity;
    }

    public float target() {
        return target;
    }

    /**
     * Aims at a new target, keeping the value and the speed it has now.
     *
     * @param dampingRatio 1.0 settles without overshoot, below 1.0 lets it
     * @param response     how fast it gets there, in seconds; not a duration
     */
    public void setTarget(float target, float dampingRatio, float response) {
        float omega = (float) (2 * Math.PI / Math.max(0.05f, response));
        stiffness = omega * omega;
        // No damping at all rings forever; a little of it still settles.
        damping = 2f * Math.max(0.05f, dampingRatio) * omega;
        travel = Math.abs(target - value);
        this.target = target;
    }

    /**
     * Jumps to a value without losing the speed the spring already has, so a
     * new gesture can take over an animation that is still moving.
     */
    public void jumpTo(float value) {
        this.value = value;
    }

    /** Advances the simulation by {@code dt} seconds. */
    public void step(float dt) {
        if (Float.isNaN(dt) || Float.isNaN(accumulator)) {
            // A bad frame time would turn the accumulator into NaN, and a
            // NaN never reaches the step threshold: the clock ticks forever.
            accumulator = 0f;
            return;
        }
        if (dt > 0.1f) {
            // A long stall must not turn into a huge step.
            dt = 0.1f;
        }
        accumulator += dt;
        while (accumulator >= STEP) {
            accumulator -= STEP;
            float acceleration = -stiffness * (value - target) - damping * velocity;
            velocity += acceleration * STEP;
            value += velocity * STEP;
        }
    }

    /**
     * True once the spring has arrived and stopped moving.
     *
     * The tolerances are relative to how far the spring was asked to travel,
     * so a slide across the screen and a scale of one percent settle at the
     * same rate.
     */
    public boolean atRest() {
        // A target that is not a number can never be reached, and a spring
        // that never rests would keep the frame clock awake for good.
        if (Float.isNaN(value) || Float.isNaN(target) || Float.isNaN(velocity)) {
            return true;
        }
        float positionTolerance = Math.max(0.0005f, travel * 0.001f);
        float velocityTolerance = Math.max(0.005f, travel * 0.02f);
        return Math.abs(value - target) <= positionTolerance
                && Math.abs(velocity) <= velocityTolerance;
    }
}
