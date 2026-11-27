package com.mitun.sonora;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackPressTest {
    @Test
    public void aPressActsWhenLetGo() {
        BackPress back = new BackPress();
        assertFalse(back.released(false, 0, false));
        assertTrue(back.released(true, 0, false));
    }

    @Test
    public void anUpWithoutItsDownIsIgnored() {
        BackPress back = new BackPress();
        assertFalse(back.released(true, 0, false));
    }

    @Test
    public void aCancelledUpIsIgnored() {
        BackPress back = new BackPress();
        back.released(false, 0, false);
        assertFalse(back.released(true, 0, true));
        // And it does not count for the next up either.
        assertFalse(back.released(true, 0, false));
    }

    @Test
    public void aHeldKeyIsOnePress() {
        BackPress back = new BackPress();
        back.released(false, 0, false);
        back.released(false, 1, false);
        back.released(false, 2, false);
        assertTrue(back.released(true, 0, false));
        assertFalse(back.released(true, 0, false));
    }
}
