package com.mitun.sonora.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The spring physics.
 *
 * These are the properties the interface depends on: a spring that never
 * overshoots when it is not asked to, a spring that arrives, and a spring
 * that keeps its speed when it is re-aimed, which is what lets an
 * interrupted animation carry on instead of starting again.
 */
public class SpringTest {
    private static final float FRAME = 1f / 60f;

    /** Runs the spring for {@code seconds} of simulated time. */
    private static void run(Spring spring, float seconds) {
        int steps = Math.round(seconds / FRAME);
        for (int i = 0; i < steps; i++) {
            spring.step(FRAME);
        }
    }

    @Test
    public void aCriticalSpringArrives() {
        Spring spring = new Spring(0f);
        spring.setTarget(100f, 1f, 0.4f);
        run(spring, 2f);
        assertTrue("never arrived: " + spring.value(), spring.atRest());
        assertEquals(100f, spring.value(), 0.1f);
    }

    @Test
    public void aCriticalSpringNeverOvershoots() {
        Spring spring = new Spring(0f);
        spring.setTarget(100f, 1f, 0.4f);
        float highest = spring.value();
        for (int i = 0; i < 240; i++) {
            spring.step(FRAME);
            highest = Math.max(highest, spring.value());
        }
        assertTrue("overshot to " + highest, highest <= 100f + 0.001f);
    }

    @Test
    public void aSpringySpringDoesOvershoot() {
        // Below critical damping the spring is meant to carry past the target
        // and settle back, so the damping ratio has to actually matter.
        Spring spring = new Spring(0f);
        spring.setTarget(100f, 0.5f, 0.4f);
        float highest = 0f;
        for (int i = 0; i < 240; i++) {
            spring.step(FRAME);
            highest = Math.max(highest, spring.value());
        }
        assertTrue("never overshot: " + highest, highest > 100f);
    }

    @Test
    public void reAimingKeepsTheSpeed() {
        Spring spring = new Spring(0f);
        spring.setTarget(100f, 1f, 0.4f);
        run(spring, 0.1f);
        float value = spring.value();
        float speed = spring.velocity();
        assertTrue("the spring was not moving", speed > 0f);

        spring.setTarget(-100f, 1f, 0.4f);
        assertEquals("the value jumped on re-aim", value, spring.value(), 0.0001f);
        assertEquals("the speed was lost on re-aim", speed, spring.velocity(), 0.0001f);
    }

    @Test
    public void reAimingStillArrives() {
        Spring spring = new Spring(0f);
        spring.setTarget(100f, 1f, 0.4f);
        run(spring, 0.1f);
        spring.setTarget(-100f, 1f, 0.4f);
        run(spring, 3f);
        assertTrue("never arrived: " + spring.value(), spring.atRest());
        assertEquals(-100f, spring.value(), 0.1f);
    }

    @Test
    public void jumpingKeepsTheSpeed() {
        Spring spring = new Spring(0f);
        spring.setTarget(100f, 1f, 0.4f);
        run(spring, 0.1f);
        float speed = spring.velocity();
        spring.jumpTo(42f);
        assertEquals(42f, spring.value(), 0.0001f);
        assertEquals("the speed was lost on the jump", speed, spring.velocity(), 0.0001f);
    }

    @Test
    public void aLongStallDoesNotExplode() {
        Spring spring = new Spring(0f);
        spring.setTarget(100f, 1f, 0.4f);
        spring.step(10f);
        assertFalse("the spring flew off", Float.isInfinite(spring.value())
                || Float.isNaN(spring.value()));
        assertTrue("the spring overshot", spring.value() < 100f + 1f);
    }

    @Test
    public void aSpringThatHasNotMovedIsAtRest() {
        Spring spring = new Spring(5f);
        assertTrue(spring.atRest());
    }

    @Test
    public void aSpringWithNoNumberInItIsAtRest() {
        // A target that is not a number can never be reached, and a spring
        // that never rests would keep the frame clock awake for good.
        Spring spring = new Spring(0f);
        spring.setTarget(Float.NaN, 1f, 0.4f);
        assertTrue(spring.atRest());
    }

    @Test
    public void noDampingStillSettles() {
        // A damping ratio of nothing would ring for good, so it is clamped.
        Spring spring = new Spring(0f);
        spring.setTarget(100f, 0f, 0.4f);
        run(spring, 12f);
        assertFalse("the spring flew off", Float.isInfinite(spring.value())
                || Float.isNaN(spring.value()));
        assertTrue("never arrived: " + spring.value(), spring.atRest());
    }

    @Test
    public void negativeDampingDoesNotExplode() {
        // Negative damping feeds energy in; the clamp turns it into the
        // smallest useful damping instead.
        Spring spring = new Spring(0f);
        spring.setTarget(100f, -1f, 0.4f);
        run(spring, 12f);
        assertFalse("the spring flew off", Float.isInfinite(spring.value())
                || Float.isNaN(spring.value()));
        assertTrue("never arrived: " + spring.value(), spring.atRest());
    }
}
