package com.mitun.sonora;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The BACK rule on the home screen is a hard platform constraint. */
public class BackPolicyTest {

    @Test
    public void scrollsToTopWhenTheGridIsScrolled() {
        assertEquals(BackPolicy.SCROLL_TO_TOP,
                BackPolicy.atTopLevel(120, 0, false));
    }

    @Test
    public void scrollsToTopWhenTheFocusIsBelowTheFirstRow() {
        assertEquals(BackPolicy.SCROLL_TO_TOP,
                BackPolicy.atTopLevel(0, 7, false));
    }

    @Test
    public void leavesWhenThereIsNothingToGoBackTo() {
        assertEquals(BackPolicy.LEAVE,
                BackPolicy.atTopLevel(0, 0, false));
    }

    @Test
    public void staysWhenSonoraIsTheHomeScreen() {
        // Sonora is the TV home screen here, so there is nowhere to leave to.
        assertEquals(BackPolicy.SCROLL_TO_TOP,
                BackPolicy.atTopLevel(0, 0, true));
    }
}
