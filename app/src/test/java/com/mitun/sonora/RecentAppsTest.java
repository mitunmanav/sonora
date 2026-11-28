package com.mitun.sonora;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RecentAppsTest {
    @Test
    public void anAppTakenOffComesBackOnlyWhenUsedAgain() {
        assertTrue(RecentApps.shown(100L, null));
        assertFalse(RecentApps.shown(100L, 200L));
        assertFalse(RecentApps.shown(200L, 200L));
        assertTrue(RecentApps.shown(300L, 200L));
    }

    @Test
    public void openingFromSonoraBringsItBack() {
        RecentApps.dismiss("com.test.back");
        RecentApps.noteLaunched("com.test.back");
        // noteLaunched forgets the dismissal; nothing is left to hide it.
        assertTrue(RecentApps.shown(0L, null));
    }
}
