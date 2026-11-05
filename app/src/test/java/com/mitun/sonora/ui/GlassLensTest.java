package com.mitun.sonora.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlassLensTest {
    @Test
    public void theRimPushesHardestAndTheMiddleNotAtAll() {
        assertEquals(10f, GlassLens.rimOffset(0f, 40f, 10f), 0.001f);
        assertEquals(0f, GlassLens.rimOffset(40f, 40f, 10f), 0.001f);
        assertEquals(0f, GlassLens.rimOffset(90f, 40f, 10f), 0.001f);
    }

    @Test
    public void theMeshNeverFoldsOverItself() {
        // A point further from the rim must stay further from the rim after
        // the push, or the picture would show through inside out.
        float band = 40f;
        float push = band * 0.42f;
        float previous = Float.NEGATIVE_INFINITY;
        for (float d = 0f; d <= band; d += 0.5f) {
            float placed = d - GlassLens.rimOffset(d, band, push);
            assertTrue("folded at " + d, placed > previous);
            previous = placed;
        }
    }

    @Test
    public void aDegenerateBandDoesNothing() {
        assertEquals(0f, GlassLens.rimOffset(0f, 0f, 10f), 0.001f);
    }
}
