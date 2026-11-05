package com.mitun.sonora;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeviceTierTest {
    private static final long MIB = 1024L * 1024L;

    private static int auto(long mib) {
        return DeviceTier.resolve(mib * MIB, false, DeviceTier.AUTO);
    }

    @Test
    public void aOneGigabyteTvIsLowWhateverItReports() {
        assertEquals(DeviceTier.LOW, auto(780));
        assertEquals(DeviceTier.LOW, auto(1024));
    }

    @Test
    public void aTwoGigabyteTvIsMid() {
        // The TV this was built on reports 1990 MiB.
        assertEquals(DeviceTier.MID, auto(1990));
    }

    @Test
    public void threeGigabytesAndUpIsHigh() {
        assertEquals(DeviceTier.HIGH, auto(2860));
        assertEquals(DeviceTier.HIGH, auto(7800));
    }

    @Test
    public void theLowRamFlagWinsOverTheSize() {
        assertEquals(DeviceTier.LOW, DeviceTier.resolve(4000 * MIB, true, DeviceTier.AUTO));
    }

    @Test
    public void aChoiceInSettingsWinsOverEverything() {
        assertEquals(DeviceTier.HIGH, DeviceTier.resolve(700 * MIB, true, DeviceTier.HIGH));
        assertEquals(DeviceTier.LOW, DeviceTier.resolve(8000 * MIB, false, DeviceTier.LOW));
    }

    @Test
    public void anUnknownSizeIsNotTreatedAsTheBest() {
        assertEquals(DeviceTier.MID, DeviceTier.resolve(0, false, DeviceTier.AUTO));
    }

    @Test
    public void onlyTheLowTierHalvesTheWallpaper() {
        assertEquals(2, DeviceTier.wallpaperDivisor(DeviceTier.LOW));
        assertEquals(1, DeviceTier.wallpaperDivisor(DeviceTier.MID));
        assertEquals(1, DeviceTier.wallpaperDivisor(DeviceTier.HIGH));
    }

    @Test
    public void costlyEffectsAreOffOnlyOnLow() {
        assertFalse(DeviceTier.bentGlass(DeviceTier.LOW));
        assertTrue(DeviceTier.bentGlass(DeviceTier.MID));
        assertFalse(DeviceTier.cardShadow(DeviceTier.LOW));
        assertTrue(DeviceTier.cardShadow(DeviceTier.HIGH));
    }

    @Test
    public void lowStoresArtworkAtTheCardSize() {
        assertEquals(250, DeviceTier.artworkWidth(DeviceTier.LOW, 250, 1.22f));
        assertEquals(305, DeviceTier.artworkWidth(DeviceTier.MID, 250, 1.22f));
    }
}
