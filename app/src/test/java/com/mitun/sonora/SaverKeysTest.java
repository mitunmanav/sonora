package com.mitun.sonora;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SaverKeysTest {
    private static final int ON_TAP = 0;
    private static final int ALWAYS = 1;
    private static final int NEVER = 2;

    @Test
    public void theArrowsChangeTheScene() {
        assertEquals(SaverKeys.PREVIOUS, SaverKeys.action(21, ON_TAP, false));
        assertEquals(SaverKeys.NEXT, SaverKeys.action(22, ALWAYS, true));
    }

    @Test
    public void okShowsTheClockOnceThenWakes() {
        assertEquals(SaverKeys.SHOW_CLOCK, SaverKeys.action(23, ON_TAP, false));
        assertEquals(SaverKeys.WAKE, SaverKeys.action(23, ON_TAP, true));
        assertEquals(SaverKeys.WAKE, SaverKeys.action(66, ALWAYS, true));
        assertEquals(SaverKeys.WAKE, SaverKeys.action(23, NEVER, false));
    }

    @Test
    public void anyOtherKeyWakes() {
        // UP, DOWN, BACK, MENU, PLAY/PAUSE.
        for (int key : new int[]{19, 20, 4, 82, 85}) {
            assertEquals(SaverKeys.WAKE, SaverKeys.action(key, ON_TAP, false));
        }
    }

    @Test
    public void theVolumeIsLeftToTheTv() {
        for (int key : new int[]{24, 25, 164}) {
            assertEquals(SaverKeys.PASS, SaverKeys.action(key, ON_TAP, false));
        }
    }
}
